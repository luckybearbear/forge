package io.github.luckybearbear.forge.service.identity.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 平台管理员仓库（全局表，无判别式过滤；admin 登录在无租户上下文下直查）。 */
public interface PlatformAdminRepository extends JpaRepository<PlatformAdminEntity, String> {

    Optional<PlatformAdminEntity> findByUsername(String username);
}
