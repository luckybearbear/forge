package io.github.luckybearbear.forge.platform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 双端前缀配置（spec §6.5）。
 *
 * <p>Task 11 修复轮：本 record 兼作配置绑定模型——starter 侧经
 * {@code @EnableConfigurationProperties} 注册后绑定 {@code forge.web.api-prefix.*}
 * （缺省由 {@code @DefaultValue} 兜底）；程序化构造/显式 @Bean 覆盖仍可用（platform-web 自测即如此）。
 * 前缀取值统一经 {@link ForgeApiPrefixRequestMappingHandlerMapping#normalizePrefix} 斜杠归一。
 *
 * @param enabled     是否启用前缀（false 时全部不加）
 * @param appPrefix   app 端（包名含 {@code .controller.app.}）前缀
 * @param adminPrefix admin 端（包名含 {@code .controller.admin.}）前缀
 */
@ConfigurationProperties(prefix = "forge.web.api-prefix")
public record ForgeApiPrefixProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("/app-api") String appPrefix,
        @DefaultValue("/admin-api") String adminPrefix) {

    /** 程序化缺省（未注册绑定的场景回退用，与 @DefaultValue 保持一致）。 */
    public static ForgeApiPrefixProperties defaults() {
        return new ForgeApiPrefixProperties(true, "/app-api", "/admin-api");
    }
}
