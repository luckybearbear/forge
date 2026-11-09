package io.github.luckybearbear.forge.contract.mold;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 模具域错误码（MOLD- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum MoldErrorCodes implements ErrorCode {

    MOLD_NOT_FOUND("MOLD-0404", "模具不存在: {}");

    private final String code;
    private final String defaultMessage;

    MoldErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
