package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Arrays;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

/**
 * 以租户前缀 cacheName 的 RedisCacheManager（spec §6.1 入口 3）。
 *
 * <p>覆写 {@code getCache(String)} 将逻辑名映射为 {@code t:{tenantId}:{name}} / {@code p:{name}}，
 * 内部 cacheMap（AbstractCacheManager）以映射后的名字为键（javap 实证 {@code getCache} 非 final、
 * {@code lookupCache} 虽 final 但仅以传入名查表），重复获取同一逻辑名命中同一实例。
 * starter 集成（Bean 装配 + 运行时验证）在 Phase 2b。
 *
 * <p>注意：{@code getCacheNames()} 返回的是前缀化后的名字。
 */
public class TenantRedisCacheManager extends RedisCacheManager {

    private final CurrentTenantSupplier tenantSupplier;

    public TenantRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfiguration,
                                   CurrentTenantSupplier tenantSupplier) {
        super(cacheWriter, cacheConfiguration);
        this.tenantSupplier = tenantSupplier;
    }

    public TenantRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfiguration,
                                   CurrentTenantSupplier tenantSupplier, String... initialCacheNames) {
        super(cacheWriter, cacheConfiguration, prefix(initialCacheNames, tenantSupplier));
        this.tenantSupplier = tenantSupplier;
    }

    @Override
    public Cache getCache(String name) {
        return super.getCache(TenantCacheKeys.cacheName(name, tenantSupplier));
    }

    private static String[] prefix(String[] names, CurrentTenantSupplier supplier) {
        return Arrays.stream(names).map(n -> TenantCacheKeys.cacheName(n, supplier)).toArray(String[]::new);
    }
}
