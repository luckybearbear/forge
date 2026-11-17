package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 实现（结构选型：String 值 + JSON，非 hash——Redis hash 无单键整体 TTL 的便捷 API，
 * 滑动续期需整值重写时 String 值最简且语义完整）：
 *
 * <ul>
 *   <li>键：{@code auth:token:{token}}（opaque UUIDv7 token，不编码任何身份信息）</li>
 *   <li>值：StoredToken JSON（token/grant/expiresAt，Jackson 3 序列化，Instant → ISO-8601）</li>
 *   <li>TTL：键级过期即 token 生命周期；refresh 重写值（新 expiresAt）+ 重设 TTL，二者恒一致</li>
 *   <li>resolve 双保险：键已被 Redis 逐出（TTL 到）自然 miss；JSON 内 expiresAt 再校验一次
 *       （防御 refresh 与逐出竞态及外部改 TTL 的运维操作）</li>
 * </ul>
 *
 * 语义对齐 TokenStore 四方法；生产实现，无 Redis 类路径时由自动配置降级 InMemoryTokenStore。
 */
public class RedisTokenStore implements TokenStore {

    static final String KEY_PREFIX = "auth:token:";

    private final StringRedisTemplate redis;
    private final IdGenerator idGenerator = new UuidV7Generator();
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public StoredToken create(TokenGrant grant, Duration ttl) {
        String token = idGenerator.nextId();
        StoredToken stored = new StoredToken(token, grant, Instant.now().plus(ttl));
        redis.opsForValue().set(key(token), mapper.writeValueAsString(stored), ttl);
        return stored;
    }

    @Override
    public Optional<StoredToken> resolve(String token) {
        String json = redis.opsForValue().get(key(token));
        if (json == null) {
            return Optional.empty();
        }
        StoredToken stored = mapper.readValue(json, StoredToken.class);
        return stored.expiresAt().isAfter(Instant.now()) ? Optional.of(stored) : Optional.empty();
    }

    @Override
    public void revoke(String token) {
        redis.delete(key(token));
    }

    @Override
    public void refresh(String token, Duration ttl) {
        String json = redis.opsForValue().get(key(token));
        if (json == null) {
            return; // 已过期/已吊销：语义对齐 InMemoryTokenStore.computeIfPresent（no-op）
        }
        StoredToken stored = mapper.readValue(json, StoredToken.class);
        StoredToken renewed = new StoredToken(token, stored.grant(), Instant.now().plus(ttl));
        redis.opsForValue().set(key(token), mapper.writeValueAsString(renewed), ttl);
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }
}
