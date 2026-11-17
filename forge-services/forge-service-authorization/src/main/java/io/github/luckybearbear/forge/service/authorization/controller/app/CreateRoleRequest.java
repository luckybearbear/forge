package io.github.luckybearbear.forge.service.authorization.controller.app;

import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull DataScope dataScope) {}
