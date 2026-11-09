package io.github.luckybearbear.forge.platform.core;

/**
 * 业务错误码。code 形如 "FORGE-0404"（^[A-Z][A-Z0-9]{1,9}-\d{4}$），与 HTTP 状态码解耦。
 */
public interface ErrorCode {

    String code();

    String defaultMessage();
}
