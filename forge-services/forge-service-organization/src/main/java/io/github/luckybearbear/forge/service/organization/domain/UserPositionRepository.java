package io.github.luckybearbear.forge.service.organization.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用户挂岗仓库（租户表：判别式过滤总生效；软删行自动排除——摘岗后同组合可重挂）。 */
public interface UserPositionRepository extends JpaRepository<UserPositionEntity, String> {

    Optional<UserPositionEntity> findByUserIdAndPositionId(String userId, String positionId);

    List<UserPositionEntity> findByUserId(String userId);

    /** 岗位删除保护：该岗位是否存在挂岗。 */
    boolean existsByPositionId(String positionId);
}
