package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantRedisCacheManagerTest {

    @Test
    void cacheNamePrefixedWithTenant() {
        RedisCacheManager manager = new TenantRedisCacheManager(
                mock(RedisCacheWriter.class), RedisCacheConfiguration.defaultCacheConfig(), supplier("t-1", false));

        Cache cache = manager.getCache("perms");

        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("t:t-1:perms");
        assertThat(manager.getCache("perms")).isSameAs(cache); // 前缀化后命中同一实例
    }

    @Test
    void platformViewWithoutTenant() {
        RedisCacheManager manager = new TenantRedisCacheManager(
                mock(RedisCacheWriter.class), RedisCacheConfiguration.defaultCacheConfig(), supplier(null, true));

        assertThat(manager.getCache("cfg").getName()).isEqualTo("p:cfg");
    }

    private CurrentTenantSupplier supplier(String tenantId, boolean ignore) {
        return new CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return Optional.ofNullable(tenantId); }
            @Override public boolean ignore() { return ignore; }
        };
    }
}
