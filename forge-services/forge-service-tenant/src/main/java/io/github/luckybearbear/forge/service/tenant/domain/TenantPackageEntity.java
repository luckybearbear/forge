package io.github.luckybearbear.forge.service.tenant.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 租户套餐实体——<b>全局表</b>（同 {@link TenantEntity}：无 @TenantId，判别式/写守护不适用）。
 * code 唯一性同样由 partial unique index（{@code WHERE deleted = FALSE}）承载。
 */
@Entity
@Table(name = "tenant_package")
public class TenantPackageEntity extends AbstractAuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(length = 256)
    private String remark;

    protected TenantPackageEntity() {} // JPA

    public TenantPackageEntity(String code, String name, Integer maxUsers, String remark) {
        this.code = code;
        this.name = name;
        this.maxUsers = maxUsers;
        this.remark = remark;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
