package io.github.luckybearbear.forge.demo.mold.api;

import jakarta.validation.constraints.NotBlank;

public record RegisterMoldRequest(@NotBlank String name, String specification) {}
