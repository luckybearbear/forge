package io.github.luckybearbear.forge.platform.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** append-only 流水仓库：无业务查询出口（检索/清留在 Phase 3 运维能力）。 */
public interface ApiErrorLogRepository extends JpaRepository<ApiErrorLogEntity, String> {}
