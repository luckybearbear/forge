package io.github.luckybearbear.forge.service.authorization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 角色-权限点关联——<b>租户表</b>：授权行为本身归属租户（判别式隔离）。roleId/permissionId 为
 * 逻辑引用（无物理 FK：角色软删/权限点字典清理侧重绑，README 约定）。(tenant_id, role_id,
 * permission_id) partial unique——同一角色对同一权限点至多一条有效授权。
 */
@Entity
@Table(name = "auth_role_permission", indexes = @Index(name = "idx_auth_role_permission_role",
        columnList = "role_id"))
public class RolePermissionEntity extends AbstractTenantEntity {

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    @Column(name = "permission_id", nullable = false, length = 36)
    private String permissionId;

    protected RolePermissionEntity() {} // JPA

    public RolePermissionEntity(String roleId, String permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public String getRoleId() { return roleId; }
    public String getPermissionId() { return permissionId; }
}
