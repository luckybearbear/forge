package io.github.luckybearbear.forge.platform.security;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

    private final TenantContextHolderSupplier supplier = new TenantContextHolderSupplier();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void setGetClearRoundTrip() {
        TenantContextHolder.setTenantId("t-1");
        assertThat(supplier.currentTenantId()).contains("t-1");
        assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo("t-1");
        TenantContextHolder.clear();
        assertThat(supplier.currentTenantId()).isEmpty();
        assertThatThrownBy(TenantContextHolder::getRequiredTenantId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoreFlagAndSupplier() {
        TenantContextHolder.setIgnore(true);
        assertThat(supplier.ignore()).isTrue();
        TenantContextHolder.setIgnore(false);
        assertThat(supplier.ignore()).isFalse();
    }

    @Test
    void isolatedAcrossThreads() throws InterruptedException {
        TenantContextHolder.setTenantId("t-1");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertThat(TenantContextHolder.getTenantId()).isEmpty();
            } catch (Throwable e) { // Throwable：AssertionError extends Error
                failure.set(e);
            }
        });
        other.start();
        other.join();
        assertThat(failure.get()).isNull();
    }
}
