package io.github.luckybearbear.forge.service.authorization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 角色-权限点关联仓库（租户表）。 */
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, String> {

    List<RolePermissionEntity> findByRoleId(String roleId);

    void deleteByRoleId(String roleId);

    /**
     * 用户的权限点编码全集（UserPermissionsQueryImpl 的 DB 侧单查询）：
     * user_role → role_permission → permission 三跳 join；user_role/role_permission 受 @TenantId
     * 判别式过滤（调用方须在正确租户上下文/事务内执行），permission 为全局表不过滤。
     */
    @Query("select distinct p.code from PermissionEntity p "
            + "where p.id in (select rp.permissionId from RolePermissionEntity rp "
            + "where rp.roleId in (select ur.roleId from UserRoleEntity ur where ur.userId = :userId))")
    List<String> findAuthorityCodes(@Param("userId") String userId);
}
