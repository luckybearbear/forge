package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @Test
    void clampsInvalidValues() {
        assertThat(new PageQuery(0, 0)).isEqualTo(new PageQuery(1, 20));
        assertThat(new PageQuery(3, 9999)).isEqualTo(new PageQuery(3, 200));
    }

    @Test
    void offsetIsZeroBasedPageTimesSize() {
        assertThat(new PageQuery(2, 50).offset()).isEqualTo(50);
    }
}
