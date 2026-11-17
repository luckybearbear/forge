package io.github.luckybearbear.forge.contract.tenant;

import java.time.Instant;

/** 租户快照 DTO，跨模块同步查询用。 */
public record TenantDTO(String id, String code, String name, String packageId, String status, Instant expiredAt) {}
