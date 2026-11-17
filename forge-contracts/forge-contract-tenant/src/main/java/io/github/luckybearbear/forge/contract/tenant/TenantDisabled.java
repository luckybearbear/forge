package io.github.luckybearbear.forge.contract.tenant;

import java.time.Instant;

/** 租户停用事件：service-tenant 发布，identity 登录等侧据此拦截已停用租户（TENANT-0423）。 */
public record TenantDisabled(String tenantId, Instant occurredAt) {}
