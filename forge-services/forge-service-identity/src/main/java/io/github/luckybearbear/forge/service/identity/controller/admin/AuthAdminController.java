package io.github.luckybearbear.forge.service.identity.controller.admin;

import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.contract.authorization.MenuQueryService;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.platform.security.auth.ForgeAuthentication;
import io.github.luckybearbear.forge.service.identity.app.AdminLoginRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证接口（双端前缀自动前插 /admin-api）：/admin-api/auth/login、/admin-api/auth/logout、
 * /admin-api/auth/me、/admin-api/auth/password（自助改密）、/admin-api/auth/menus（可见菜单树）。
 * admin 端 token 为平台 token（grant.tenantId=null，TokenAuthFilter 不填充租户）；password 需认证
 * （/admin-api/** 面的 PLATFORM authority 门禁）——种子口令的更换通道（最终评审 Important-2）。
 * 控制器薄层约定同 {@code AuthAppController}。
 *
 * <p><b>/menus 为 app 面同实现的 admin 镜像（Task 2 review Important-1）</b>：语义与
 * {@code AuthAppController#menus} 逐句一致（token 快照 authorities 直取 → 契约
 * {@link MenuQueryService}，不经 UserPermissionsQuery 重查）——管理台动态路由数据源走
 * /admin-api 前缀（vite dev 代理只转发 /admin-api），admin token 的 authorities 恒含
 * PLATFORM（超级全通过）。契约经 {@link ObjectProvider} 注入，未装配 service-authorization
 * 时缺省空菜单列表。
 */
@RestController
@RequestMapping("/auth")
public class AuthAdminController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final ObjectProvider<MenuQueryService> menuQueryServiceProvider;

    public AuthAdminController(AuthService authService,
            ObjectProvider<MenuQueryService> menuQueryServiceProvider) {
        this.authService = authService;
        this.menuQueryServiceProvider = menuQueryServiceProvider;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok(authService.adminLogin(request));
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
            // 不可达防线（/admin-api/** 面经认证后到达）；与契约缺省同形
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

    /**
     * 自助改密（需已认证的 admin token）：旧密码错 IDENT-0401、新密码弱 IDENT-0400；成功后发布
     * UserPasswordChanged → 该管理员全部既有 token（含本请求所用）吊销——响应 200 后需重新登录。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangeAdminPasswordRequest request) {
        authService.changeAdminPassword(request.oldPassword(), request.newPassword());
        return Result.ok();
    }

    private static String bearerToken(String authorization) {
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
