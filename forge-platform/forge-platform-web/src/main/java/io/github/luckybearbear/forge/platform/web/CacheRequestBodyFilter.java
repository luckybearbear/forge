package io.github.luckybearbear.forge.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 请求体可重复读（访问日志/审计前置，spec §6.5）。排除 URL 不包装。
 *
 * <p>排除匹配为<b>精确路径</b>（{@link Set#contains}，终审 Important #2）：前缀匹配会让
 * {@code /upload} 误伤 {@code /upload2} 等同前缀路径；如需模式排除请列举完整路径。
 * 缓存上限在构造处对 {@code > Integer.MAX_VALUE} 钳制（{@link ContentCachingRequestWrapper}
 * 的 contentCacheLimit 为 int）。
 */
public class CacheRequestBodyFilter extends OncePerRequestFilter {

    private final int maxBodySize;
    private final Set<String> excludeUrls;

    public CacheRequestBodyFilter(long maxBodySize, Set<String> excludeUrls) {
        this.maxBodySize = (int) Math.min(maxBodySize, Integer.MAX_VALUE);
        this.excludeUrls = excludeUrls == null ? Set.of() : Set.copyOf(excludeUrls);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return excludeUrls.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, maxBodySize);
        filterChain.doFilter(wrapper, response);
    }
}
