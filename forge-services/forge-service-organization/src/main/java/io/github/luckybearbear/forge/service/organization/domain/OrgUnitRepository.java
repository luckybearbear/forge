package io.github.luckybearbear.forge.service.organization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 组织单元仓库（租户表：@TenantId 判别式过滤总生效；软删行由 Hibernate 类级 @SoftDelete 自动排除）。 */
public interface OrgUnitRepository extends JpaRepository<OrgUnitEntity, String> {

    /** 直接子节点（环检测上溯/subTree 递归不走此路——subTree 走 findAll 内存建图避免 N+1）。 */
    List<OrgUnitEntity> findByParentId(String parentId);

    /** 删除保护：是否存在子节点。 */
    boolean existsByParentId(String parentId);
}
