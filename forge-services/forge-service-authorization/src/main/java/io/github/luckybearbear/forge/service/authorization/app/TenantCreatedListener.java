package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.tenant.TenantCreated;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 事件监听：消费 tenant 域的 {@link TenantCreated}（契约事件）→ 为新租户初始化默认 ADMIN 角色 +
 * 全量权限点授权。租户开通即有可用管理员角色，首个用户经 {@code PUT /app-api/users/{userId}/roles}
 * 或 demo seeder 绑定（简裁定：监听器不绑定创建人——TenantCreated 不携带 userId）。
 *
 * <p>@ApplicationModuleListener：事务提交后异步投递 + event_publication 持久化登记 + 失败重投
 * （spec §10）。异步线程不继承 TTL 上下文：auth_role/auth_role_permission 为<b>租户表</b>，
 * 监听器显式 setTenantId(payload.tenantId()) → 写 → finally clear（demo MoldRepairRequestedListener
 * 范式）；落库在 {@link TenantInitializationService} 的 REQUIRES_NEW 事务内。
 */
@Component
public class TenantCreatedListener {

    private final TenantInitializationService initializationService;

    public TenantCreatedListener(TenantInitializationService initializationService) {
        this.initializationService = initializationService;
    }

    @ApplicationModuleListener
    public void on(TenantCreated event) {
        TenantContextHolder.setTenantId(event.tenantId());
        try {
            initializationService.initializeDefaultAuthorization();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
