package io.github.luckybearbear.forge.platform.security.token;

import java.time.Duration;
import java.util.Optional;

/** Token 存储 SPI：Redis 实现（2a starter 装配）/内存实现（测试与开发降级）。 */
public interface TokenStore {

    StoredToken create(TokenGrant grant, Duration ttl);

    Optional<StoredToken> resolve(String token);

    void revoke(String token);

    void refresh(String token, Duration ttl);
}
