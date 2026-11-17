package io.github.luckybearbear.forge.service.organization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 岗位仓库（租户表：判别式过滤总生效；软删行自动排除）。 */
public interface PositionRepository extends JpaRepository<PositionEntity, String> {

    List<PositionEntity> findByOrgUnitId(String orgUnitId);

    /** 组织单元删除保护：该单元下是否存在岗位。 */
    boolean existsByOrgUnitId(String orgUnitId);
}
