package io.github.luckybearbear.forge.demo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithBoundaryTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(ForgeDemoApplication.class);

    @Test
    void moduleBoundariesAreSound() {
        MODULES.verify();
    }

    @Test
    void containsExactlyMoldAndMaintenance() {
        // Modulith 2.x removed ApplicationModule.getName(): identifier is the module's local name.
        assertThat(MODULES.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("mold", "maintenance");
    }
}
