package io.github.luckybearbear.forge.starter.web;

import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixConfiguration;
import io.github.luckybearbear.forge.platform.web.ForgeApiPrefixProperties;
import io.github.luckybearbear.forge.platform.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * web 层自动装配：全局异常处理 + 双端前缀路由（spec §6.5；Task 11 修复轮补齐批次装配缺口）。
 *
 * <p>{@code @Import(ForgeApiPrefixConfiguration.class)}：controller.app/admin.* 包 → /app-api、
 * /admin-api 前缀开箱即用；{@code forge.web.api-prefix.*} 绑定至 {@link ForgeApiPrefixProperties}
 * （缺省 enabled=true、/app-api、/admin-api，取值经斜杠归一）。用户显式声明自己的
 * ForgeApiPrefixProperties bean 或 WebMvcRegistrations 仍可覆盖（绑定 bean 与替换式注册器语义保留）。
 */
@AutoConfiguration
@EnableConfigurationProperties(ForgeApiPrefixProperties.class)
@Import(ForgeApiPrefixConfiguration.class)
public class ForgeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
