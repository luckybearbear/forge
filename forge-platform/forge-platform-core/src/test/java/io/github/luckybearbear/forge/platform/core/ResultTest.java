package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    @Test
    void okCarriesSuccessCodeAndData() {
        Result<String> result = Result.ok("data");
        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data()).isEqualTo("data");
    }

    @Test
    void okVoidHasNullData() {
        assertThat(Result.ok().data()).isNull();
    }

    @Test
    void errorUsesErrorCodeAndMessage() {
        Result<Void> result = Result.error(ErrorCodes.NOT_FOUND, "找不到模具: m-1");
        assertThat(result.code()).isEqualTo("FORGE-0404");
        assertThat(result.message()).isEqualTo("找不到模具: m-1");
        assertThat(result.data()).isNull();
    }

    @Test
    void errorRejectsSuccessCode() {
        ErrorCode fakeOk = new ErrorCode() {
            @Override public String code() { return "OK"; }
            @Override public String defaultMessage() { return "x"; }
        };
        assertThatThrownBy(() -> Result.error(fakeOk))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
