package io.github.luckybearbear.forge.contract.organization;

import java.time.Instant;

/** 组织单元变更事件（changeType 区分创建/更新/删除）：service-organization 发布，消费方据此失效组织快照。 */
public record OrgUnitChanged(String tenantId, String orgUnitId, String changeType, Instant occurredAt) {}
