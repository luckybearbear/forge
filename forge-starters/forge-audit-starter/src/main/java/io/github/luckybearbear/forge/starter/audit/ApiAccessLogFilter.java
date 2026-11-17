package io.github.luckybearbear.forge.starter.audit;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

/**
 * API 访问日志过滤器（spec §6.6）：入口计时 → 链后（finally 段）取响应状态码/请求体摘要/
 * 租户-用户上下文快照 → 发布 {@link AccessLogRecorded}——经 Modulith 事件登记异步落
 * {@code api_access_log}（监听器在 platform-audit）。
 *
 * <p><b>执行位置</b>：{@code FilterOrder.ACCESS_LOG_FILTER=-80}，晚于 body 缓存（-150）与
 * 安全链（-100）——入口时租户/用户上下文已就绪；出口在 finally 段读取（外层
 * TokenAuthFilter 的 finally 清理尚未回退执行，上下文仍有效），链异常/正常响应统一落账。
 *
 * <p><b>发布事务</b>：filter 出口无自然事务，而 @ApplicationModuleListener 为 AFTER_COMMIT
 * 投递（无事务发布直接丢弃，service-authorization 集成测试实证）——publishEvent 以
 * TransactionTemplate 包裹（REQUIRED 空事务）：event_publication 登记随其原子提交，
 * 提交后异步投递。
 *
     * <p><b>body 摘要</b>：仅当请求链上可寻得 {@link ContentCachingRequestWrapper}（web-starter 的
     * CacheRequestBodyFilter 装配；经 {@code WebUtils.getNativeRequest} 沿 wrapper 链取——安全链的
     * FirewalledRequest 会再裹一层，instanceof 判定在 security 组装下恒 false）；被
     * {@code exclude-urls} 排除或自定义栈未包装时为 null）；
 * 缓存体本身已被 wrapper 的 contentCacheLimit 截断，此处再按 max-body-size 防御性截断，
 * 转 UTF-8 串后<b>剥离 ISO 控制字符</b>（保留 \t/\n/\r 与全部可见字符）——PG text 拒绝
 * U+0000，二进制 body（protobuf/压缩流）原样入库会使监听器 INSERT 失败、event_publication
 * 卡 incomplete；摘要是有损可读快照而非还原。GET 等未读体请求为空串。
 *
 * <p><b>审计不反噬请求</b>：发布异常仅 warn（访问日志缺失可容忍，请求语义不可损）。
 */
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);

    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate publishTransaction;
    private final long maxBodySize;

    public ApiAccessLogFilter(ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager, long maxBodySize) {
        this.eventPublisher = eventPublisher;
        this.publishTransaction = new TransactionTemplate(transactionManager);
        this.maxBodySize = maxBodySize;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Instant occurredAt = Instant.now();
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            publishSafely(request, response, occurredAt, durationMs);
        }
    }

    private void publishSafely(HttpServletRequest request, HttpServletResponse response,
            Instant occurredAt, long durationMs) {
        try {
            // ThreadLocalUserProvider 无静态读出口：无状态实例读同一 TTL 持有器（TokenAuthFilter 写入侧）
            AccessLogRecorded event = new AccessLogRecorded(
                    TenantContextHolder.getTenantId().orElse(null),
                    new ThreadLocalUserProvider().currentUser().map(CurrentUser::userId).orElse(null),
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    durationMs, bodyDigest(request), occurredAt);
            publishTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        } catch (Exception ex) {
            log.warn("access log publish skipped: {}", ex.getMessage(), ex);
        }
    }

    private String bodyDigest(HttpServletRequest request) {
        // 安全链在场时（FilterChainProxy 的 HttpFirewall）请求被再裹一层 FirewalledRequest——
        // instanceof 判定恒 false、body_digest 恒 null（demo 四服务+security+audit 全链组装实证，
        // audit-starter 自测因类路径无 security 未覆盖该形态）；经 WebUtils 沿 ServletRequestWrapper
        // 链取原生 ContentCachingRequestWrapper（无嵌套时即直接命中）。
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return null; // 无缓存 wrapper（exclude-urls 排除/自定义栈未包装）：不记体
        }
        byte[] cached = wrapper.getContentAsByteArray();
        int limit = (int) Math.min(maxBodySize, Integer.MAX_VALUE);
        byte[] truncated = cached.length > limit ? Arrays.copyOf(cached, limit) : cached;
        return stripControlChars(new String(truncated, StandardCharsets.UTF_8));
    }

    /** 剥离 ISO 控制字符（C0 0x00-0x1F 除 \t/\n/\r、DEL 0x7F、C1 0x80-0x9F）——PG text 拒绝 U+0000。 */
    private static String stripControlChars(String digest) {
        StringBuilder stripped = new StringBuilder(digest.length());
        digest.codePoints().forEach(codePoint -> {
            boolean keep = (codePoint >= 0x20 && (codePoint < 0x7F || codePoint > 0x9F))
                    || codePoint == '\t' || codePoint == '\n' || codePoint == '\r';
            if (keep) {
                stripped.appendCodePoint(codePoint);
            }
        });
        return stripped.toString();
    }
}
