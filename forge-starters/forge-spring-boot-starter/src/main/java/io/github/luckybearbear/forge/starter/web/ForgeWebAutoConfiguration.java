package io.github.luckybearbear.forge.starter.web;

import io.github.luckybearbear.forge.platform.web.CacheRequestBodyFilter;
import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixConfiguration;
import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixProperties;
import io.github.luckybearbear.forge.platform.web.FilterOrder;
import io.github.luckybearbear.forge.platform.web.GlobalExceptionHandler;
import io.github.luckybearbear.forge.platform.web.JacksonLongToStringModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * web 层自动装配（spec §6.5；Task 11 修复轮补齐批次装配缺口，终审 Important #2 补齐
 * body filter 装配与 Long→String 注册）：
 *
 * <ul>
 *   <li>{@code @Import(ForgeApiPrefixConfiguration.class)}：controller.app/admin.* 包 →
 *       /app-api、/admin-api 前缀开箱即用；{@code forge.web.api-prefix.*} 绑定至
 *       {@link ForgeApiPrefixProperties}（缺省 enabled=true、/app-api、/admin-api，取值经斜杠归一）。</li>
 *   <li>Long→String（防 JS 精度丢失）：注册 {@link JacksonLongToStringModule} 为
 *       {@code tools.jackson.databind.JacksonModule} Bean——Boot 4 的 Jackson 自动配置收集
 *       容器内全部 JacksonModule Bean 注入 mapper builder（jar 实证 spring-boot-jackson 4.1.2
 *       AbstractMapperBuilderCustomizer 构造器 Collection&lt;JacksonModule&gt;），故所有
 *       Jackson 消费者（MVC 转换器/actuator 等）默认获得 Long→String。</li>
 *   <li>body 可重复读：{@code forge.web.access-log.*}（缺省 enabled=true、8KB、空排除集），
 *       以 {@link FilterRegistrationBean} 注册 {@link CacheRequestBodyFilter}，
 *       order={@link FilterOrder#REQUEST_BODY_WRAPPER}；排除 URL 为精确路径匹配。</li>
 * </ul>
 *
 * <p><b>WebMvcRegistrations 覆盖语义（如实描述，终审修正）</b>：Boot 4 的
 * {@code WebMvcAutoConfiguration$EnableWebMvcConfiguration} 以
 * {@code ObjectProvider.getIfUnique()} 解析 {@code WebMvcRegistrations}——若应用另行声明
 * 自己的 WebMvcRegistrations Bean，上下文出现双候选且均非 primary，getIfUnique() 解析为
 * null，<b>forge 的前缀注册器与应用自己的注册器双双静默失效</b>（无报错、无日志，前缀不再
 * 生效）。已知现状，不做静默兜底；2b 将改为 @ConditionalOnMissingBean/compose 方案（应用
 * 注册器存在时让位或组合）。当下规避：依赖方不要再声明 WebMvcRegistrations Bean；
 * 自定义前缀请走 {@code forge.web.api-prefix.*} 配置项。
 */
@AutoConfiguration
@EnableConfigurationProperties({ForgeApiPrefixProperties.class, ForgeWebAccessLogProperties.class})
@Import(ForgeApiPrefixConfiguration.class)
public class ForgeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Long→String：JacksonModule Bean 由 Boot Jackson 自动配置统一收集（见类 javadoc）。 */
    @Bean
    public JacksonLongToStringModule forgeLongToStringModule() {
        return new JacksonLongToStringModule();
    }

    /** body 可重复读（访问日志/审计前置）：默认启用，{@code forge.web.access-log.enabled=false} 关闭。 */
    @Bean
    @ConditionalOnMissingBean(CacheRequestBodyFilter.class)
    @ConditionalOnProperty(prefix = "forge.web.access-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CacheRequestBodyFilter> cacheRequestBodyFilterRegistration(
            ForgeWebAccessLogProperties properties) {
        FilterRegistrationBean<CacheRequestBodyFilter> registration = new FilterRegistrationBean<>(
                new CacheRequestBodyFilter(properties.maxBodySize().toBytes(), properties.excludeUrls()));
        registration.setOrder(FilterOrder.REQUEST_BODY_WRAPPER);
        return registration;
    }
}
