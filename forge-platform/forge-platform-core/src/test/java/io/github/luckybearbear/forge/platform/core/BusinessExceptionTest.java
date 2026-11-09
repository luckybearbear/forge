package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void ofFormatsPlaceholders() {
        BusinessException ex = BusinessException.of(ErrorCodes.NOT_FOUND, "m-1");
        assertThat(ex.errorCode()).isEqualTo(ErrorCodes.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("请求的资源不存在: m-1");
        assertThat(ex.httpStatus()).isNull();
    }

    @Test
    void withHttpStatusIsFluent() {
        BusinessException ex = BusinessException.of(ErrorCodes.NOT_FOUND, "m-1").withHttpStatus(404);
        assertThat(ex.httpStatus()).isEqualTo(404);
    }
}
