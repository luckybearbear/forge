package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.demo.maintenance.api.MaintenanceOrderDTO;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderJpaEntity;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrderRepository;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private final MaintenanceOrderRepository repository;
    private final MoldQueryService moldQueryService;

    public MaintenanceService(MaintenanceOrderRepository repository,
                              MoldQueryService moldQueryService) {
        this.repository = repository;
        this.moldQueryService = moldQueryService;
    }

    /**
     * 事件消费入口（监听线程，租户上下文已由 listener 从 payload 恢复）。
     * REQUIRES_NEW 实证必要：@ApplicationModuleListener 的 @Transactional(REQUIRES_NEW) 在方法体
     * 执行前即开启 Hibernate 会话——判别式多租户在会话开启时解析租户（早于 listener 恢复上下文），
     * 会话被绑到平台租户 "0"；本方法自开新事务/新会话时租户已恢复，落库归属正确租户。
     * 
     * 经契约同步查询补全模具名快照；createdAt 由 JPA 审计在落库时填充
     * （原内存版取 event.occurredAt，连库后语义并入 created_at 审计列，见任务报告取舍记录）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaintenanceOrderDTO createFromEvent(String moldId, String description) {
        var mold = moldQueryService.findById(moldId); // 契约同步查询：事件只携带 id，消费方补全当前状态
        var order = new MaintenanceOrderJpaEntity(moldId, mold.name(), description, "OPEN");
        return toDto(repository.save(order));
    }

    public MaintenanceOrderDTO findById(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(MaintenanceErrorCodes.MAINT_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    public List<MaintenanceOrderDTO> findByMoldId(String moldId) {
        return repository.findByMoldId(moldId).stream().map(this::toDto).toList();
    }

    private MaintenanceOrderDTO toDto(MaintenanceOrderJpaEntity order) {
        return new MaintenanceOrderDTO(order.getId(), order.getMoldId(), order.getMoldName(),
                order.getDescription(), order.getStatus(), order.getCreatedAt());
    }
}
