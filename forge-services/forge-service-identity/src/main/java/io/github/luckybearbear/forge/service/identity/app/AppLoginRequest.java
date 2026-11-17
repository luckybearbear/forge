package io.github.luckybearbear.forge.service.identity.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** app 端登录请求：租户编码 + 用户名 + 密码（无认证无租户上下文——租户编码即登录租户定位依据）。 */
public record AppLoginRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 64) String username,
        @NotBlank String password) {}
