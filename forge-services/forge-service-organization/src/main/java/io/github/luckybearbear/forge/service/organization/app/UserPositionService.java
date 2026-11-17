package io.github.luckybearbear.forge.service.organization.app;

import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.contract.identity.UserQueryService;
import io.github.luckybearbear.forge.contract.organization.OrganizationErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.service.organization.domain.PositionRepository;
import io.github.luckybearbear.forge.service.organization.domain.UserPositionEntity;
import io.github.luckybearbear.forge.service.organization.domain.UserPositionRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户挂岗应用服务：assign（挂岗）/unassign（摘岗）/findByUserId。
 *
 * <p><b>跨服务契约校验</b>：userId 存在性经 contract-identity {@link UserQueryService}
 * （实现方为 service-identity；本模块不依赖任何 service）——不存在抛 IDENT-0404/404；
 * positionId 本域校验 ORG-0414/404。查询走本租户判别式：挂岗/查岗均在调用方租户上下文内。
 *
 * <p><b>幂等语义</b>：顺序幂等——重复挂岗（同 (tenant, user, position)）先查后插返回既有记录；
 * <b>并发幂等</b>——竞争窗口漏检时 insert 撞 partial unique index，捕获
 * {@link DataIntegrityViolationException} 后重查返回既有行（真幂等，见 {@link #assign} 的事务
 * 形态说明）；摘岗不存在时静默成功（软删行不占键，摘后可重挂）。
 */
@Service
public class UserPositionService {

    private static final Comparator<UserPositionEntity> ORDER =
            Comparator.comparing(UserPositionEntity::getPositionId).thenComparing(UserPositionEntity::getId);

    private final UserPositionRepository repository;
    private final PositionRepository positionRepository;
    private final UserQueryService userQueryService;

    public UserPositionService(UserPositionRepository repository, PositionRepository positionRepository,
            UserQueryService userQueryService) {
        this.repository = repository;
        this.positionRepository = positionRepository;
        this.userQueryService = userQueryService;
    }

    /**
     * 挂岗（幂等）：用户缺失 IDENT-0404/404（跨服务契约）；岗位缺失 ORG-0414/404（本域）。
     *
     * <p><b>故意不挂外层 @Transactional</b>：并发兜底要求在本层捕获 {@code saveAndFlush} 的
     * 唯一键冲突后继续重查——若存在外层事务，冲突异常经仓库代理会把参与事务标记 rollback-only，
     * 随后的提交翻成 UnexpectedRollbackException。各仓库调用自带短事务
     * （SimpleJpaRepository 类级 @Transactional）：冲突只回滚 insert 那一调，重查开新读事务
     * 即见竞争赢家已提交行。本方法只写单行、无跨调用一致性需求，拆事务无损。
     */
    public UserPositionDTO assign(String userId, String positionId) {
        userQueryService.findById(userId)
                .orElseThrow(() -> BusinessException.of(IdentityErrorCodes.IDENT_USER_NOT_FOUND, userId)
                        .withHttpStatus(404));
        positionRepository.findById(positionId)
                .orElseThrow(() -> BusinessException.of(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND, positionId)
                        .withHttpStatus(404));
        return repository.findByUserIdAndPositionId(userId, positionId)
                .map(this::toDto) // 顺序幂等：重复挂岗返回既有记录
                .orElseGet(() -> {
                    try {
                        return toDto(repository.saveAndFlush(new UserPositionEntity(userId, positionId)));
                    } catch (DataIntegrityViolationException race) {
                        // 并发幂等：另一事务已落同 (tenant, user, position)（partial unique 冲突）——重查返回既有行
                        return repository.findByUserIdAndPositionId(userId, positionId)
                                .map(this::toDto)
                                .orElseThrow(() -> race); // 行仍不可见（如未提交即消失）：如实上抛原始冲突
                    }
                });
    }

    /** 按用户查挂岗（本租户判别式内）。 */
    @Transactional(readOnly = true)
    public List<UserPositionDTO> findByUserId(String userId) {
        return repository.findByUserId(userId).stream().sorted(ORDER).map(this::toDto).toList();
    }

    /** 摘岗（幂等）：不存在时静默成功（软删——行留存、(tenant, user, position) 键释放可重挂）。 */
    @Transactional
    public void unassign(String userId, String positionId) {
        repository.findByUserIdAndPositionId(userId, positionId).ifPresent(repository::delete);
    }

    private UserPositionDTO toDto(UserPositionEntity entity) {
        return new UserPositionDTO(entity.getId(), entity.getUserId(), entity.getPositionId());
    }
}
