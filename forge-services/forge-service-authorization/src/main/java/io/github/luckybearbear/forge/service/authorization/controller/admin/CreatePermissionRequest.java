package io.github.luckybearbear.forge.service.authorization.controller.admin;

import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePermissionRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull PermissionType type) {}
