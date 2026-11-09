package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public class ThreadLocalTenantProvider implements CurrentTenantProvider {

    private static final ThreadLocal<CurrentTenant> HOLDER = new ThreadLocal<>();

    public static void set(CurrentTenant tenant) {
        HOLDER.set(tenant);
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Override
    public Optional<CurrentTenant> currentTenant() {
        return Optional.ofNullable(HOLDER.get());
    }
}
