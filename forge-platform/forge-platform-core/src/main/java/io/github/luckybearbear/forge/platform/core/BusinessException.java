package io.github.luckybearbear.forge.platform.core;

import org.slf4j.helpers.MessageFormatter;

/**
 * 业务异常。HTTP 状态仅作传输提示：默认 200，可通过 {@link #withHttpStatus(int)} 覆盖。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private Integer httpStatus;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 按 defaultMessage 的 {} 占位格式化构造。 */
    public static BusinessException of(ErrorCode errorCode, Object... messageArgs) {
        String message = MessageFormatter.arrayFormat(errorCode.defaultMessage(), messageArgs).getMessage();
        return new BusinessException(errorCode, message);
    }

    public BusinessException withHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    public ErrorCode errorCode() { return errorCode; }

    /** null 表示使用默认 200。 */
    public Integer httpStatus() { return httpStatus; }
}
