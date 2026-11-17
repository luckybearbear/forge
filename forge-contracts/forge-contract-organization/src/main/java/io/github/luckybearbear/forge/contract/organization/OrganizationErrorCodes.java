package io.github.luckybearbear.forge.contract.organization;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 组织域错误码（ORG- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum OrganizationErrorCodes implements ErrorCode {

    ORG_UNIT_NOT_FOUND("ORG-0404", "组织单元不存在: {}"),
    ORG_CYCLE_DETECTED("ORG-0409", "组织树检测到环: {}"),
    ORG_POSITION_NOT_FOUND("ORG-0414", "岗位不存在: {}");

    private final String code;
    private final String defaultMessage;

    OrganizationErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
