package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceModuleConfiguration {

    @Bean
    public MaintenanceRegistry maintenanceRegistry() {
        return new MaintenanceRegistry();
    }
}
