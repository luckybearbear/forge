package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultAssertionsTest {

    @Test
    void assertOkPassesOnMatchingData() {
        assertThatCode(() -> ResultAssertions.assertOk(Result.ok("x"), "x")).doesNotThrowAnyException();
    }

    @Test
    void assertErrorChecksCode() {
        Result<Void> result = Result.error(ErrorCodes.NOT_FOUND);
        assertThatCode(() -> ResultAssertions.assertError(result, "FORGE-0404"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ResultAssertions.assertError(result, "FORGE-0500"))
                .isInstanceOf(AssertionError.class);
    }
}
