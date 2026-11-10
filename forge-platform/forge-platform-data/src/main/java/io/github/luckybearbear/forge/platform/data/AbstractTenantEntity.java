package io.github.luckybearbear.forge.platform.data;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/** 租户实体基线：@TenantId 由 Hibernate 自动过滤/注入（spec §6.1 入口 2）。 */
@MappedSuperclass
public abstract class AbstractTenantEntity extends AbstractAuditableEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
