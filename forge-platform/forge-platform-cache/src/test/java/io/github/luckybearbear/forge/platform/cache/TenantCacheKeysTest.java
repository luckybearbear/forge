package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TenantCacheKeysTest {

    @Test
    void tenantPrefixApplied() {
        assertThat(TenantCacheKeys.tenantKey("user:1", supplier("t-1", false))).isEqualTo("t:t-1:user:1");
        assertThat(TenantCacheKeys.cacheName("perms", supplier("t-1", false))).isEqualTo("t:t-1:perms");
    }

    @Test
    void platformViewWithoutPrefix() {
        assertThat(TenantCacheKeys.tenantKey("cfg", supplier(null, true))).isEqualTo("p:cfg");
        assertThat(TenantCacheKeys.tenantKey("cfg", supplier(null, false))).isEqualTo("p:cfg");
    }

    private CurrentTenantSupplier supplier(String tenantId, boolean ignore) {
        return new CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return Optional.ofNullable(tenantId); }
            @Override public boolean ignore() { return ignore; }
        };
    }
}
