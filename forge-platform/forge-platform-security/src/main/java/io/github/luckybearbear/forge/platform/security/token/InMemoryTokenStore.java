package io.github.luckybearbear.forge.platform.security.token;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现：测试与无 Redis 开发降级用。生产必须 Redis。 */
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, StoredToken> store = new ConcurrentHashMap<>();
    private final IdGenerator idGenerator = new UuidV7Generator();

    @Override
    public StoredToken create(TokenGrant grant, Duration ttl) {
        String token = idGenerator.nextId();
        StoredToken stored = new StoredToken(token, grant, Instant.now().plus(ttl));
        store.put(token, stored);
        return stored;
    }

    @Override
    public Optional<StoredToken> resolve(String token) {
        return Optional.ofNullable(store.get(token)).filter(t -> t.expiresAt().isAfter(Instant.now()));
    }

    @Override
    public void revoke(String token) {
        store.remove(token);
    }

    @Override
    public void refresh(String token, Duration ttl) {
        store.computeIfPresent(token, (k, v) -> new StoredToken(k, v.grant(), Instant.now().plus(ttl)));
    }
}
