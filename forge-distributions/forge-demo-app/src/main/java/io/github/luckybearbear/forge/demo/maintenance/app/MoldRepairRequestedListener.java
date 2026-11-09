package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 事件监听：消费 mold 模块发布的 MoldRepairRequested（契约事件，模块间唯一合法通道）。
 * brief Step 3 降级：@ApplicationModuleListener（异步事务提交后投递）依赖 spring-modulith-events-core
 * 的 EventPublicationRegistry 持久化存储（JPA/JDBC/Mongo），Phase 1 无 DB 故不可用（上下文启动失败）；
 * 当前用同步 @EventListener，验证流程不受影响，Modulith verify() 边界检查不受影响。
 * Phase 2 随 PostgreSQL + 事件持久化恢复 @ApplicationModuleListener（spec §10）。
 */
@Component
public class MoldRepairRequestedListener {

    private final MaintenanceService maintenanceService;

    public MoldRepairRequestedListener(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @EventListener
    public void on(MoldRepairRequested event) {
        maintenanceService.createFromEvent(event.moldId(), event.description(), event.occurredAt());
    }
}
