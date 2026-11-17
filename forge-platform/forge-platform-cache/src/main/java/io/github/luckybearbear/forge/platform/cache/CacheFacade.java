package io.github.luckybearbear.forge.platform.cache;

import java.time.Duration;
import java.util.Optional;

/** 缓存抽象 SPI。Phase 2 提供 Redis 实现与 forge-cache-starter。 */
public interface CacheFacade {

    /**
     * 写入缓存。
     *
     * @param ttl 有效期；<b>null 或 zero/negative 表示不过期</b>（终审 Minor-6 明确语义：
     *            Redis 实现走无 EX/PX 的 SET，而非把 0/负值当有效 TTL 下发）
     */
    void put(String key, Object value, Duration ttl);

    Optional<Object> get(String key);

    void evict(String key);
}
