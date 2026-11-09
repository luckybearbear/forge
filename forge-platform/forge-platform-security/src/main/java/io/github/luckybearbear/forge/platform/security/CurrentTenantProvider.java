package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public interface CurrentTenantProvider {
    Optional<CurrentTenant> currentTenant();
}
