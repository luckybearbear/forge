package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 异步事务提交后监听（Modulith 默认）。无持久化 Event Publication Registry 时事件为内存投递
 * （spec §10：registry 持久化 Phase 2 随 PostgreSQL 启用）。
 */
@Component
public class MoldRepairRequestedListener {

    private final MaintenanceService maintenanceService;

    public MoldRepairRequestedListener(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @ApplicationModuleListener
    public void on(MoldRepairRequested event) {
        maintenanceService.createFromEvent(event.moldId(), event.description(), event.occurredAt());
    }
}
