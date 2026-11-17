package io.github.luckybearbear.forge.service.tenant;

import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.service.tenant.domain.TenantEntity;
import io.github.luckybearbear.forge.service.tenant.domain.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 契约 {@link TenantQueryService} 实现：跨模块同步查询入口（identity 登录侧校验租户状态等）。
 * 栖身模块根包（随 TenantServiceAutoConfiguration 的 @ComponentScan 暴露为契约 bean）。
 */
@Service
public class TenantQueryServiceImpl implements TenantQueryService {

    private final TenantRepository repository;

    public TenantQueryServiceImpl(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantDTO> findById(String tenantId) {
        return repository.findById(tenantId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantDTO> findByCode(String code) {
        return repository.findByCode(code).map(this::toDto);
    }

    private TenantDTO toDto(TenantEntity entity) {
        return new TenantDTO(entity.getId(), entity.getCode(), entity.getName(),
                entity.getPackageId(), entity.getStatus().name(), entity.getExpiredAt());
    }
}
