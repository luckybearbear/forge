package io.github.luckybearbear.forge.demo.maintenance.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 维修单仓库：租户过滤同 {@link MoldRepository}；按模具列单供 app 端查询。 */
public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrderJpaEntity, String> {

    List<MaintenanceOrderJpaEntity> findByMoldId(String moldId);
}
