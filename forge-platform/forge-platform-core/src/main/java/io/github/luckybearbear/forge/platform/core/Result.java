package io.github.luckybearbear.forge.platform.core;

/**
 * 统一响应体。成功码为字符串常量 "OK"；错误码见 {@link ErrorCode} 体系。
 * 注意：platform-core 保持零 Spring 依赖，校验手写（勿用 org.springframework.util.Assert）。
 */
public record Result<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static Result<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.defaultMessage());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        requireError(errorCode);
        return new Result<>(errorCode.code(), message, null);
    }

    private static void requireError(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        if (errorCode.code() == null || errorCode.code().isBlank()) {
            throw new IllegalArgumentException("errorCode.code must not be blank");
        }
        if (SUCCESS_CODE.equals(errorCode.code())) {
            throw new IllegalArgumentException("errorCode must not be the success code");
        }
    }
}
