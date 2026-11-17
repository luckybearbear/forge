package io.github.luckybearbear.forge.service.organization.app;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.contract.organization.OrganizationErrorCodes;
import io.github.luckybearbear.forge.service.organization.domain.OrgUnitRepository;
import io.github.luckybearbear.forge.service.organization.domain.PositionEntity;
import io.github.luckybearbear.forge.service.organization.domain.PositionRepository;
import io.github.luckybearbear.forge.service.organization.domain.UserPositionRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位应用服务：create/findById/findByOrgUnitId/update/delete。
 *
 * <p>挂靠单元存在性校验（create/update 换挂靠时）→ ORG-0404；缺失语义 ORG-0414；
 * 删除保护：存在挂岗（user_position 引用）即拒绝——ORG-0409 码 + 文案"岗位存在挂岗，无法删除"
 * （FOREIGN_CODES 纪律：不新增枚举）。部分更新：orgUnitId/name/sort 传 null 不改。
 */
@Service
public class PositionService {

    private static final Comparator<PositionEntity> ORDER =
            Comparator.comparingInt(PositionEntity::getSort).thenComparing(PositionEntity::getId);

    private final PositionRepository repository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserPositionRepository userPositionRepository;

    public PositionService(PositionRepository repository, OrgUnitRepository orgUnitRepository,
            UserPositionRepository userPositionRepository) {
        this.repository = repository;
        this.orgUnitRepository = orgUnitRepository;
        this.userPositionRepository = userPositionRepository;
    }

    /** 创建岗位（挂靠单元须在本租户存在 → ORG-0404）。 */
    @Transactional
    public PositionDTO create(String orgUnitId, String name, int sort) {
        requireOrgUnit(orgUnitId);
        PositionEntity saved = repository.save(new PositionEntity(orgUnitId, name, sort));
        return toDto(saved);
    }

    /** 缺失 → ORG-0414/404（缺失语义在 service 层，控制器薄层）。 */
    @Transactional(readOnly = true)
    public PositionDTO findById(String id) {
        return toDto(requirePosition(id));
    }

    @Transactional(readOnly = true)
    public List<PositionDTO> findByOrgUnitId(String orgUnitId) {
        return repository.findByOrgUnitId(orgUnitId).stream().sorted(ORDER).map(this::toDto).toList();
    }

    /** 部分更新：orgUnitId（换挂靠，须存在）/name/sort 传 null 即不改。 */
    @Transactional
    public PositionDTO update(String id, String orgUnitId, String name, Integer sort) {
        PositionEntity entity = requirePosition(id);
        if (orgUnitId != null) {
            requireOrgUnit(orgUnitId);
            entity.setOrgUnitId(orgUnitId);
        }
        if (name != null) {
            entity.setName(name);
        }
        if (sort != null) {
            entity.setSort(sort);
        }
        return toDto(entity); // 托管态脏检查随事务提交落库
    }

    /** 删除（软删）：存在挂岗拒绝（ORG-0409 码 + 特定文案）。 */
    @Transactional
    public void delete(String id) {
        PositionEntity entity = requirePosition(id);
        if (userPositionRepository.existsByPositionId(id)) {
            throw new BusinessException(OrganizationErrorCodes.ORG_CYCLE_DETECTED,
                    "岗位存在挂岗，无法删除: " + id).withHttpStatus(409);
        }
        repository.delete(entity);
    }

    private void requireOrgUnit(String orgUnitId) {
        orgUnitRepository.findById(orgUnitId)
                .orElseThrow(() -> BusinessException.of(OrganizationErrorCodes.ORG_UNIT_NOT_FOUND, orgUnitId)
                        .withHttpStatus(404));
    }

    private PositionEntity requirePosition(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.of(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    private PositionDTO toDto(PositionEntity entity) {
        return new PositionDTO(entity.getId(), entity.getOrgUnitId(), entity.getName(), entity.getSort());
    }
}
