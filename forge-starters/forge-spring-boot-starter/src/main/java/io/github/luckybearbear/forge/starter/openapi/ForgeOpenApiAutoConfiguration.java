package io.github.luckybearbear.forge.starter.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(ForgeOpenApiProperties.class)
public class ForgeOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI forgeOpenApi(ForgeOpenApiProperties properties) {
        return new OpenAPI().info(new Info()
                .title(properties.title())
                .description(properties.description())
                .version(properties.version()));
    }
}
