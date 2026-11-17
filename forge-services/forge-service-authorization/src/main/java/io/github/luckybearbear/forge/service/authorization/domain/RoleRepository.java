package io.github.luckybearbear.forge.service.authorization.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色仓库（租户表，@TenantId 判别式隔离；软删行由 Hibernate 类级 @SoftDelete 自动排除）。 */
public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    Optional<RoleEntity> findByCode(String code);
}
