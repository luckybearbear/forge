package io.github.luckybearbear.forge.service.identity.controller.app;

import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.contract.authorization.MenuQueryService;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.platform.security.auth.ForgeAuthentication;
import io.github.luckybearbear.forge.service.identity.app.AppLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.app.LoginResponse;
import io.github.luckybearbear.forge.service.identity.app.MeResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户端认证接口：包名含 {@code .controller.app.} → platform-web 双端前缀映射自动前插
 * {@code /app-api}（spec §6.5）——实际路径 /app-api/auth/login、/app-api/auth/logout、
 * /app-api/auth/me、/app-api/auth/menus（login 在 security-starter 默认 permit-all；
 * logout/me/menus 需认证）。
 *
 * <p>控制器保持薄层（错误码/事件/上下文编排全在 {@link AuthService}）：logout 自 Authorization
 * Bearer 提取当前 token 传入 service 吊销；me 直读 SecurityContext 快照。
 *
 * <p><b>/menus token 快照语义（菜单切片 Task 1）</b>：直取 ForgeAuthentication 的 authorities
 * （登录时冻结进 TokenGrant 的权限集）→ 契约 {@link MenuQueryService} 过滤可见菜单树——
 * <b>不经 UserPermissionsQuery 重查</b>（菜单面与权限门禁同一快照口径：授权变更后重登生效，
 * 既有 token 冻结至过期/登出）。契约经 {@link ObjectProvider} 注入：service-authorization
 * 未装配（identity 独立运行/测试）时缺省空菜单列表（照 2b UserPermissionsQuery 先例）。
 */
@RestController
@RequestMapping("/auth")
public class AuthAppController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final ObjectProvider<MenuQueryService> menuQueryServiceProvider;

    public AuthAppController(AuthService authService, ObjectProvider<MenuQueryService> menuQueryServiceProvider) {
        this.authService = authService;
        this.menuQueryServiceProvider = menuQueryServiceProvider;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody AppLoginRequest request) {
        return Result.ok(authService.appLogin(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(bearerToken(authorization));
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<MeResponse> me() {
        return Result.ok(authService.currentUser());
    }

    /** 可见菜单树：token 快照 authorities 直取（语义见类 javadoc）；契约缺省/非 Forge 主体 → 空列表。 */
    @GetMapping("/menus")
    public Result<List<MenuNodeDTO>> menus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof ForgeAuthentication forge)) {
            // 不可达防线（/app-api/** 面经 TokenAuthFilter 认证后到达）；与契约缺省同形
            return Result.ok(List.of());
        }
        MenuQueryService menuQueryService = menuQueryServiceProvider.getIfAvailable();
        if (menuQueryService == null) {
            return Result.ok(List.of()); // 契约未装配（identity 独立运行）缺省空菜单
        }
        Set<String> authorities = forge.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return Result.ok(menuQueryService.visibleMenus(authorities));
    }

    /** RFC 7235 scheme 大小写不敏感（与 TokenAuthFilter 同式）；非 Bearer 头返回 null（logout 幂等）。 */
    private static String bearerToken(String authorization) {
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
