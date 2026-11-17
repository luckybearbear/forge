package io.github.luckybearbear.forge.contract.authorization;

import java.time.Instant;

/** 角色权限变更事件：service-authorization 发布，权限缓存侧据此失效该角色的授权快照。 */
public record RolePermissionsChanged(String tenantId, String roleId, Instant occurredAt) {}
