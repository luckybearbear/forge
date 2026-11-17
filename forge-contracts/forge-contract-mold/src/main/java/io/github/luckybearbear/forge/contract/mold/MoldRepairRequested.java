package io.github.luckybearbear.forge.contract.mold;

import java.time.Instant;

/**
 * 模具报修事件：mold 模块发布，maintenance 模块监听。
 *
 * <p>Phase 2a 契约变更（breaking）：新增 {@code tenantId} 组件——异步监听线程不继承 TTL 租户上下文，
 * 发布方显式携带当前租户，监听器入口据此恢复上下文（spec §6.1）。Phase 1 消费方仅 demo 两模块，同步改。
 */
public record MoldRepairRequested(String tenantId, String moldId, String description, Instant occurredAt) {}
