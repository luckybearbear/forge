package io.github.luckybearbear.forge.platform.security.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashAndMatch() {
        String encoded = hasher.hash("s3cret!");
        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(hasher.matches("s3cret!", encoded)).isTrue();
        assertThat(hasher.matches("wrong", encoded)).isFalse();
    }
}
