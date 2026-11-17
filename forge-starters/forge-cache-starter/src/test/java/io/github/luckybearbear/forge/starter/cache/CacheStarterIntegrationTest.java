package io.github.luckybearbear.forge.starter.cache;

import io.github.luckybearbear.forge.platform.cache.CacheFacade;
import io.github.luckybearbear.forge.platform.cache.TenantRedisCacheManager;
import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import io.github.luckybearbear.forge.platform.security.TenantContextHolderSupplier;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * forge-cache-starter 容器集成（真实 redis:8-alpine 容器 @ServiceConnection 直连）：
 * RedisTemplate&lt;String,Object&gt; JSON 基线、RedisCacheFacade 租户前缀（原始 key 经
 * StringRedisTemplate 断言——redis-cli 式）、平台视角 {@code p:} 前缀、TTL 下发、
 * TenantRedisCacheManager @Primary（Spring Cache API 侧的租户隔离）。
 *
 * <p>CurrentTenantSupplier 由测试 @Bean 提供（TenantContextHolderSupplier，platform-security）：
 * 生产装配中该 bean 由 forge-data-starter 提供（@ConditionalOnMissingBean 让位治理），本 starter
 * 只声明接口依赖——仅引 cache-starter 而无 supplier 的应用按缺 bean fail-fast（README 约定）。
 *
 * <p><b>原始 key 断言一律经 Awaitility 有界等待</b>（≤2s，实测毫秒级即达）：写命令的连接与
 * 断言用的 StringRedisTemplate 连接不同——RedisCacheWriter 写入对其他连接的可见存在毫秒级窗口
 * （实证：紧邻 hasKey 偶发 false、1-4ms 后可见），同步立即断言会引入与本任务无关的 flake。
 */
@SpringBootTest(classes = CacheStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CacheStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        /** data-starter 不在类路径：测试自供 CurrentTenantSupplier（生产由 data-starter 装配）。 */
        @Bean
        CurrentTenantSupplier currentTenantSupplier() {
            return new TenantContextHolderSupplier();
        }
    }

    @Autowired CacheFacade cacheFacade;
    @Autowired CacheManager cacheManager;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    @Autowired StringRedisTemplate rawRedis; // 原始 key 断言（redis-cli 式只读视角）

    private static void awaitKeyPresent(StringRedisTemplate redis, String key) {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redis.hasKey(key)).isTrue());
    }

    private static void awaitKeyAbsent(StringRedisTemplate redis, String key) {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redis.hasKey(key)).isFalse());
    }

    @Test
    void facadePutGetEvictUnderTenantViewWritesTenantPrefixedKey() {
        TenantTestSupport.runAs("t-cache-1", () ->
                cacheFacade.put("auth:demo", new LinkedHashSet<>(Set.of("user:manage", "mold:read")),
                        Duration.ofMinutes(5)));

        // 原始 key 断言：t:{tenantId}: 前缀 + 值为 JSON（含类型信息与元素字面量）
        awaitKeyPresent(rawRedis, "t:t-cache-1:auth:demo");
        String json = rawRedis.opsForValue().get("t:t-cache-1:auth:demo");
        assertThat(json).contains("user:manage").contains("mold:read");

        // 租户视角 get 命中；TTL 已下发（put 5min → 原始键剩余 TTL > 0）
        TenantTestSupport.runAs("t-cache-1", () ->
                assertThat(cacheFacade.get("auth:demo")).isPresent());
        assertThat(rawRedis.getExpire("t:t-cache-1:auth:demo", TimeUnit.SECONDS)).isPositive();

        // 他租户视角 = 不同物理键 → miss（租户隔离在 key 层，不依赖业务代码自觉）
        TenantTestSupport.runAs("t-cache-2", () ->
                assertThat(cacheFacade.get("auth:demo")).isEmpty());

        // evict 删除的是本租户的原始键
        TenantTestSupport.runAs("t-cache-1", () -> cacheFacade.evict("auth:demo"));
        awaitKeyAbsent(rawRedis, "t:t-cache-1:auth:demo");
    }

    @Test
    void platformViewUsesPlatformPrefix() {
        TenantTestSupport.runAsPlatform(() -> {
            cacheFacade.put("global:config", "v1", Duration.ofMinutes(1));
            return null;
        });

        awaitKeyPresent(rawRedis, "p:global:config");
        assertThat(TenantTestSupport.runAsPlatform(() ->
                cacheFacade.get("global:config"))).contains("v1");
        // 平台视角与租户视角互不可见
        TenantTestSupport.runAs("t-cache-1", () ->
                assertThat(cacheFacade.get("global:config")).isEmpty());
    }

    /** facade 值经 RedisTemplate<String,Object> JSON 基线写入：模板本身即 String key + JSON value。 */
    @Test
    void redisTemplateBaselineIsStringKeyJsonValue() {
        redisTemplate.opsForValue().set("t:t-tpl:plain", "hello");

        awaitKeyPresent(rawRedis, "t:t-tpl:plain");
        assertThat(rawRedis.opsForValue().get("t:t-tpl:plain")).contains("hello");
        assertThat(redisTemplate.getKeySerializer())
                .isInstanceOf(org.springframework.data.redis.serializer.StringRedisSerializer.class);
        assertThat(redisTemplate.getValueSerializer())
                .isNotInstanceOf(org.springframework.data.redis.serializer.JdkSerializationRedisSerializer.class);
    }

    /** Spring Cache API 侧：TenantRedisCacheManager 为 @Primary，cacheName 映射 t:{tenantId}:{name}。 */
    @Test
    void cacheManagerIsTenantPrefixedAndPrimary() {
        assertThat(cacheManager).isInstanceOf(TenantRedisCacheManager.class);

        TenantTestSupport.runAs("t-cache-1", () -> {
            Cache cache = cacheManager.getCache("demoCache");
            cache.put("k", "v1");
        });
        // RedisCache 内部键形如 {cacheName}::{key}，cacheName 已带租户前缀
        awaitKeyPresent(rawRedis, "t:t-cache-1:demoCache::k");

        // 他租户同名 cache = 另一物理空间（miss 后写入互不覆盖）
        TenantTestSupport.runAs("t-cache-2", () -> {
            Cache cache = cacheManager.getCache("demoCache");
            assertThat(cache.get("k")).isNull();
            cache.put("k", "v2");
        });
        awaitKeyPresent(rawRedis, "t:t-cache-2:demoCache::k");

        TenantTestSupport.runAs("t-cache-1", () ->
                assertThat(cacheManager.getCache("demoCache").get("k").get()).isEqualTo("v1"));
        TenantTestSupport.runAs("t-cache-2", () ->
                assertThat(cacheManager.getCache("demoCache").get("k").get()).isEqualTo("v2"));
    }
}
