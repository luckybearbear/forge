package io.github.luckybearbear.forge.service.identity.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 平台管理员实体——<b>全局表</b>（spec §6.1）：继承 {@link AbstractAuditableEntity} 而非
 * AbstractTenantEntity——无 @TenantId，判别式过滤与平台视角写守护均不适用；admin 登录查询
 * 在无租户上下文下直查（全局表查询不随判别式收敛）。
 *
 * <p>种子 admin/admin123 由 Flyway 迁移写入（固定 bcrypt hash，README 记初始口令与首登改密提示）。
 * username 唯一性由 partial unique index（{@code WHERE deleted = FALSE}）承载。
 */
@Entity
@Table(name = "platform_admin")
public class PlatformAdminEntity extends AbstractAuditableEntity {

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    protected PlatformAdminEntity() {} // JPA

    public PlatformAdminEntity(String username, String passwordHash, UserStatus status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
