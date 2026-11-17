package io.github.luckybearbear.forge.contract.authorization;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 授权域错误码（AUTH- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum AuthorizationErrorCodes implements ErrorCode {

    AUTH_ROLE_NOT_FOUND("AUTH-0404", "角色不存在: {}"),
    AUTH_PERMISSION_NOT_FOUND("AUTH-0414", "权限点不存在: {}"),
    AUTH_ROLE_CODE_DUPLICATED("AUTH-0409", "角色编码已存在: {}");

    private final String code;
    private final String defaultMessage;

    AuthorizationErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
