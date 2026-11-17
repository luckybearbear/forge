package io.github.luckybearbear.forge.contract.tenant;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 租户域错误码（TENANT- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum TenantErrorCodes implements ErrorCode {

    TENANT_NOT_FOUND("TENANT-0404", "租户不存在: {}"),
    TENANT_CODE_DUPLICATED("TENANT-0409", "租户编码已存在: {}"),
    TENANT_DISABLED("TENANT-0423", "租户已停用");

    private final String code;
    private final String defaultMessage;

    TenantErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
