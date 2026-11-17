package io.github.luckybearbear.forge.platform.audit.listener;

import io.github.luckybearbear.forge.platform.audit.ErrorLogRecorded;
import io.github.luckybearbear.forge.platform.audit.domain.ApiErrorLogEntity;
import io.github.luckybearbear.forge.platform.audit.domain.ApiErrorLogRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 错误日志事件监听（spec §6.6）：{@code ErrorLogRecorded} → {@code api_error_log} 行。
 * 事务/租户语义与 {@link AccessLogListener} 同构：发布方（GlobalExceptionHandler 兜底分支，
 * 无自然事务）以 TransactionTemplate 包裹 publishEvent 满足 AFTER_COMMIT 投递；全局表写
 * 不恢复租户上下文（payload 显式携带）。
 */
@Component
public class ErrorLogListener {

    private final ApiErrorLogRepository repository;

    public ErrorLogListener(ApiErrorLogRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(ErrorLogRecorded event) {
        repository.save(new ApiErrorLogEntity(event.tenantId(), event.userId(), event.exception(),
                event.message(), event.stackDigest(), event.occurredAt()));
    }
}
