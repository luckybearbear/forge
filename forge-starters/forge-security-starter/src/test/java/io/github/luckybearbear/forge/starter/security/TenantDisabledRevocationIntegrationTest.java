package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.contract.tenant.TenantDisabled;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户停用会话失效链路（Phase 3 前置 Task 1，真实 redis:8-alpine 容器）：
 * {@code revokeByTenantId}（tenant 索引遍历吊销 + 索引清理，只影响目标租户）与
 * {@code TenantDisabled} 监听器（事件 → 吊销该租户全部存量 token，即时全下线）。
 *
 * <p>隔离语义重点：邻居租户 token 不受影响；tenantId=null 的 admin token 不入租户索引、
 * 不受租户吊销影响（平台管理员跨租户）。
 *
 * <p>NONE web 环境：安全链按 SERVLET 守卫退避——本测试只涉 TokenStore/监听器，无需 HTTP。
 */
@SpringBootTest(classes = TenantDisabledRevocationIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TenantDisabledRevocationIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired TokenStore tokenStore;
    @Autowired StringRedisTemplate rawRedis;
    @Autowired ApplicationEventPublisher eventPublisher;

    private static TokenGrant grant(String userId, String tenantId) {
        return new TokenGrant(userId, tenantId, "MEMBER", Set.of());
    }

    private static TokenGrant adminGrant(String userId) {
        return new TokenGrant(userId, null, "PLATFORM", Set.of());
    }

    @Test
    void revokeByTenantIdRevokesOnlyThatTenantsTokensAndClearsIndex() {
        StoredToken aFirst = tokenStore.create(grant("u-ta1", "tenant-a"), Duration.ofHours(8));
        StoredToken aSecond = tokenStore.create(grant("u-ta2", "tenant-a"), Duration.ofHours(8));
        StoredToken bFirst = tokenStore.create(grant("u-tb1", "tenant-b"), Duration.ofHours(8));
        StoredToken admin = tokenStore.create(adminGrant("root-a"), Duration.ofHours(8));

        // tenant 索引随 create 落盘（SADD + TTL）；admin token（tenantId=null）不入租户索引
        assertThat(rawRedis.opsForSet().members("auth:tenant-tokens:tenant-a"))
                .containsExactlyInAnyOrder(aFirst.token(), aSecond.token());
        assertThat(rawRedis.getExpire("auth:tenant-tokens:tenant-a", TimeUnit.SECONDS)).isPositive();
        assertThat(rawRedis.hasKey("auth:tenant-tokens:null")).isFalse();

        tokenStore.revokeByTenantId("tenant-a");

        assertThat(tokenStore.resolve(aFirst.token())).isEmpty();
        assertThat(tokenStore.resolve(aSecond.token())).isEmpty();
        assertThat(tokenStore.resolve(bFirst.token())).isPresent(); // 邻居租户不受影响
        assertThat(tokenStore.resolve(admin.token())).isPresent(); // admin token（无租户）不受影响
        assertThat(rawRedis.hasKey("auth:tenant-tokens:tenant-a")).isFalse(); // 索引一并清理
        assertThat(rawRedis.hasKey("auth:tenant-tokens:tenant-b")).isTrue(); // 邻居索引保留

        // 幂等：重复吊销未知租户 no-op
        tokenStore.revokeByTenantId("t-never-existed");
        assertThat(tokenStore.resolve(bFirst.token())).isPresent();
    }

    /** refresh 滑动续期同步续 tenant 索引 TTL（索引恒 ≥ 其内最长 token 生命周期，同 user 索引）。 */
    @Test
    void refreshRenewsTenantIndexTtlAlongsideToken() {
        StoredToken shortLived = tokenStore.create(grant("u-trf", "tenant-rf"), Duration.ofSeconds(3));

        tokenStore.refresh(shortLived.token(), Duration.ofHours(8));

        assertThat(rawRedis.getExpire("auth:tenant-tokens:tenant-rf", TimeUnit.SECONDS)).isGreaterThan(60);
        assertThat(tokenStore.resolve(shortLived.token())).isPresent();
    }

    /** TenantDisabled 事件 → 监听器吊销该租户全部存量 token（停用通道的会话失效承接，即时全下线）。 */
    @Test
    void tenantDisabledEventRevokesThatTenantsTokensOnly() {
        StoredToken aFirst = tokenStore.create(grant("u-ea1", "tenant-ev-a"), Duration.ofHours(8));
        StoredToken aSecond = tokenStore.create(grant("u-ea2", "tenant-ev-a"), Duration.ofHours(8));
        StoredToken bFirst = tokenStore.create(grant("u-eb1", "tenant-ev-b"), Duration.ofHours(8));
        StoredToken admin = tokenStore.create(adminGrant("root-e"), Duration.ofHours(8));

        eventPublisher.publishEvent(new TenantDisabled("tenant-ev-a", Instant.now()));

        assertThat(tokenStore.resolve(aFirst.token())).isEmpty();
        assertThat(tokenStore.resolve(aSecond.token())).isEmpty();
        assertThat(tokenStore.resolve(bFirst.token())).isPresent(); // 邻居租户不受影响
        assertThat(tokenStore.resolve(admin.token())).isPresent(); // admin token（无租户）不受影响
    }
}
