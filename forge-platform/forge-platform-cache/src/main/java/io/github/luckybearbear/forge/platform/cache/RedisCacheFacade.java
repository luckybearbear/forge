package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;

/** CacheFacade 的 Redis 实现（Phase 1 SPI 兑现）。 */
public class RedisCacheFacade implements CacheFacade {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CurrentTenantSupplier tenantSupplier;

    public RedisCacheFacade(RedisTemplate<String, Object> redisTemplate, CurrentTenantSupplier tenantSupplier) {
        this.redisTemplate = redisTemplate;
        this.tenantSupplier = tenantSupplier;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        String tenantKey = TenantCacheKeys.tenantKey(key, tenantSupplier);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            // 不过期（终审 Minor-6）：zero/negative/null 走无 EX/PX 的 SET，而非下发非法 TTL
            redisTemplate.opsForValue().set(tenantKey, value);
        } else {
            redisTemplate.opsForValue().set(tenantKey, value, ttl);
        }
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(TenantCacheKeys.tenantKey(key, tenantSupplier)));
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(TenantCacheKeys.tenantKey(key, tenantSupplier));
    }
}
