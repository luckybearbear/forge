package io.github.luckybearbear.forge.platform.audit.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * API 错误日志实体——<b>全局表</b>（spec §6.6/附录 A）：tenant_id/user_id 为普通冗余列
 * （非 {@code @TenantId} 判别式），语义同 {@link ApiAccessLogEntity}。仅未预期 500 兜底
 * 分支写入（发布点取舍见 {@code ErrorLogRecorded} javadoc）。append-only 流水：无唯一约束。
 */
@Entity
@Table(name = "api_error_log", indexes = {
        @Index(name = "idx_api_error_log_tenant_created", columnList = "tenant_id, created_at")
})
public class ApiErrorLogEntity extends AbstractAuditableEntity {

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, length = 512)
    private String exception;

    /** 异常消息（可空）——TEXT 列。 */
    @Column(columnDefinition = "text")
    private String message;

    /** 堆栈摘要（前 2000 字符截断）——TEXT 列。 */
    @Column(name = "stack_digest", columnDefinition = "text")
    private String stackDigest;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ApiErrorLogEntity() {} // JPA

    public ApiErrorLogEntity(String tenantId, String userId, String exception, String message,
            String stackDigest, Instant occurredAt) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.exception = exception;
        this.message = message;
        this.stackDigest = stackDigest;
        this.occurredAt = occurredAt;
    }

    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getException() { return exception; }
    public String getMessage() { return message; }
    public String getStackDigest() { return stackDigest; }
    public Instant getOccurredAt() { return occurredAt; }
}
