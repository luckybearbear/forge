package io.github.luckybearbear.forge.platform.cache;

import java.time.Duration;
import java.util.Optional;

/** 缓存抽象 SPI。Phase 2 提供 Redis 实现与 forge-cache-starter。 */
public interface CacheFacade {

    void put(String key, Object value, Duration ttl);

    Optional<Object> get(String key);

    void evict(String key);
}
