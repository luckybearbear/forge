package io.github.luckybearbear.forge.platform.core;

import java.util.Optional;

/** 当前租户供给接口：由 platform-security（TTL 实现）提供，platform-data/cache 消费，避免模块间耦合。 */
public interface CurrentTenantSupplier {

    Optional<String> currentTenantId();

    boolean ignore();
}
