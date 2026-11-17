package io.github.luckybearbear.forge.starter.web;

import io.github.luckybearbear.forge.platform.web.CacheRequestBodyFilter;
import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixProperties;
import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixRequestMappingHandlerMapping;
import io.github.luckybearbear.forge.platform.web.FilterOrder;
import io.github.luckybearbear.forge.platform.web.GlobalExceptionHandler;
import io.github.luckybearbear.forge.platform.web.JacksonLongToStringModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * web 层自动装配（spec §6.5；Task 11 修复轮补齐批次装配缺口，终审 Important #2 补齐
 * body filter 装配与 Long→String 注册）：
 *
 * <ul>
 *   <li>双端前缀（controller.app/admin.* → /app-api、/admin-api）：以
 *       {@code @ConditionalOnMissingBean(WebMvcRegistrations.class)} 注册
 *       {@link ForgeApiPrefixRequestMappingHandlerMapping}（终审遗留的覆盖治理，2b 兑现：
 *       原先经 @Import 无条件注册，与应用自带的 WebMvcRegistrations 形成双候选，Boot 4 的
 *       {@code getIfUnique()} 解析为 null，双方静默失效；现在应用自带注册器时 forge 让位、
 *       应用注册器正常生效）；{@code forge.web.api-prefix.*} 绑定至
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
 */
@AutoConfiguration
@EnableConfigurationProperties({ForgeApiPrefixProperties.class, ForgeWebAccessLogProperties.class})
public class ForgeWebAutoConfiguration {

    /**
     * 双端前缀的 WebMvcRegistrations（Boot 4 多 default 方法非函数接口——匿名实现仅覆写
     * handler mapping 一项，其余沿 Boot 缺省）。应用自带 WebMvcRegistrations 时让位：
     * 用户注册器独占生效（自定义前缀请走 {@code forge.web.api-prefix.*} 或自带注册器）。
     */
    @Bean
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public WebMvcRegistrations forgeApiPrefixWebMvcRegistrations(ForgeApiPrefixProperties properties) {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new ForgeApiPrefixRequestMappingHandlerMapping(properties);
            }
        };
    }

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
