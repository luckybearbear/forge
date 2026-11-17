package io.github.luckybearbear.forge.service.identity.domain;

import io.github.luckybearbear.forge.platform.data.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 租户用户实体——<b>租户表</b>（spec §6.1）：继承 {@link AbstractTenantEntity}，
 * {@code @TenantId} 判别式自动过滤（查询只读本租户行）/自动注入（写入由会话开启时的
 * 租户上下文落 tenant_id）。
 *
 * <p>登录上下文要点：登录请求无租户上下文（未认证）——{@code AuthService.appLogin} 在查询前
 * 显式 {@code TenantContextHolder.setTenantId(tenant.id())} 并 finally clear（判别式过滤在
 * 会话开启时解析，见该类 javadoc）。
 *
 * <p>(tenant_id, username) 唯一性由 partial unique index（{@code WHERE deleted = FALSE}）承载：
 * 租户内唯一、跨租户可重名、软删行不占名；软删除/审计/乐观锁/UUIDv7 主键由基类机制承载。
 */
@Entity
@Table(name = "identity_user")
public class IdentityUserEntity extends AbstractTenantEntity {

    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    /** 管理端启停（UserService.enable/disable）；DISABLED 用户登录按坏凭据拒绝（不泄露停用状态）。 */
    @Enumerated(EnumType.STRING) // 默认 ORDINAL 会在枚举重排时静默错位，明确按名存储
    @Column(nullable = false, length = 16)
    private UserStatus status;

    protected IdentityUserEntity() {} // JPA

    public IdentityUserEntity(String username, String phone, String passwordHash, UserStatus status) {
        this.username = username;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
