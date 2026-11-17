package io.github.luckybearbear.forge.service.tenant.controller.admin;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 套餐更新请求（管理端）：全部字段可选——null 即不改（部分更新语义，README）。 */
public record UpdateTenantPackageRequest(
        @Size(max = 128) String name,
        @Positive Integer maxUsers,
        @Size(max = 256) String remark) {}
