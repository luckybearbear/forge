package io.github.luckybearbear.forge.platform.data;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/** ignore/无租户 → 平台租户 "0"（租户表恒空 = 平台视角，spec §6.1）。 */
public class ForgeTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String PLATFORM_TENANT_ID = "0";

    private final CurrentTenantSupplier supplier;

    public ForgeTenantIdentifierResolver(CurrentTenantSupplier supplier) {
        this.supplier = supplier;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (supplier.ignore()) {
            return PLATFORM_TENANT_ID;
        }
        return supplier.currentTenantId().orElse(PLATFORM_TENANT_ID);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
