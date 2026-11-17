package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.TenantIgnore;
import io.github.luckybearbear.forge.platform.security.auth.ForgeSecurityResponses;
import io.github.luckybearbear.forge.platform.security.auth.TokenAuthFilter;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;

/**
 * 安全层自动装配（spec §6.1）：
 *
 * <ul>
 *   <li>{@code TokenStore}：Redis 类路径（{@link RedisOperations} 存在）且未自定义 → RedisTokenStore；
 *       否则由 {@link ForgeTokenStoreFallbackAutoConfiguration}（after 本类）降级 InMemoryTokenStore + 启动 warn</li>
 *   <li>SecurityFilterChain（@ConditionalOnMissingBean，应用可整体替换）：无状态 + CSRF off +
 *       permit-all（forge.security.permit-all，登录/健康/文档）+ 其余 authenticated +
 *       401/403 统一 Result 输出；TokenAuthFilter 以 new 注入链（不暴露为 @Bean Filter，
 *       避免 Boot 全局注册导致的二次执行）</li>
 *   <li>@TenantIgnore AOP：方法/类级切面切换平台视角，finally 还原</li>
 * </ul>
 *
 * <p>两处排序实证（Task 10 集成测试 + 条件评估报告）：
 * <ul>
 *   <li>须先于 Boot 的 {@code ServletWebSecurityAutoConfiguration}（Boot 4 中默认链所在，
 *       Boot 3 的 SpringBootWebSecurityConfiguration 已更名，jar 实证 4.1.2）——双方均
 *       @ConditionalOnMissingBean 语义，先注册者胜；不显式排序则仅靠类名字典序，不可靠</li>
 *   <li>TokenStore 兜底不能与 Redis 分支同置一个类的内嵌类里：内嵌类加载顺序不受
 *       @Order/声明序约束（条件评估报告实证 fallback 先于 redis 注册），故拆独立自动配置类
 *       以 {@code after} 显式定序</li>
 * </ul>
 */
@AutoConfiguration(before = org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class)
@EnableConfigurationProperties(ForgeSecurityProperties.class)
public class ForgeSecurityAutoConfiguration {

    /**
     * SERVLET 守卫（2b 前置加固）：安全链仅装配于 Servlet Web 环境——非 Servlet
     * （NONE/Reactive，如纯后端服务/事件处理器仅引本 starter 用 TokenStore/@TenantIgnore）时
     * 优雅退避，否则 HttpSecurity 等链路基建缺失会导致上下文启动失败。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SecurityFilterChain.class, HttpSecurity.class, TokenAuthFilter.class})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class SecurityChainConfiguration {

        /**
         * TokenAuthFilter 经 addFilterBefore 注入（不暴露为 @Bean Filter，避免 Boot 全局注册二次执行）：
         * 认证 + 租户上下文填充 + 滑动续期（TTL = forge.security.token-ttl），链尾 finally 清理。
         *
         * <p>锚点实证（集成测试 RED→GREEN）：锚 {@code AuthorizationFilter}（即
         * ExceptionTranslationFilter 之后）时，未认证请求在 AuthorizationFilter 抛 AccessDeniedException
         * 后先经本 filter 的 finally 清 SecurityContext，ExceptionTranslationFilter 再按（已空的）上下文
         * 判定 → 恒走 accessDeniedHandler → 403 而非 401。锚 {@code ExceptionTranslationFilter}
         * （其之前）时 finally 清理发生在 ETF 处理完异常之后，401（entryPoint，含匿名）与
         * 403（已认证越权）语义恢复正确。
         */
        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain forgeSecurityFilterChain(HttpSecurity http, TokenStore tokenStore,
                ForgeSecurityProperties properties) throws Exception {
            TokenAuthFilter tokenAuthFilter = new TokenAuthFilter(tokenStore, properties.tokenTtl());
            http.csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> {
                        properties.permitAll().forEach(path -> authorize.requestMatchers(path).permitAll());
                        authorize.anyRequest().authenticated();
                    })
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint((request, response, authException) ->
                                    ForgeSecurityResponses.write401(response))
                            .accessDeniedHandler((request, response, accessDeniedException) ->
                                    ForgeSecurityResponses.write403(response)))
                    .addFilterBefore(tokenAuthFilter, ExceptionTranslationFilter.class);
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Aspect.class, TenantIgnore.class})
    static class TenantIgnoreAspectConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TenantIgnoreAspect forgeTenantIgnoreAspect() {
            return new TenantIgnoreAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisOperations.class)
    static class RedisTokenStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(TokenStore.class)
        TokenStore redisTokenStore(StringRedisTemplate redisTemplate) {
            return new RedisTokenStore(redisTemplate);
        }
    }
}
