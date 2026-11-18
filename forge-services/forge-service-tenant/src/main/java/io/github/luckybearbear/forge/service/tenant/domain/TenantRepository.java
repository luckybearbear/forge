package io.github.luckybearbear.forge.service.tenant.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 租户仓库（全局表，无判别式过滤；软删行由 Hibernate 类级 @SoftDelete 自动排除）。 */
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

    Optional<TenantEntity> findByCode(String code);

    /** 全量列表（code 升序——排序键在活跃行上唯一，输出稳定；管理端全量下拉/表格用）。 */
    List<TenantEntity> findAllByOrderByCodeAsc();
}
