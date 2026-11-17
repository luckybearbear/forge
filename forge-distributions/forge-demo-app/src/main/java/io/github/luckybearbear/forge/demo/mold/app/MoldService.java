package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldErrorCodes;
import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldJpaEntity;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRepository;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoldService {

    private final MoldRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public MoldService(MoldRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public MoldDTO register(RegisterMoldRequest request) {
        var saved = repository.save(new MoldJpaEntity(request.name(), request.specification(),
                MoldStatus.IN_SERVICE));
        return toDto(saved);
    }

    public MoldDTO findById(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(MoldErrorCodes.MOLD_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    /**
     * @Transactional 关键（spec §10）：事件发布须在事务内——event_publication 登记随业务事务原子提交，
     * @ApplicationModuleListener 的 AFTER_COMMIT 异步投递才有事务边界可挂靠。
     * 事件显式携带当前租户：异步监听线程不继承 TTL 上下文，由 payload 恢复（spec §6.1）。
     */
    @Transactional
    public void requestRepair(String moldId, RequestRepairRequest request) {
        findById(moldId); // 确保存在（当前租户视角），不存在则抛 MOLD-0404
        String tenantId = TenantContextHolder.getRequiredTenantId();
        eventPublisher.publishEvent(new MoldRepairRequested(tenantId, moldId,
                request.description(), Instant.now()));
    }

    private MoldDTO toDto(MoldJpaEntity entity) {
        return new MoldDTO(entity.getId(), entity.getName(), entity.getSpecification(),
                entity.getStatus());
    }
}
