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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户挂岗应用服务：assign（挂岗）/unassign（摘岗）/findByUserId。
 *
 * <p><b>跨服务契约校验</b>：userId 存在性经 contract-identity {@link UserQueryService}
 * （实现方为 service-identity；本模块不依赖任何 service）——不存在抛 IDENT-0404/404；
 * positionId 本域校验 ORG-0414/404。查询走本租户判别式：挂岗/查岗均在调用方租户上下文内。
 *
 * <p><b>幂等语义</b>：重复挂岗（同 (tenant, user, position)）返回既有记录不重复插
 * （service 先查后插 + partial unique index 兜底并发）；摘岗不存在时静默成功（软删行不占键，
 * 摘后可重挂）。
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

    /** 挂岗（幂等）：用户缺失 IDENT-0404/404（跨服务契约）；岗位缺失 ORG-0414/404（本域）。 */
    @Transactional
    public UserPositionDTO assign(String userId, String positionId) {
        userQueryService.findById(userId)
                .orElseThrow(() -> BusinessException.of(IdentityErrorCodes.IDENT_USER_NOT_FOUND, userId)
                        .withHttpStatus(404));
        positionRepository.findById(positionId)
                .orElseThrow(() -> BusinessException.of(OrganizationErrorCodes.ORG_POSITION_NOT_FOUND, positionId)
                        .withHttpStatus(404));
        return repository.findByUserIdAndPositionId(userId, positionId)
                .map(this::toDto) // 幂等：重复挂岗返回既有记录
                .orElseGet(() -> toDto(repository.save(new UserPositionEntity(userId, positionId))));
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
