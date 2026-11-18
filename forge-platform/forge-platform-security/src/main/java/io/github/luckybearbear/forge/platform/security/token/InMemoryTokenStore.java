package io.github.luckybearbear.forge.platform.security.token;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现：测试与无 Redis 开发降级用。生产必须 Redis。 */
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, StoredToken> store = new ConcurrentHashMap<>();
    /** userId → token 反向索引（revokeByUserId 承接点；revoke(token) 惰性残留，随整组吊销回收）。 */
    private final Map<String, Set<String>> tokensByUser = new ConcurrentHashMap<>();
    /** tenantId → token 反向索引（revokeByTenantId 承接点）；tenantId null 的 admin token 不入本索引。 */
    private final Map<String, Set<String>> tokensByTenant = new ConcurrentHashMap<>();
    private final IdGenerator idGenerator = new UuidV7Generator();

    @Override
    public StoredToken create(TokenGrant grant, Duration ttl) {
        String token = idGenerator.nextId();
        StoredToken stored = new StoredToken(token, grant, Instant.now().plus(ttl));
        store.put(token, stored);
        tokensByUser.computeIfAbsent(grant.userId(), key -> ConcurrentHashMap.newKeySet()).add(token);
        if (grant.tenantId() != null) {
            tokensByTenant.computeIfAbsent(grant.tenantId(), key -> ConcurrentHashMap.newKeySet()).add(token);
        }
        return stored;
    }

    @Override
    public Optional<StoredToken> resolve(String token) {
        return Optional.ofNullable(store.get(token)).filter(t -> t.expiresAt().isAfter(Instant.now()));
    }

    @Override
    public void revoke(String token) {
        store.remove(token); // 索引残留无害：revokeByUserId/revokeByTenantId 时统一回收（幂等 remove）
    }

    @Override
    public void refresh(String token, Duration ttl) {
        store.computeIfPresent(token, (k, v) -> new StoredToken(k, v.grant(), Instant.now().plus(ttl)));
    }

    @Override
    public void revokeByUserId(String userId) {
        Set<String> tokens = tokensByUser.remove(userId);
        if (tokens != null) {
            tokens.forEach(store::remove); // tenant 索引残留同 revoke(token) 语义：revokeByTenantId 幂等回收
        }
    }

    @Override
    public void revokeByTenantId(String tenantId) {
        Set<String> tokens = tokensByTenant.remove(tenantId);
        if (tokens != null) {
            tokens.forEach(store::remove); // user 索引残留同 revoke(token) 语义：revokeByUserId 幂等回收
        }
    }
}
