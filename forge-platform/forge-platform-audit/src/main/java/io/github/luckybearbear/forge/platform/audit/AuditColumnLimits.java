package io.github.luckybearbear.forge.platform.audit;

/** 事件 payload 字段与落库列长的边界（api_access_log/api_error_log 迁移）：事件构造侧统一截断。 */
final class AuditColumnLimits {

    static final int TENANT_ID = 36;
    static final int USER_ID = 36;
    static final int METHOD = 8;
    static final int PATH = 512;
    static final int EXCEPTION = 512;

    private AuditColumnLimits() {}

    static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
