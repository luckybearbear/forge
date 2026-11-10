package io.github.luckybearbear.forge.platform.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import java.util.Optional;

/**
 * 租户上下文（TTL 承载）。Web 请求链由 TokenAuthFilter 填充并 finally 清理；
 * 异步/事件场景不依赖隐式传播——payload 显式携带租户，监听器显式 set/clear（spec §6.1）。
 * 平台视角：ignore=true 或无租户时解析为 PLATFORM_TENANT_ID（"0"，租户表恒空）。
 */
public final class TenantContextHolder {

    public static final String PLATFORM_TENANT_ID = "0";

    private static final ThreadLocal<String> TENANT = new TransmittableThreadLocal<String>() {
        @Override
        protected String childValue(String parentValue) {
            return null; // 裸线程不继承：传播仅经 TtlRunnable/TtlCallable 显式包装（capture/replay 走 set/get，不经 childValue）
        }
    };
    private static final ThreadLocal<Boolean> IGNORE = new TransmittableThreadLocal<Boolean>() {
        @Override
        protected Boolean childValue(Boolean parentValue) {
            return null;
        }
    };

    private TenantContextHolder() {}

    public static void setTenantId(String tenantId) { TENANT.set(tenantId); }

    public static Optional<String> getTenantId() { return Optional.ofNullable(TENANT.get()); }

    public static String getRequiredTenantId() {
        return getTenantId().orElseThrow(() -> new IllegalStateException("no tenant in current context"));
    }

    public static void setIgnore(boolean ignore) { IGNORE.set(ignore); }

    public static boolean isIgnore() { return Boolean.TRUE.equals(IGNORE.get()); }

    public static void clear() {
        TENANT.remove();
        IGNORE.remove();
    }
}
