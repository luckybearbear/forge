package io.github.luckybearbear.forge.service.identity.controller.app;

import jakarta.validation.constraints.NotBlank;

/** 改密请求（租户端）：oldPassword 校验失败 IDENT-0401；newPassword 弱密码拒绝 IDENT-0400。 */
public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank String newPassword) {}
