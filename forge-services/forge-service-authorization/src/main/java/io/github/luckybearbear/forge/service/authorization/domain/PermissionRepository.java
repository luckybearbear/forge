package io.github.luckybearbear.forge.service.authorization.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 权限点仓库（全局表，无判别式过滤——字典全租户共享）。 */
public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {

    Optional<PermissionEntity> findByCode(String code);

    List<PermissionEntity> findByCodeIn(Collection<String> codes);
}
