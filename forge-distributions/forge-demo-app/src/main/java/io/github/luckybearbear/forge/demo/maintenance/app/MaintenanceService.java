package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.demo.maintenance.api.MaintenanceOrderDTO;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrder;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceService {

    private final MaintenanceRegistry registry;
    private final IdGenerator idGenerator;
    private final MoldQueryService moldQueryService;

    public MaintenanceService(MaintenanceRegistry registry, IdGenerator idGenerator,
                              MoldQueryService moldQueryService) {
        this.registry = registry;
        this.idGenerator = idGenerator;
        this.moldQueryService = moldQueryService;
    }

    public MaintenanceOrderDTO createFromEvent(String moldId, String description, Instant occurredAt) {
        var mold = moldQueryService.findById(moldId); // 契约同步查询：事件只携带 id，消费方补全当前状态
        var order = new MaintenanceOrder(idGenerator.nextId(), moldId, mold.name(),
                description, "OPEN", occurredAt);
        registry.save(order);
        return toDto(order);
    }

    public MaintenanceOrderDTO findById(String id) {
        return registry.find(id).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(MaintenanceErrorCodes.MAINT_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    public List<MaintenanceOrderDTO> findByMoldId(String moldId) {
        return registry.findByMoldId(moldId).stream().map(this::toDto).toList();
    }

    private MaintenanceOrderDTO toDto(MaintenanceOrder order) {
        return new MaintenanceOrderDTO(order.id(), order.moldId(), order.moldName(),
                order.description(), order.status(), order.createdAt());
    }
}
