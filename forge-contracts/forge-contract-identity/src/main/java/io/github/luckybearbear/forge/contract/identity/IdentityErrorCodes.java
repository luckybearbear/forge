package io.github.luckybearbear.forge.contract.identity;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 身份域错误码（IDENT- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum IdentityErrorCodes implements ErrorCode {

    IDENT_USER_NOT_FOUND("IDENT-0404", "用户不存在: {}"),
    IDENT_USERNAME_DUPLICATED("IDENT-0409", "用户名已存在: {}"),
    IDENT_BAD_CREDENTIALS("IDENT-0401", "用户名或密码错误"),
    IDENT_PASSWORD_TOO_WEAK("IDENT-0400", "密码强度不足");

    private final String code;
    private final String defaultMessage;

    IdentityErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
