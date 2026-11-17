package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.contract.identity.UserPasswordChanged;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话吊销链路（Task 4 安全输入 2，真实 redis:8-alpine 容器）：
 * {@code revokeByUserId}（user 索引遍历吊销 + 索引清理，只影响目标用户）与
 * {@code UserPasswordChanged} 监听器（事件 → 吊销该用户全部 token）。
 *
 * <p>NONE web 环境：安全链按 SERVLET 守卫退避——本测试只涉 TokenStore/监听器，无需 HTTP。
 */
@SpringBootTest(classes = TokenRevocationIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TokenRevocationIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired TokenStore tokenStore;
    @Autowired StringRedisTemplate rawRedis;
    @Autowired ApplicationEventPublisher eventPublisher;

    private static TokenGrant grant(String userId, String tenantId) {
        return new TokenGrant(userId, tenantId, "MEMBER", Set.of());
    }

    @Test
    void revokeByUserIdRevokesOnlyThatUsersTokensAndClearsIndex() {
        StoredToken first = tokenStore.create(grant("u-revoke", "t-1"), Duration.ofHours(8));
        StoredToken second = tokenStore.create(grant("u-revoke", "t-1"), Duration.ofHours(8));
        StoredToken other = tokenStore.create(grant("u-keep", "t-1"), Duration.ofHours(8));

        // user 索引随 create 落盘（SADD + TTL）
        assertThat(rawRedis.hasKey("auth:user-tokens:u-revoke")).isTrue();
        assertThat(rawRedis.getExpire("auth:user-tokens:u-revoke", java.util.concurrent.TimeUnit.SECONDS))
                .isPositive();

        tokenStore.revokeByUserId("u-revoke");

        assertThat(tokenStore.resolve(first.token())).isEmpty();
        assertThat(tokenStore.resolve(second.token())).isEmpty();
        assertThat(tokenStore.resolve(other.token())).isPresent(); // 他用户 token 不受影响
        assertThat(rawRedis.hasKey("auth:user-tokens:u-revoke")).isFalse(); // 索引一并清理

        // 幂等：重复吊销未知用户 no-op
        tokenStore.revokeByUserId("u-never-existed");
        assertThat(tokenStore.resolve(other.token())).isPresent();
    }

    /** UserPasswordChanged 事件 → 监听器吊销该用户全部 token（identity 改密通道的会话失效承接）。 */
    @Test
    void userPasswordChangedEventRevokesThatUsersTokens() {
        StoredToken oldToken = tokenStore.create(grant("u-pw", "t-2"), Duration.ofHours(8));
        StoredToken anotherUser = tokenStore.create(grant("u-other", "t-2"), Duration.ofHours(8));
        assertThat(tokenStore.resolve(oldToken.token())).isPresent();

        eventPublisher.publishEvent(new UserPasswordChanged("t-2", "u-pw", Instant.now()));

        assertThat(tokenStore.resolve(oldToken.token())).isEmpty();
        assertThat(tokenStore.resolve(anotherUser.token())).isPresent();
    }
}
