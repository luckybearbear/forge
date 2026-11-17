package io.github.luckybearbear.forge.service.organization;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * service-organization 自动配置（照抄 service-tenant 范式样板）：应用只须引本 jar（经 forge-bom），
 * 即得组织树/岗位/用户挂岗全部 bean、JPA 实体/仓库扫描、Flyway 迁移（jar 内
 * {@code db/migration}）与双端前缀路由下的 /app-api 控制器。
 *
 * <p>样板要点（与 TenantServiceAutoConfiguration 逐条一致）：
 * <ul>
 * <li><b>@AutoConfiguration 类本身不能被 @ComponentScan 拾取</b>：本类的 @ComponentScan 显式挂
 *     {@link AutoConfigurationExcludeFilter}（自扫排除自身）；消费方 @SpringBootApplication 的组件
 *     扫描自带同一 filter，且本类登记于 AutoConfiguration.imports——双路径均不重复注册。</li>
 * <li>@EntityScan/@EnableJpaRepositories 限定 {@code domain} 子包（Boot 4 注意：@EntityScan 在
 *     新包 {@code org.springframework.boot.persistence.autoconfigure}）。</li>
 * <li>无 @Bean 方法：组装全经组件扫描（app/ 服务、controller/app/ 控制器、app 内契约实现
 *     OrgTreeQueryServiceImpl），覆盖治理点为整模块（不引本依赖即整体让位）。</li>
 * <li>依赖方向铁律：services 依赖 platform+contracts——organization 仅消费契约
 *     （identity 的 UserQueryService——实现方为 service-identity，装配期由消费应用提供该 bean）
 *     而不依赖任何 service。</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.github.luckybearbear.forge.service.organization",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM,
                classes = AutoConfigurationExcludeFilter.class))
@EntityScan(basePackages = "io.github.luckybearbear.forge.service.organization.domain")
@EnableJpaRepositories(basePackages = "io.github.luckybearbear.forge.service.organization.domain")
public class OrganizationServiceAutoConfiguration {
}
