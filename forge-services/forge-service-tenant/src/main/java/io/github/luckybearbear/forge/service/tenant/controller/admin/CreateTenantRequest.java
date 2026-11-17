package io.github.luckybearbear.forge.service.tenant.controller.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 租户创建请求（管理端）。packageId/expiredAt 可选（后置 update 绑定/设定）。 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 36) String packageId,
        Instant expiredAt) {}
