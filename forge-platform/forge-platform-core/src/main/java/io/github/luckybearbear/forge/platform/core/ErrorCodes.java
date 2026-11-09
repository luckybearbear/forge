package io.github.luckybearbear.forge.platform.core;

/**
 * 平台级错误码（FORGE- 前缀保留段）。业务域前缀在 docs/standards/error-codes.md 登记。
 */
public enum ErrorCodes implements ErrorCode {

    PLATFORM_BAD_REQUEST("FORGE-0400", "请求参数不正确: {}"),
    UNAUTHORIZED("FORGE-0401", "账号未登录"),
    FORBIDDEN("FORGE-0403", "没有该操作权限"),
    NOT_FOUND("FORGE-0404", "请求的资源不存在: {}"),
    METHOD_NOT_ALLOWED("FORGE-0405", "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE("FORGE-0415", "媒体类型不支持"),
    CONCURRENT_LOCKED("FORGE-0423", "请求冲突，请稍后重试"),
    TOO_MANY_REQUESTS("FORGE-0429", "请求过于频繁"),
    INTERNAL_SERVER_ERROR("FORGE-0500", "系统异常"),
    NOT_IMPLEMENTED("FORGE-0501", "功能未实现或未开启");

    private final String code;
    private final String defaultMessage;

    ErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
