package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 事件监听：消费 mold 模块发布的 MoldRepairRequested（契约事件，模块间唯一合法通道）。
 * Task 11 连库后恢复 @ApplicationModuleListener（Phase 1 因无持久化登记表降级同步 @EventListener）：
 * 事务提交后异步投递 + event_publication 持久化登记 + 失败重投（spec §10）。
 * 异步线程不继承 TTL 上下文：入口以 payload 携带的租户（契约变更新增组件）恢复上下文，finally 清理（spec §6.1）。
 */
@Component
public class MoldRepairRequestedListener {

    private final MaintenanceService maintenanceService;

    public MoldRepairRequestedListener(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @ApplicationModuleListener
    public void on(MoldRepairRequested event) {
        TenantContextHolder.setTenantId(event.tenantId());
        try {
            maintenanceService.createFromEvent(event.moldId(), event.description());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
