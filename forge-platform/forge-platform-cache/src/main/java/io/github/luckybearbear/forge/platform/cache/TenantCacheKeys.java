package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;

/** 租户 key 规则（spec §6.1 入口 3）。 */
public final class TenantCacheKeys {

    private TenantCacheKeys() {}

    public static String tenantKey(String key, CurrentTenantSupplier supplier) {
        return supplier.ignore()
                ? "p:" + key
                : supplier.currentTenantId().map(t -> "t:" + t + ":" + key).orElse("p:" + key);
    }

    public static String cacheName(String name, CurrentTenantSupplier supplier) {
        return tenantKey(name, supplier);
    }
}
