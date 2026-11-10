package io.github.luckybearbear.forge.platform.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import java.util.Optional;

/** ThreadLocal 实现。Web 场景必须由 Filter 在 finally 中 clear，防止串号。 */
public class ThreadLocalUserProvider implements CurrentUserProvider {

    private static final ThreadLocal<CurrentUser> HOLDER = new TransmittableThreadLocal<CurrentUser>() {
        @Override
        protected CurrentUser childValue(CurrentUser parentValue) {
            return null; // 裸线程不继承：传播仅经 TtlRunnable/TtlCallable 显式包装
        }
    };

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        return Optional.ofNullable(HOLDER.get());
    }
}
