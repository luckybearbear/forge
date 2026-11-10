package io.github.luckybearbear.forge.platform.data;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractAuditableEntityTest {

    @Test
    void auditFieldsCarryJpaAndSpringDataAnnotations() throws Exception {
        assertThat(annotationOn("id", jakarta.persistence.Id.class)).isNotNull();
        assertThat(annotationOn("version", jakarta.persistence.Version.class)).isNotNull();
        assertThat(annotationOn("createdBy", CreatedBy.class)).isNotNull();
        assertThat(annotationOn("createdAt", CreatedDate.class)).isNotNull();
        assertThat(annotationOn("updatedBy", LastModifiedBy.class)).isNotNull();
        assertThat(annotationOn("updatedAt", LastModifiedDate.class)).isNotNull();
        assertThat(annotationOn("deleted", org.hibernate.annotations.SoftDelete.class) != null
                || AbstractAuditableEntity.class.isAnnotationPresent(org.hibernate.annotations.SQLRestriction.class))
                .as("soft delete via @SQLRestriction on base class")
                .isTrue();
        assertThat(annotationOn("tenantId", org.hibernate.annotations.TenantId.class))
                .as("tenantId only on AbstractTenantEntity")
                .isNull(); // 本类无租户字段
        assertThat(annotationOnAbstractTenant("tenantId", org.hibernate.annotations.TenantId.class)).isNotNull();
    }

    @Test
    void prePersistAssignsUuidV7WhenIdMissing() {
        TestEntity entity = new TestEntity();
        entity.assignId();
        assertThat(entity.getId()).contains("-");
        assertThat(UUID.fromString(entity.getId()).version()).isEqualTo(7); // UUID v7 version nibble
    }

    @Test
    void resolverMapsIgnoreAndEmptyToPlatformTenant() {
        var ignoring = supplierWith(Optional.empty(), true);
        var resolver = new ForgeTenantIdentifierResolver(ignoring);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("0");
        assertThat(resolver.validateExistingCurrentSessions()).isFalse();

        var empty = supplierWith(Optional.empty(), false);
        assertThat(new ForgeTenantIdentifierResolver(empty).resolveCurrentTenantIdentifier()).isEqualTo("0");

        var tenanted = supplierWith(Optional.of("t-9"), false);
        assertThat(new ForgeTenantIdentifierResolver(tenanted).resolveCurrentTenantIdentifier()).isEqualTo("t-9");
    }

    private Object annotationOn(String field, Class<? extends java.lang.annotation.Annotation> type)
            throws NoSuchFieldException {
        try {
            return AbstractAuditableEntity.class.getDeclaredField(field).getAnnotation(type);
        } catch (NoSuchFieldException e) {
            return null; // 字段不存在 = 必无该注解（tenantId 不在基类）
        }
    }

    private Object annotationOnAbstractTenant(String field, Class<? extends java.lang.annotation.Annotation> type)
            throws NoSuchFieldException {
        return AbstractTenantEntity.class.getDeclaredField(field).getAnnotation(type);
    }

    private io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier supplierWith(
            Optional<String> tenantId, boolean ignore) {
        return new io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return tenantId; }
            @Override public boolean ignore() { return ignore; }
        };
    }

    static class TestEntity extends AbstractAuditableEntity {}
}
