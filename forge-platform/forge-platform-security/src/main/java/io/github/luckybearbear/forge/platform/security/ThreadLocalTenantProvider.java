package io.github.luckybearbear.forge.platform.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import java.util.Optional;

public class ThreadLocalTenantProvider implements CurrentTenantProvider {

    private static final ThreadLocal<CurrentTenant> HOLDER = new TransmittableThreadLocal<CurrentTenant>() {
        @Override
        protected CurrentTenant childValue(CurrentTenant parentValue) {
            return null; // 裸线程不继承：传播仅经 TtlRunnable/TtlCallable 显式包装
        }
    };

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
