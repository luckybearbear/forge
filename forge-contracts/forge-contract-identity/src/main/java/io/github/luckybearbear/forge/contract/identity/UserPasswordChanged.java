package io.github.luckybearbear.forge.contract.identity;

import java.time.Instant;

/** 用户密码变更事件：service-identity 发布，token/权限缓存侧据此失效既有会话。 */
public record UserPasswordChanged(String tenantId, String userId, Instant occurredAt) {}
