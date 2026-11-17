package io.github.luckybearbear.forge.service.identity.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** admin 端登录请求：平台管理员（全局账号，无租户维度）。 */
public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank String password) {}
