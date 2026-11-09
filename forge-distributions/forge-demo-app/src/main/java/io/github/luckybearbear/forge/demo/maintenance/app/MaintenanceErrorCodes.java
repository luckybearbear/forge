package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 维修域错误码（MAINT- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum MaintenanceErrorCodes implements ErrorCode {

    MAINT_NOT_FOUND("MAINT-0404", "维修单不存在: {}");

    private final String code;
    private final String defaultMessage;

    MaintenanceErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
