package io.github.luckybearbear.forge.service.authorization.controller.app;

import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import jakarta.validation.constraints.NotNull;

public record UpdateDataScopeRequest(@NotNull DataScope dataScope) {}
