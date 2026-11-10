package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheFacadeTest {

    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void putDelegatesWithTenantKeyAndTtl() {
        CurrentTenantSupplier supplier = supplier("t-1", false);
        Duration ttl = Duration.ofSeconds(60);
        new RedisCacheFacade(redisTemplate, supplier).put("user:1", "alice", ttl);

        verify(valueOps).set(eq(TenantCacheKeys.tenantKey("user:1", supplier)), same("alice"), same(ttl));
    }

    @Test
    void getDelegatesWithTenantKey() {
        CurrentTenantSupplier supplier = supplier("t-1", false);
        when(valueOps.get(TenantCacheKeys.tenantKey("user:1", supplier))).thenReturn("alice");

        Optional<Object> value = new RedisCacheFacade(redisTemplate, supplier).get("user:1");

        assertThat(value).contains("alice");
    }

    @Test
    void getAbsentKeyYieldsEmpty() {
        CurrentTenantSupplier supplier = supplier("t-1", false);
        when(valueOps.get(TenantCacheKeys.tenantKey("user:1", supplier))).thenReturn(null);

        assertThat(new RedisCacheFacade(redisTemplate, supplier).get("user:1")).isEmpty();
    }

    @Test
    void evictDelegatesWithTenantKey() {
        CurrentTenantSupplier supplier = supplier("t-1", false);
        new RedisCacheFacade(redisTemplate, supplier).evict("user:1");

        verify(redisTemplate).delete(TenantCacheKeys.tenantKey("user:1", supplier));
    }

    @Test
    void platformViewUsesPlatformPrefix() {
        CurrentTenantSupplier supplier = supplier(null, true);
        new RedisCacheFacade(redisTemplate, supplier).put("cfg", 1, Duration.ZERO);

        verify(valueOps).set(eq(TenantCacheKeys.tenantKey("cfg", supplier)), same(1), same(Duration.ZERO));
    }

    private CurrentTenantSupplier supplier(String tenantId, boolean ignore) {
        return new CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return Optional.ofNullable(tenantId); }
            @Override public boolean ignore() { return ignore; }
        };
    }
}
