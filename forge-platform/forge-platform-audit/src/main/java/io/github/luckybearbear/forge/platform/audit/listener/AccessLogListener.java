package io.github.luckybearbear.forge.platform.audit.listener;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import io.github.luckybearbear.forge.platform.audit.domain.ApiAccessLogEntity;
import io.github.luckybearbear.forge.platform.audit.domain.ApiAccessLogRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 访问日志事件监听（spec §6.6）：{@code AccessLogRecorded} → {@code api_access_log} 行。
 *
 * <p><b>事务语义</b>：@ApplicationModuleListener 为 AFTER_COMMIT 投递（无事务发布直接丢弃，
 * service-authorization 集成测试实证）——发布点（ApiAccessLogFilter 的 finally 段）无自然事务，
 * 由发布方以 TransactionTemplate 包裹 publishEvent（登记随该事务原子提交，提交后异步投递 +
 * event_publication 先登记后完成标记）。落库事务由注解自带的 REQUIRES_NEW 承载。
 *
 * <p><b>不恢复租户上下文</b>：api_access_log 为全局表（tenant_id 普通冗余列，无 @TenantId
 * 判别式），写路径不经租户解析/过滤；租户归属以 payload 字段落列（异步线程不继承 TTL，
 * spec §6.1 的"payload 显式携带"分支）。平台视角写守护仅作用于 AbstractTenantEntity，
 * 本写不受影响。
 */
@Component
public class AccessLogListener {

    private final ApiAccessLogRepository repository;

    public AccessLogListener(ApiAccessLogRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(AccessLogRecorded event) {
        repository.save(new ApiAccessLogEntity(event.tenantId(), event.userId(), event.method(),
                event.path(), event.resultCode(), event.durationMs(), event.bodyDigest(),
                event.occurredAt()));
    }
}
