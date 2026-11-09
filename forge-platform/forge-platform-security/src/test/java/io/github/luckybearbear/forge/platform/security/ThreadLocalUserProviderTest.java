package io.github.luckybearbear.forge.platform.security;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThreadLocalUserProviderTest {

    private final ThreadLocalUserProvider provider = new ThreadLocalUserProvider();

    @AfterEach
    void tearDown() {
        ThreadLocalUserProvider.clear();
    }

    @Test
    void setThenReadInSameThread() {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        assertThat(provider.currentUser()).contains(new CurrentUser("u-1", "alice"));
    }

    @Test
    void emptyWhenUnset() {
        assertThat(provider.currentUser()).isEmpty();
    }

    @Test
    void isolatedAcrossThreads() throws InterruptedException {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertThat(new ThreadLocalUserProvider().currentUser()).isEmpty();
            } catch (Exception e) {
                failure.set(e);
            }
        });
        other.start();
        other.join();
        assertThat(failure.get()).isNull();
    }

    @Test
    void clearRemovesValue() {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        ThreadLocalUserProvider.clear();
        assertThat(provider.currentUser()).isEmpty();
    }
}
