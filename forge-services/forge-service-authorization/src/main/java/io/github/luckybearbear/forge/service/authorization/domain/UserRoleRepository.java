package io.github.luckybearbear.forge.service.authorization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 用户-角色关联仓库（租户表）。 */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, String> {

    List<UserRoleEntity> findByUserId(String userId);

    void deleteByUserId(String userId);

    List<UserRoleEntity> findByRoleId(String roleId);

    void deleteByRoleId(String roleId);
}
