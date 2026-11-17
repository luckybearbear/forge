package io.github.luckybearbear.forge.service.identity.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 租户用户仓库：{@code @TenantId} 判别式过滤总生效——查询（findByUsername）只命中当前租户行，
 * 须在租户上下文内执行（登录侧由 AuthService 显式 set/finally clear，管理侧由 TokenAuthFilter 填充）；
 * 软删行由 Hibernate 类级 @SoftDelete 自动排除。
 */
public interface IdentityUserRepository extends JpaRepository<IdentityUserEntity, String> {

    Optional<IdentityUserEntity> findByUsername(String username);
}
