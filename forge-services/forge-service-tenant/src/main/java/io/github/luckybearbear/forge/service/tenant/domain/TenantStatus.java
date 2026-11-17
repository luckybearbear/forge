package io.github.luckybearbear.forge.service.tenant.domain;

/**
 * 租户生命周期状态机：创建即 {@code INIT}（未开通）；{@code enable} → {@code ENABLED}；
 * {@code disable} → {@code DISABLED}（幂等，可再 {@code enable} 复通）。
 * 契约 {@code TenantDTO.status} 以枚举名（String）传输。
 */
public enum TenantStatus {
    INIT, ENABLED, DISABLED
}
