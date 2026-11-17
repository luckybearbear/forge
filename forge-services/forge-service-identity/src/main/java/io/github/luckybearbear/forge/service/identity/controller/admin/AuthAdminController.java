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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证接口（双端前缀自动前插 /admin-api）：/admin-api/auth/login、/admin-api/auth/logout、
 * /admin-api/auth/me。admin 端 token 为平台 token（grant.tenantId=null，TokenAuthFilter 不填充租户）。
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

    private static String bearerToken(String authorization) {
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
