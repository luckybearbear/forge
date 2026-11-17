package io.github.luckybearbear.forge.service.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * service-tenant 自动配置——<b>Phase 2b 服务=自动配置库形态的范式样板</b>（Task 4/5/6 照抄本形态）：
 * 应用只须引本 jar（经 forge-bom），即得租户/套餐全部 bean、JPA 实体/仓库扫描、Flyway 迁移
 * （jar 内 {@code db/migration}）与双端前缀路由下的 /admin-api 控制器。
 *
 * <p>样板要点：
 * <ul>
 * <li><b>@AutoConfiguration 类本身不能被 @ComponentScan 拾取</b>：本类的 @ComponentScan 显式挂
 *     {@link AutoConfigurationExcludeFilter}（自扫排除自身）；消费方 @SpringBootApplication 的组件
 *     扫描自带同一 filter，且本类登记于 AutoConfiguration.imports——imports 文件在 classpath 即令
 *     该 filter 生效（参照 forge-data-starter 既有模式，双路径均不重复注册）。</li>
 * <li>@EntityScan/@EnableJpaRepositories 限定 {@code domain} 子包——不吞并应用/其他服务的实体与仓库。</li>
 * <li>无 @Bean 方法：组装全经组件扫描（app/controller + 模块根包契约实现），覆盖治理点为整模块
 *     （不引本依赖即整体让位）。</li>
 * <li>依赖方向铁律：services 依赖 platform+contracts——本模块零应用侧反向耦合。</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "io.github.luckybearbear.forge.service.tenant",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM,
                classes = AutoConfigurationExcludeFilter.class))
@EntityScan(basePackages = "io.github.luckybearbear.forge.service.tenant.domain")
@EnableJpaRepositories(basePackages = "io.github.luckybearbear.forge.service.tenant.domain")
public class TenantServiceAutoConfiguration {
}
