package io.github.luckybearbear.forge.platform.data;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractAuditableEntityTest {

    @Test
    void auditFieldsCarrySpringDataAnnotations() throws Exception {
        Map<String, Class<? extends Annotation>> expected = Map.of(
                "id", Id.class,
                "version", Version.class,
                "createdBy", CreatedBy.class,
                "createdAt", CreatedDate.class,
                "updatedBy", LastModifiedBy.class,
                "updatedAt", LastModifiedDate.class);
        for (Map.Entry<String, Class<? extends Annotation>> entry : expected.entrySet()) {
            String fieldName = entry.getKey();
            Class<? extends Annotation> annotation = entry.getValue();
            Field field = AbstractAuditableEntity.class.getDeclaredField(fieldName);
            assertThat(field.getAnnotation(annotation))
                    .as("field %s should carry @%s", fieldName, annotation.getSimpleName())
                    .isNotNull();
        }
    }
}
