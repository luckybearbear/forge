package io.github.luckybearbear.forge.platform.audit.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * API 访问日志实体——<b>全局表</b>（spec §6.6/附录 A）：不继承 AbstractTenantEntity、
 * tenant_id/user_id 为普通冗余列（非 {@code @TenantId} 判别式）——审计行跨租户检索/留存，
 * 不参与租户过滤；租户归属由事件 payload 显式携带。平台视角写守护不适用（非租户实体）。
 *
 * <p>append-only 流水：无唯一约束（软删行无码可复用语义，partial unique 不适用）。
 */
@Entity
@Table(name = "api_access_log", indexes = {
        @Index(name = "idx_api_access_log_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_api_access_log_path_created", columnList = "path, created_at")
})
public class ApiAccessLogEntity extends AbstractAuditableEntity {

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, length = 8)
    private String method;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(name = "result_code", nullable = false)
    private int resultCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    /** 请求体摘要（缓存体截断 UTF-8 串；无缓存 wrapper 为 null，未读体为空串）——TEXT 列。 */
    @Column(name = "body_digest", columnDefinition = "text")
    private String bodyDigest;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ApiAccessLogEntity() {} // JPA

    public ApiAccessLogEntity(String tenantId, String userId, String method, String path,
            int resultCode, long durationMs, String bodyDigest, Instant occurredAt) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.method = method;
        this.path = path;
        this.resultCode = resultCode;
        this.durationMs = durationMs;
        this.bodyDigest = bodyDigest;
        this.occurredAt = occurredAt;
    }

    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public int getResultCode() { return resultCode; }
    public long getDurationMs() { return durationMs; }
    public String getBodyDigest() { return bodyDigest; }
    public Instant getOccurredAt() { return occurredAt; }
}
