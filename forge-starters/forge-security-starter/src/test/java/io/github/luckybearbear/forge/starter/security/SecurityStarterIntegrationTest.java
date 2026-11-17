package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.TenantIgnore;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引入 forge-security-starter 即得：无状态 SecurityFilterChain（401/403 → Result 统一体）、
 * RedisTokenStore（真实 redis:8-alpine 容器，经 platform-test REDIS @ServiceConnection 直连）、
 * TokenAuthFilter 注链（认证 + 租户填充 + 滑动续期）、@TenantIgnore AOP（平台视角）。
 */
@SpringBootTest(classes = SecurityStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        @RestController
        static class ProbeController {
            @GetMapping("/probe/secure")
            String secure() {
                return "tenant=" + TenantContextHolder.getTenantId().orElse("none");
            }

            @GetMapping("/probe/open")
            @TenantIgnore
            String open() {
                return "ignore=" + TenantContextHolder.isIgnore();
            }
        }
    }

    @Autowired TokenStore tokenStore;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @LocalServerPort int port;

    // Boot 4 无 TestRestTemplate：裸 RestTemplate + 透传 handler（不抛 4xx/5xx），
    // 沿用 forge-spring-boot-starter StarterSmokeTest 的既有模式。
    RestTemplate rest = noThrowRestTemplate();

    private static RestTemplate noThrowRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }

    @Test
    void unauthenticatedGets401Result() {
        ResponseEntity<String> response = rest.getForEntity(url("/probe/secure"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("FORGE-0401");
    }

    @Test
    void validTokenAuthenticatesAndFillsTenant() {
        // Redis 类路径在（starter 带 data-redis）→ 必须装配 RedisTokenStore；
        // 不断言类型则 InMemory 兜底静默通过（条件评估报告实证过该风险）
        assertThat(tokenStore).isInstanceOf(RedisTokenStore.class);

        StoredToken stored = tokenStore.create(grant("u-1", "t-7"), Duration.ofHours(8));

        ResponseEntity<String> response = rest.exchange(url("/probe/secure"),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(bearer(stored.token())), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("tenant=t-7");
    }

    @Test
    void revokedTokenRejected() {
        StoredToken stored = tokenStore.create(grant("u-2", "t-7"), Duration.ofHours(8));
        assertThat(rest.exchange(url("/probe/secure"), org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(bearer(stored.token())), String.class).getStatusCode().value()).isEqualTo(200);

        tokenStore.revoke(stored.token());

        ResponseEntity<String> rejected = rest.exchange(url("/probe/secure"),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(bearer(stored.token())), String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(401);
        assertThat(rejected.getBody()).contains("FORGE-0401");
    }

    @Test
    void tenantIgnoreAnnotationActivatesPlatformView() {
        // 默认 permit-all 不含 /probe/open：以有效 token 访问，验证注解本身切换平台视角
        StoredToken stored = tokenStore.create(grant("u-3", "t-7"), Duration.ofHours(8));

        ResponseEntity<String> response = rest.exchange(url("/probe/open"),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(bearer(stored.token())), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("ignore=true");
    }

    @Test
    void refreshDoesNotResurrectRevokedToken() {
        // refresh Lua 条件写原子化（2b 前置加固）：GET 存在才 SET——revoke 后 refresh
        // 不得重建键（复活即已吊销 token 重新可用，GET→SET 窗口内吊销亦不可复活）
        StoredToken stored = tokenStore.create(grant("u-4", "t-7"), Duration.ofHours(8));
        tokenStore.revoke(stored.token());
        tokenStore.refresh(stored.token(), Duration.ofHours(8));
        assertThat(stringRedisTemplate.hasKey(RedisTokenStore.KEY_PREFIX + stored.token())).isFalse();
        assertThat(tokenStore.resolve(stored.token())).isEmpty();
    }

    @Test
    void refreshRenewsExistingKeyTtlAndExpiry() {
        StoredToken stored = tokenStore.create(grant("u-5", "t-7"), Duration.ofSeconds(5));
        tokenStore.refresh(stored.token(), Duration.ofHours(1));
        // 既有键续期：键 TTL 由脚本 PX 重设为长窗口，值内 expiresAt 前移（滑动续期语义不变）
        assertThat(stringRedisTemplate.getExpire(RedisTokenStore.KEY_PREFIX + stored.token(),
                TimeUnit.SECONDS)).isGreaterThan(1800L);
        assertThat(tokenStore.resolve(stored.token()))
                .hasValueSatisfying(renewed -> assertThat(renewed.expiresAt()).isAfter(stored.expiresAt()));
    }

    private static TokenGrant grant(String userId, String tenantId) {
        return new TokenGrant(userId, tenantId, "USER", Set.of("x"));
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
