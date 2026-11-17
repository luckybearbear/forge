package io.github.luckybearbear.forge.service.organization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 组织单元仓库（租户表：@TenantId 判别式过滤总生效；软删行由 Hibernate 类级 @SoftDelete 自动排除）。 */
public interface OrgUnitRepository extends JpaRepository<OrgUnitEntity, String> {

    /** 直接子节点（subTree 逐层下钻用；tree() 全林走 findAll 内存建图，环检测上溯走 findById）。 */
    List<OrgUnitEntity> findByParentId(String parentId);

    /** 删除保护：是否存在子节点。 */
    boolean existsByParentId(String parentId);
}
