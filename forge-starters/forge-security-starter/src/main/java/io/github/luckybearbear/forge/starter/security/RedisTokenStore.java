package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
 *   <li>refresh Lua 原子化（2b 前置加固）：条件写（GET 存在才 SET）——读值与条件重写间键被
 *       吊销/逐出时不复活（revoke 后不可复活；旧 GET→SET 两步在窗口内可复活已吊销 token）</li>
 *   <li>resolve 双保险：键已被 Redis 逐出（TTL 到）自然 miss；JSON 内 expiresAt 再校验一次
 *       （防御 refresh 与逐出竞态及外部改 TTL 的运维操作）</li>
 *   <li><b>user 索引（Task 4 安全输入 2）</b>：create 时 SADD {@code auth:user-tokens:{userId}} +
 *       键级 TTL（= token TTL）；refresh 续期时同步续索引 TTL（索引恒 ≥ 其内最长 token 生命周期）；
 *       {@code revokeByUserId} 遍历索引删除 token 键并清索引——改密吊销既有会话的承接点</li>
 * </ul>
 *
 * 语义对齐 TokenStore 五方法；生产实现，无 Redis 类路径时由自动配置降级 InMemoryTokenStore。
 */
public class RedisTokenStore implements TokenStore {

    static final String KEY_PREFIX = "auth:token:";
    static final String USER_INDEX_PREFIX = "auth:user-tokens:";

    /**
     * 条件重写（单键原子）：键存在 → 以 ARGV[1]（新 JSON）+ PX ARGV[2]（毫秒 TTL）重设并返回 1；
     * 键不存在（过期/吊销）→ 不创建、返回 0。参数经 {@code RedisTemplate.execute(RedisScript, keys, args)}
     * 走 String 序列化（与键值编码一致）。
     */
    private static final RedisScript<Boolean> REFRESH_SCRIPT =
            RedisScript.of("if redis.call('GET', KEYS[1]) then "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) return true "
                    + "else return false end", Boolean.class);

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
        indexToken(grant.userId(), token, ttl);
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
        Boolean refreshed = redis.execute(REFRESH_SCRIPT, List.of(key(token)),
                mapper.writeValueAsString(renewed), String.valueOf(ttl.toMillis()));
        if (!Boolean.TRUE.equals(refreshed)) {
            return; // 读值与条件重写窗口内键消失（过期/吊销竞态）：不复活，静默放弃
        }
        indexToken(stored.grant().userId(), token, ttl); // 续期后索引 TTL 同步延长（索引 ≥ 最长 token 生命周期）
    }

    /**
     * 按用户吊销全部 token（幂等）：遍历索引删除 token 键 + 清索引键。无租户语义——token 键空间
     * 全局唯一，按 userId 全域吊销（改密等会话失效事件由 security-starter 监听器调用）。
     */
    @Override
    public void revokeByUserId(String userId) {
        String indexKey = userIndexKey(userId);
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens != null) {
            tokens.forEach(token -> redis.delete(key(token)));
        }
        redis.delete(indexKey);
    }

    /** 登记用户索引：SADD + 键级 TTL（EXPIRE 覆写为本次写值——统一 TTL 策略下新写者剩余寿命最长，
     * 索引恒 ≥ 其内最长 token；异 TTL 策略属后续演进注记）。 */
    private void indexToken(String userId, String token, Duration ttl) {
        String indexKey = userIndexKey(userId);
        redis.opsForSet().add(indexKey, token);
        redis.expire(indexKey, ttl);
    }

    private static String userIndexKey(String userId) {
        return USER_INDEX_PREFIX + userId;
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }
}
