package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldErrorCodes;
import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldEntity;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MoldService {

    private final MoldRegistry registry;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public MoldService(MoldRegistry registry, IdGenerator idGenerator,
                       ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    public MoldDTO register(RegisterMoldRequest request) {
        String id = idGenerator.nextId();
        registry.save(new MoldEntity(id, request.name(), request.specification(),
                MoldStatus.IN_SERVICE, Instant.now()));
        return toDto(registry.find(id).orElseThrow());
    }

    public MoldDTO findById(String id) {
        return registry.find(id).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(MoldErrorCodes.MOLD_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    public void requestRepair(String moldId, RequestRepairRequest request) {
        findById(moldId); // 确保存在，不存在则抛 MOLD-0404
        eventPublisher.publishEvent(new MoldRepairRequested(moldId, request.description(), Instant.now()));
    }

    private MoldDTO toDto(MoldEntity entity) {
        return new MoldDTO(entity.id(), entity.name(), entity.specification(), entity.status());
    }
}
