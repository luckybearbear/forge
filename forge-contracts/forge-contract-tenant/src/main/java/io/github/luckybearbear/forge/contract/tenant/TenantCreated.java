package io.github.luckybearbear.forge.contract.tenant;

import java.time.Instant;

/**
 * 租户创建事件：service-tenant 发布，authorization 侧监听初始化默认角色。
 *
 * <p>tenant 域为全局表（无租户上下文），{@code tenantId} 是事件主体租户而非 TTL 上下文回填字段。
 */
public record TenantCreated(String tenantId, String code, String tenantName, Instant occurredAt) {}
