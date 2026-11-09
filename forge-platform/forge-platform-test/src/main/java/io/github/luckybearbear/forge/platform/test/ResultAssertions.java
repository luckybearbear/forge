package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.core.Result;
import org.assertj.core.api.Assertions;

/** Demo/业务测试常用断言。 */
public final class ResultAssertions {

    public static <T> void assertOk(Result<T> result, T expectedData) {
        Assertions.assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
        Assertions.assertThat(result.data()).isEqualTo(expectedData);
    }

    public static void assertError(Result<?> result, String expectedCode) {
        Assertions.assertThat(result.code()).isEqualTo(expectedCode);
    }

    private ResultAssertions() {}
}
