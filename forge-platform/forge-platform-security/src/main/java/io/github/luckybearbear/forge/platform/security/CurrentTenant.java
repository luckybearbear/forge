package io.github.luckybearbear.forge.platform.security;

/** 当前租户快照。Phase 2 多租户实施时评估 TransmittableThreadLocal / ScopedValue 替换。 */
public record CurrentTenant(String tenantId) {}
