package io.github.luckybearbear.forge.demo.mold.api;

import jakarta.validation.constraints.NotBlank;

public record RequestRepairRequest(@NotBlank String description) {}
