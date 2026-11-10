package io.github.luckybearbear.forge.platform.security;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;

public class TenantContextHolderSupplier implements CurrentTenantSupplier {

    @Override
    public Optional<String> currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    @Override
    public boolean ignore() {
        return TenantContextHolder.isIgnore();
    }
}
