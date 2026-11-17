package io.github.luckybearbear.forge.service.tenant.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 租户实体——<b>全局表</b>（spec §6.1）：无 @TenantId（继承 {@link AbstractAuditableEntity} 而非
 * AbstractTenantEntity），Hibernate 判别式多租户与平台视角写守护均不适用；任意租户视角共享同一行集。
 *
 * <p>code 唯一性由 partial unique index（{@code WHERE deleted = FALSE}）承载：软删行不占码，
 * 编码可复用；软删除/审计/乐观锁/UUIDv7 主键由基类机制（Hibernate 7 类级 @SoftDelete、
 * JPA auditing、@Version、@PrePersist）承载。
 */
@Entity
@Table(name = "tenant")
public class TenantEntity extends AbstractAuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 逻辑引用 tenant_package.id；无物理 FK（套餐可先行软删，租户侧重绑，README 约定）。 */
    @Column(name = "package_id", length = 36)
    private String packageId;

    @Enumerated(EnumType.STRING) // 默认 ORDINAL 会在枚举重排时静默错位，明确按名存储
    @Column(nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "expired_at")
    private Instant expiredAt;

    protected TenantEntity() {} // JPA

    public TenantEntity(String code, String name, String packageId, TenantStatus status, Instant expiredAt) {
        this.code = code;
        this.name = name;
        this.packageId = packageId;
        this.status = status;
        this.expiredAt = expiredAt;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }
    public Instant getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Instant expiredAt) { this.expiredAt = expiredAt; }
}
