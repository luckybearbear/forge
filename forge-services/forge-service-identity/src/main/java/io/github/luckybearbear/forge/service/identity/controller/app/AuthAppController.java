package io.github.luckybearbear.forge.service.identity.controller.app;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.identity.app.AppLoginRequest;
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
 * 租户端认证接口：包名含 {@code .controller.app.} → platform-web 双端前缀映射自动前插
 * {@code /app-api}（spec §6.5）——实际路径 /app-api/auth/login、/app-api/auth/logout、
 * /app-api/auth/me（login 在 security-starter 默认 permit-all；logout/me 需认证）。
 *
 * <p>控制器保持薄层（错误码/事件/上下文编排全在 {@link AuthService}）：logout 自 Authorization
 * Bearer 提取当前 token 传入 service 吊销；me 直读 SecurityContext 快照。
 */
@RestController
@RequestMapping("/auth")
public class AuthAppController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthAppController(AuthService authService) {
        this.authService = authService;
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

    /** RFC 7235 scheme 大小写不敏感（与 TokenAuthFilter 同式）；非 Bearer 头返回 null（logout 幂等）。 */
    private static String bearerToken(String authorization) {
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
