package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import java.util.function.Supplier;

/** 租户上下文测试工具：runAs 显式 set/finally clear；runAsPlatform 模拟平台视角（ignore）。 */
public final class TenantTestSupport {

    public static void runAs(String tenantId, Runnable action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    public static <T> T runAsResult(String tenantId, Supplier<T> action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    public static <T> T runAsPlatform(Supplier<T> action) {
        TenantContextHolder.setIgnore(true);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantTestSupport() {}
}
