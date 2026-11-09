package io.github.luckybearbear.forge.platform.audit;

import java.time.Instant;

/** 操作审计条目（操作人/动作/对象/时间）。 */
public record AuditEntry(String actor, String action, String targetType, String targetId, Instant at) {}
