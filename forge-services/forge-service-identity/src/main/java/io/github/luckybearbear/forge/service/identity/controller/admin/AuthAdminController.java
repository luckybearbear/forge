package io.github.luckybearbear.forge.service.identity.controller.admin;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.identity.app.AdminLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.app.LoginResponse;
import io.github.luckybearbear.forge.service.identity.app.MeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证接口（双端前缀自动前插 /admin-api）：/admin-api/auth/login、/admin-api/auth/logout、
 * /admin-api/auth/me、/admin-api/auth/password（自助改密）。admin 端 token 为平台 token
 * （grant.tenantId=null，TokenAuthFilter 不填充租户）；password 需认证（/admin-api/** 面的
 * PLATFORM authority 门禁）——种子口令的更换通道（最终评审 Important-2）。
 * 控制器薄层约定同 {@code AuthAppController}。
 */
@RestController
@RequestMapping("/auth")
public class AuthAdminController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthAdminController(AuthService authService) {
        this.authService = authService;
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
