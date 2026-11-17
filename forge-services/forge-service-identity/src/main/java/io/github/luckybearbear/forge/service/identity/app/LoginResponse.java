package io.github.luckybearbear.forge.service.identity.app;

import java.time.Instant;

/**
 * 登录响应（双端同构）：token 为不透明凭据（Authorization: Bearer &lt;token&gt;）；
 * tenantId 在 admin 端为 null（平台 token——TokenAuthFilter 见 null 不填充租户上下文）。
 */
public record LoginResponse(String token, Instant expiresAt, String userId, String tenantId) {}
