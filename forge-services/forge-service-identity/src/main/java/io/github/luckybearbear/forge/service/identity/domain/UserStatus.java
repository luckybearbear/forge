package io.github.luckybearbear.forge.service.identity.domain;

/** 账号状态（identity_user 与 platform_admin 共用）：ENABLED 可登录；DISABLED 按坏凭据拒绝（不泄露停用状态）。 */
public enum UserStatus {
    ENABLED, DISABLED
}
