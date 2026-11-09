package io.github.luckybearbear.forge.starter.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "forge.openapi")
public record ForgeOpenApiProperties(
        @DefaultValue("Forge Application") String title,
        @DefaultValue("") String description,
        @DefaultValue("1.0.0") String version) {
}
