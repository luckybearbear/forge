package io.github.luckybearbear.forge.service.identity.controller.admin;

import jakarta.validation.constraints.NotBlank;

/** admin 自助改密请求：oldPassword 校验失败 IDENT-0401；newPassword 弱密码拒绝 IDENT-0400。 */
public record ChangeAdminPasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank String newPassword) {}
