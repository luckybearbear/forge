package io.github.luckybearbear.forge.service.identity.controller.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户创建请求（租户端）：password 为初始密码（弱密码规则与改密一致，IDENT-0400）。 */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 32) String phone,
        @NotBlank String password) {}
