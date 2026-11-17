package io.github.luckybearbear.forge.service.tenant.app;

import io.github.luckybearbear.forge.contract.tenant.TenantCreated;
import io.github.luckybearbear.forge.contract.tenant.TenantDisabled;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.service.tenant.domain.TenantEntity;
import io.github.luckybearbear.forge.service.tenant.domain.TenantRepository;
import io.github.luckybearbear.forge.service.tenant.domain.TenantStatus;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户应用服务：create/findByCode/findById/update/disable/enable。
 *
 * <p>事件发布经 {@link ApplicationEventPublisher} 且在业务事务内（spec §10：event_publication
 * 登记随业务事务原子提交）。tenant 域为全局表（无租户上下文），事件 payload 的 tenantId 是
 * <b>事件主体租户</b>而非 TTL 上下文回填字段（契约 javadoc 约定）。
 *
 * <p>语义约定（README 同步登记）：创建即 INIT（enable 显式开通）；disable 幂等（已 DISABLED 返回
 * 当前状态、不重复发事件）；enable 幂等（任意状态→ENABLED）；update 为部分更新（null 字段不改）。
 */
@Service
public class TenantService {

    private final TenantRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public TenantService(TenantRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /** 创建租户（编码唯一校验→TENANT-0409）并发布 TenantCreated；初始状态 INIT。 */
    @Transactional
    public TenantDTO create(String code, String name, String packageId, Instant expiredAt) {
        repository.findByCode(code).ifPresent(existing -> {
            throw BusinessException.of(TenantErrorCodes.TENANT_CODE_DUPLICATED, code).withHttpStatus(409);
        });
        TenantEntity saved = repository.save(new TenantEntity(code, name, packageId, TenantStatus.INIT, expiredAt));
        eventPublisher.publishEvent(
                new TenantCreated(saved.getId(), saved.getCode(), saved.getName(), Instant.now()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TenantDTO findById(String id) {
        return toDto(requireTenant(id));
    }

    @Transactional(readOnly = true)
    public TenantDTO findByCode(String code) {
        return repository.findByCode(code).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(TenantErrorCodes.TENANT_NOT_FOUND, code).withHttpStatus(404));
    }

    /** 部分更新：name/packageId/expiredAt 传 null 即不改该字段。 */
    @Transactional
    public TenantDTO update(String id, String name, String packageId, Instant expiredAt) {
        TenantEntity entity = requireTenant(id);
        if (name != null) {
            entity.setName(name);
        }
        if (packageId != null) {
            entity.setPackageId(packageId);
        }
        if (expiredAt != null) {
            entity.setExpiredAt(expiredAt);
        }
        return toDto(entity); // 托管态脏检查随事务提交落库
    }

    /** 停用（幂等）：INIT/ENABLED → DISABLED 并发布 TenantDisabled；已 DISABLED 返回当前状态、不重复发事件。 */
    @Transactional
    public TenantDTO disable(String id) {
        TenantEntity entity = requireTenant(id);
        if (entity.getStatus() == TenantStatus.DISABLED) {
            return toDto(entity); // 幂等：不重复发事件（identity 侧按事件触发清理，重复会放大）
        }
        entity.setStatus(TenantStatus.DISABLED);
        eventPublisher.publishEvent(new TenantDisabled(entity.getId(), Instant.now()));
        return toDto(entity);
    }

    /** 启用（幂等）：任意状态 → ENABLED（INIT 直接开通；已 ENABLED 无副作用）。 */
    @Transactional
    public TenantDTO enable(String id) {
        TenantEntity entity = requireTenant(id);
        entity.setStatus(TenantStatus.ENABLED);
        return toDto(entity);
    }

    private TenantEntity requireTenant(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.of(TenantErrorCodes.TENANT_NOT_FOUND, id).withHttpStatus(404));
    }

    private TenantDTO toDto(TenantEntity entity) {
        return new TenantDTO(entity.getId(), entity.getCode(), entity.getName(),
                entity.getPackageId(), entity.getStatus().name(), entity.getExpiredAt());
    }
}
