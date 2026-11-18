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

    /** 按租户吊销（Phase 3 前置 Task 1）：只吊销目标租户 token；tenantId=null 的 admin token 不受影响；幂等。 */
    @Test
    void revokeByTenantIdRevokesOnlyThatTenantsTokens() {
        StoredToken aFirst = store.create(new TokenGrant("u-ma1", "tenant-mem-a", "MEMBER", Set.of()), Duration.ofHours(1));
        StoredToken aSecond = store.create(new TokenGrant("u-ma2", "tenant-mem-a", "MEMBER", Set.of()), Duration.ofHours(1));
        StoredToken bFirst = store.create(new TokenGrant("u-mb1", "tenant-mem-b", "MEMBER", Set.of()), Duration.ofHours(1));
        StoredToken admin = store.create(new TokenGrant("root-mem", null, "PLATFORM", Set.of()), Duration.ofHours(1));

        store.revokeByTenantId("tenant-mem-a");

        assertThat(store.resolve(aFirst.token())).isEmpty();
        assertThat(store.resolve(aSecond.token())).isEmpty();
        assertThat(store.resolve(bFirst.token())).isPresent();
        assertThat(store.resolve(admin.token())).isPresent(); // admin token（无租户）不入租户索引
        store.revokeByTenantId("t-unknown"); // 幂等 no-op
        assertThat(store.resolve(bFirst.token())).isPresent();
    }

    private static void waitQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
