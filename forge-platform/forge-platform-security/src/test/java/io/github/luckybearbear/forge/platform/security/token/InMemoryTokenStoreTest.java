package io.github.luckybearbear.forge.platform.security.token;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenStoreTest {

    private final InMemoryTokenStore store = new InMemoryTokenStore();
    private final TokenGrant grant = new TokenGrant("u-1", "t-1", "MEMBER", Set.of("mold:create"));

    @Test
    void createResolveRoundTrip() {
        StoredToken created = store.create(grant, Duration.ofHours(1));
        assertThat(created.token()).isNotBlank();
        assertThat(store.resolve(created.token())).contains(created);
        assertThat(created.grant().authorities()).contains("mold:create");
    }

    @Test
    void revokedTokenUnresolvable() {
        StoredToken created = store.create(grant, Duration.ofHours(1));
        store.revoke(created.token());
        assertThat(store.resolve(created.token())).isEmpty();
    }

    @Test
    void expiredTokenUnresolvable() {
        StoredToken created = store.create(grant, Duration.ofMillis(150));
        waitQuietly(300);
        assertThat(store.resolve(created.token())).isEmpty();
    }

    @Test
    void refreshExtendsExpiry() {
        StoredToken created = store.create(grant, Duration.ofMillis(250));
        store.refresh(created.token(), Duration.ofHours(1));
        waitQuietly(300);
        assertThat(store.resolve(created.token())).isPresent();
    }

    private static void waitQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
