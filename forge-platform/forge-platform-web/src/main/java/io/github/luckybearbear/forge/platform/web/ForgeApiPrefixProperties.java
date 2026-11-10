package io.github.luckybearbear.forge.platform.web;

/**
 * 双端前缀配置（spec §6.5）。
 *
 * <p>platform-web 不做配置绑定：starter 侧将 {@code forge.web.api-prefix.*} 绑定后以 bean 注入；
 * 本模块未提供 bean 时使用 {@link #defaults()}。
 *
 * @param enabled    是否启用前缀（false 时全部不加）
 * @param appPrefix  app 端（包名含 {@code .controller.app.}）前缀
 * @param adminPrefix admin 端（包名含 {@code .controller.admin.}）前缀
 */
public record ForgeApiPrefixProperties(boolean enabled, String appPrefix, String adminPrefix) {

    public static ForgeApiPrefixProperties defaults() {
        return new ForgeApiPrefixProperties(true, "/app-api", "/admin-api");
    }
}
