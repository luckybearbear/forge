package io.github.luckybearbear.forge.contract.identity;

import java.time.Instant;

/**
 * 用户创建事件：service-identity 发布。
 *
 * <p>异步监听线程不继承 TTL 租户上下文，发布方显式携带 {@code tenantId}（同 MoldRepairRequested 约定，spec §6.1）。
 */
public record UserCreated(String tenantId, String userId, String username, Instant occurredAt) {}
