package io.github.luckybearbear.forge.service.identity.app;

import java.util.Set;

/**
 * /me 响应：当前认证主体快照（SecurityContext 的 ForgeAuthentication）——
 * app 端含 tenantId/权限集；admin 端 tenantId=null、权限集为空（PLATFORM 常量授权）。
 */
public record MeResponse(String userId, String tenantId, String userType, Set<String> authorities) {}
