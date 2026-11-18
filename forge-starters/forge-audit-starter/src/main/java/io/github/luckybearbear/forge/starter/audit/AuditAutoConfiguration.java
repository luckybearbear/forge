package io.github.luckybearbear.forge.starter.audit;

import io.github.luckybearbear.forge.platform.web.FilterOrder;
import io.github.luckybearbear.forge.starter.web.ForgeWebAccessLogProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 审计自动装配（spec §6.6，2b Task 7）：访问/错误日志落库的组装出口。应用引本 starter 即得：
 *
 * <ul>
 *   <li>{@link ApiAccessLogFilter} 注册（order={@link FilterOrder#ACCESS_LOG_FILTER}=-80，
 *       发布 {@code AccessLogRecorded}）；错误侧发布点在 web-starter 装配的
 *       {@code GlobalExceptionHandler}（500 兜底分支发布 {@code ErrorLogRecorded}，无本 starter
 *       时发布无害空投）。</li>
 *   <li>@ComponentScan platform-audit 的 {@code listener} 包——两个
 *       {@code @ApplicationModuleListener} 监听器异步消费落库（AFTER_COMMIT 投递：filter 的
 *       publishEvent 已以 TransactionTemplate 包裹满足事务内发布）；事件经 forge-data-starter
 *       的 Modulith JDBC 登记表（event_publication）持久化。</li>
 *   <li>@EntityScan/@EnableJpaRepositories 限定 audit {@code domain} 包（Boot 4 注意：
 *       @EntityScan 在 {@code org.springframework.boot.persistence.autoconfigure}）。</li>
 *   <li>jar 内 {@code db/migration} 随 Flyway 缺省位置自动建 {@code api_access_log}/
 *       {@code api_error_log}（全局表——tenant_id/user_id 普通冗余列，无判别式过滤）。</li>
 * </ul>
 *
 * <p><b>开关</b>：复用 {@code forge.web.access-log.enabled}（缺省 true）——与 body 缓存过滤器
 * 同门：关缓存即关访问日志（无缓存体则摘要恒空，成对开关语义一致、避免半开状态）；
 * {@code forge.audit.async}（缺省 true，P3 Task 2）选发布通道——false 时本类的异步发布器
 * 不装配，filter 回退请求线程同步发布（逃生口）。
 *
 * <p><b>组合型 starter</b>（Task 7 裁定，修正 starter 互不依赖约定）：审计 = web 过滤器 +
 * 事件登记 + JPA 落库的横切组合，依赖 forge-spring-boot-starter（web 栈与属性）与
 * forge-data-starter（Modulith registry/JPA/Flyway）；platform-audit 保持不依赖任何 starter
 * （"platform 不依赖 starter"铁律不破）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ComponentScan(basePackages = "io.github.luckybearbear.forge.platform.audit.listener")
@EntityScan(basePackages = "io.github.luckybearbear.forge.platform.audit.domain")
@EnableJpaRepositories(basePackages = "io.github.luckybearbear.forge.platform.audit.domain")
@EnableConfigurationProperties(ForgeAuditProperties.class)
public class AuditAutoConfiguration {

    /** 异步发布器（forge.audit.async=true 缺省装配；逃生口 false 时不存在，filter 走同步构造）。 */
    @Bean
    @ConditionalOnProperty(prefix = "forge.audit", name = "async",
            havingValue = "true", matchIfMissing = true)
    public AsyncAccessLogPublisher asyncAccessLogPublisher(ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager, ForgeAuditProperties properties) {
        return new AsyncAccessLogPublisher(eventPublisher, transactionManager,
                properties.queueCapacity());
    }

    @Bean
    @ConditionalOnMissingBean(ApiAccessLogFilter.class)
    @ConditionalOnProperty(prefix = "forge.web.access-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilterRegistration(
            ObjectProvider<AsyncAccessLogPublisher> asyncPublisher,
            ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager,
            ForgeWebAccessLogProperties properties) {
        ApiAccessLogFilter filter = asyncPublisher.getIfAvailable() != null
                ? new ApiAccessLogFilter(asyncPublisher.getObject(), properties.maxBodySize().toBytes())
                : new ApiAccessLogFilter(eventPublisher, transactionManager,
                        properties.maxBodySize().toBytes());
        FilterRegistrationBean<ApiAccessLogFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(FilterOrder.ACCESS_LOG_FILTER);
        return registration;
    }
}
