package io.github.luckybearbear.forge.platform.security.token;

import java.time.Duration;
import java.util.Optional;

/**
 * Token 存储 SPI：Redis 实现（2a starter 装配）/内存实现（测试与开发降级）。
 *
 * <p>{@link #revokeByUserId(String)}（Task 4 安全输入 2）：按用户吊销其全部 token——改密等
 * 会话失效事件的承接点（forge-security-starter 的 UserPasswordChanged 监听器调用）。实现须维护
 * userId → token 索引（Redis：SADD {@code auth:user-tokens:{userId}} + 键级 TTL；内存：反向 Map）。
 *
 * <p>{@link #revokeByTenantId(String)}（Phase 3 前置 Task 1）：按租户吊销其全部存量 token——
 * 租户停用即时全下线的承接点（forge-security-starter 的 TenantDisabled 监听器调用）。实现须维护
 * tenantId → token 索引（Redis：SADD {@code auth:tenant-tokens:{tenantId}} + 键级 TTL；内存：
 * 反向 Map）；tenantId 为 null 的 admin token 不入租户索引，不受租户吊销影响。
 */
public interface TokenStore {

    StoredToken create(TokenGrant grant, Duration ttl);

    Optional<StoredToken> resolve(String token);

    void revoke(String token);

    void refresh(String token, Duration ttl);

    /** 吊销该用户名下全部 token（幂等；未知用户/无 token 为 no-op）。无租户语义——按 userId 全域。 */
    void revokeByUserId(String userId);

    /**
     * 吊销该租户名下全部 token（幂等；未知租户/无 token 为 no-op）。无租户语义——按 tenantId
     * 全域吊销（不依赖/不设置租户上下文）；tenantId 为 null 的 admin token 不入租户索引、不受影响。
     */
    void revokeByTenantId(String tenantId);
}
