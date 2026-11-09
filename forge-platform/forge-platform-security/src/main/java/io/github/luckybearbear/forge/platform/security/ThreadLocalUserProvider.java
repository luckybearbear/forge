package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

/** ThreadLocal 实现。Web 场景必须由 Filter 在 finally 中 clear，防止串号。 */
public class ThreadLocalUserProvider implements CurrentUserProvider {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

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
