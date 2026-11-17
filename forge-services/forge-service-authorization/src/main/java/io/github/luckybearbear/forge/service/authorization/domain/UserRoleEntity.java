package io.github.luckybearbear.forge.service.authorization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 用户-角色关联——<b>租户表</b>：userId 逻辑引用 identity 用户（无物理 FK，跨域不耦合）。
 * (tenant_id, user_id, role_id) partial unique——同一用户对同一角色至多一条有效分配。
 */
@Entity
@Table(name = "auth_user_role", indexes = {
        @Index(name = "idx_auth_user_role_user", columnList = "user_id"),
        @Index(name = "idx_auth_user_role_role", columnList = "role_id")})
public class UserRoleEntity extends AbstractTenantEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    protected UserRoleEntity() {} // JPA

    public UserRoleEntity(String userId, String roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public String getUserId() { return userId; }
    public String getRoleId() { return roleId; }
}
