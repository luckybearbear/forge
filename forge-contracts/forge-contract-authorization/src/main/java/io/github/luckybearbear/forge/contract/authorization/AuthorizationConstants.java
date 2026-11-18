package io.github.luckybearbear.forge.contract.authorization;

/**
 * 授权域公共常量（契约单源，评审 Minor-1）：跨模块/跨栈统一引用——service-authorization
 * 菜单可见性（visibleMenus）、service-identity 平台 token grant（adminLogin）、
 * security-starter 链级门禁（DSL 字面量与此同源同值）、前端 v-permission（forge-web Task 3）。
 */
public final class AuthorizationConstants {

    /**
     * 平台超级 authority：authorities 含之即全通过——菜单面全 MENU 可见（不逐 code 比对）、
     * /admin-api/** 面链级准入。admin 端平台 token grant 恒为 [PLATFORM]（仅 admin token 持有）；
     * security-starter 默认链的 {@code hasAuthority("PLATFORM")} 为 DSL 字面量，与此常量同源。
     */
    public static final String PLATFORM_AUTHORITY = "PLATFORM";

    private AuthorizationConstants() {}
}
