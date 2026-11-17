package io.github.luckybearbear.forge.service.authorization;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * service-authorization 自动配置（照抄 service-tenant 范式样板）：应用只须引本 jar（经
 * forge-bom），即得角色/权限点/分配/数据权限全部 bean、JPA 实体/仓库扫描、Flyway 迁移
 * （jar 内 {@code db/migration}，含权限点字典 seed）与双端前缀路由下的 /app-api、/admin-api
 * 控制器，以及契约 {@code UserPermissionsQuery} 实现（identity 登录 grant 装配）与
 * TenantCreated 监听（新租户默认 ADMIN 角色）。
 *
 * <p>样板要点（与 TenantServiceAutoConfiguration 逐条一致）：
 * <ul>
 * <li><b>@AutoConfiguration 类本身不能被 @ComponentScan 拾取</b>：本类的 @ComponentScan 显式挂
 *     {@link AutoConfigurationExcludeFilter}（自扫排除自身）；消费方 @SpringBootApplication 的组件
 *     扫描自带同一 filter，且本类登记于 AutoConfiguration.imports——双路径均不重复注册。</li>
 * <li>@EntityScan/@EnableJpaRepositories 限定 {@code domain} 子包（Boot 4 注意：@EntityScan 在
 *     新包 {@code org.springframework.boot.persistence.autoconfigure}）。</li>
 * <li>无 @Bean 方法：组装全经组件扫描（app/ 服务与监听、controller/ 控制器、app/ 契约实现），
 *     覆盖治理点为整模块（不引本依赖即整体让位）。</li>
 * <li>依赖方向铁律：services 依赖 platform+contracts——authorization 仅消费契约
 *     （authorization/tenant）而不依赖任何 service，也不被 identity 依赖（登录侧经
 *     UserPermissionsQuery 契约 + ObjectProvider 可选装配）。</li>
 * <li>@EnableMethodSecurity 不在此（Task 4 裁定归属 forge-security-starter）。</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.github.luckybearbear.forge.service.authorization",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM,
                classes = AutoConfigurationExcludeFilter.class))
@EntityScan(basePackages = "io.github.luckybearbear.forge.service.authorization.domain")
@EnableJpaRepositories(basePackages = "io.github.luckybearbear.forge.service.authorization.domain")
public class AuthorizationServiceAutoConfiguration {
}
