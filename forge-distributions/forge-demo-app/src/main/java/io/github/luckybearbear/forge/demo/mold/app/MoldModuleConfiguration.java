package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MoldModuleConfiguration {

    @Bean
    public MoldRegistry moldRegistry() {
        return new MoldRegistry();
    }

    @Bean
    public IdGenerator idGenerator() {
        return new UuidV7Generator();
    }
}
