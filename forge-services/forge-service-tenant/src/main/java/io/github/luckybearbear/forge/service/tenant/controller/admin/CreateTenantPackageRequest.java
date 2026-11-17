package io.github.luckybearbear.forge.service.tenant.controller.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 套餐创建请求（管理端）。maxUsers 可选、为正数（人数上限）。 */
public record CreateTenantPackageRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Positive Integer maxUsers,
        @Size(max = 256) String remark) {}
