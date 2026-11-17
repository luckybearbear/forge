package io.github.luckybearbear.forge.service.tenant.app;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.service.tenant.domain.TenantPackageEntity;
import io.github.luckybearbear.forge.service.tenant.domain.TenantPackageRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 套餐应用服务：create/findByCode/findById/update/delete（软删，编码随之可复用）。
 *
 * <p>错误码约定：套餐无专属码段（FOREIGN_CODES 纪律——本任务不新增契约码）——未命中用平台
 * FORGE-0404，编码重复用平台冲突码 FORGE-0423 + 具体消息、HTTP 409（README 登记）。
 */
@Service
public class TenantPackageService {

    private final TenantPackageRepository repository;

    public TenantPackageService(TenantPackageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TenantPackageDTO create(String code, String name, Integer maxUsers, String remark) {
        repository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException(ErrorCodes.CONCURRENT_LOCKED, "套餐编码已存在: " + code).withHttpStatus(409);
        });
        TenantPackageEntity saved = repository.save(new TenantPackageEntity(code, name, maxUsers, remark));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TenantPackageDTO findById(String id) {
        return toDto(requirePackage(id));
    }

    @Transactional(readOnly = true)
    public Optional<TenantPackageDTO> findByCode(String code) {
        return repository.findByCode(code).map(this::toDto);
    }

    /** 按编码查询（缺失→FORGE-0404/404）：对外（控制器）用——缺失语义在 service 层，控制器只做 Result 包装。 */
    @Transactional(readOnly = true)
    public TenantPackageDTO findByCodeRequired(String code) {
        return repository.findByCode(code).map(this::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "套餐不存在: " + code).withHttpStatus(404));
    }

    /** 部分更新：name/maxUsers/remark 传 null 即不改该字段。 */
    @Transactional
    public TenantPackageDTO update(String id, String name, Integer maxUsers, String remark) {
        TenantPackageEntity entity = requirePackage(id);
        if (name != null) {
            entity.setName(name);
        }
        if (maxUsers != null) {
            entity.setMaxUsers(maxUsers);
        }
        if (remark != null) {
            entity.setRemark(remark);
        }
        return toDto(entity);
    }

    /** 软删除（Hibernate 类级 @SoftDelete → UPDATE deleted=true）：行留存、编码可复用。 */
    @Transactional
    public void delete(String id) {
        requirePackage(id);
        repository.deleteById(id);
    }

    private TenantPackageEntity requirePackage(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "套餐不存在: " + id).withHttpStatus(404));
    }

    private TenantPackageDTO toDto(TenantPackageEntity entity) {
        return new TenantPackageDTO(entity.getId(), entity.getCode(), entity.getName(),
                entity.getMaxUsers(), entity.getRemark());
    }
}
