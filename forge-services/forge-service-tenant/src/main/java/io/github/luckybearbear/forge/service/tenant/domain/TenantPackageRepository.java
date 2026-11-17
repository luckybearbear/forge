package io.github.luckybearbear.forge.service.tenant.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户套餐仓库（全局表，同 {@link TenantRepository} 约定）。 */
public interface TenantPackageRepository extends JpaRepository<TenantPackageEntity, String> {

    Optional<TenantPackageEntity> findByCode(String code);
}
