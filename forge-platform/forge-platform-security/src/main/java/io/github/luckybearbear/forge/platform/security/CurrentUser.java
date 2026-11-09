package io.github.luckybearbear.forge.platform.security;

/** 当前用户快照。Phase 2 由 Spring Security 认证结果填充。 */
public record CurrentUser(String userId, String username) {}
