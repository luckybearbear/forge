package io.github.luckybearbear.forge.service.tenant.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户仓库（全局表，无判别式过滤；软删行由 Hibernate 类级 @SoftDelete 自动排除）。 */
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

    Optional<TenantEntity> findByCode(String code);
}
