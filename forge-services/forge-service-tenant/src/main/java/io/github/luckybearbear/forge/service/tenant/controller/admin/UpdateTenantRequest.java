package io.github.luckybearbear.forge.service.tenant.controller.admin;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 租户更新请求（管理端）：全部字段可选——null 即不改（部分更新语义，README）。 */
public record UpdateTenantRequest(
        @Size(max = 128) String name,
        @Size(max = 36) String packageId,
        Instant expiredAt) {}
