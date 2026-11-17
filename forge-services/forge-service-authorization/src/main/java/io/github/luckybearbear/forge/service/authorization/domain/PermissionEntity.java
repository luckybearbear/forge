package io.github.luckybearbear.forge.service.authorization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 权限点实体——<b>全局表</b>（extends AbstractAuditableEntity，无 @TenantId）：权限字典是平台级
 * 领域语言（{@code user:manage}/{@code mold:read}...），所有租户共享同一字典，租户侧只经
 * auth_role_permission 做"角色-权限点"引用。code 全局唯一（partial unique index WHERE deleted = FALSE）。
 */
@Entity
@Table(name = "auth_permission")
public class PermissionEntity extends AbstractAuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 权限点类型：MENU（菜单可见性）/ API（接口调用）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PermissionType type;

    protected PermissionEntity() {} // JPA

    public PermissionEntity(String code, String name, PermissionType type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PermissionType getType() { return type; }
    public void setType(PermissionType type) { this.type = type; }
}
