package io.github.luckybearbear.forge.platform.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    void producesVersion7Variant2Uuid() {
        UUID uuid = UUID.fromString(generator.nextId());
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void producesUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void roughlyMonotonicTimestampPrefix() {
        long previous = 0;
        for (int i = 0; i < 100; i++) {
            UUID uuid = UUID.fromString(generator.nextId());
            long ts = uuid.getMostSignificantBits() >>> 16;
            assertThat(ts).isGreaterThanOrEqualTo(previous);
            previous = ts;
        }
    }
}
