package io.github.luckybearbear.forge.starter.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * forge.security.* 属性（spec §6.1）：
 * token-ttl 同时作为签发 TTL 与 TokenAuthFilter 的滑动续期窗口；permit-all 为放行路径
 * （Ant 风格），其余路径一律 authenticated。
 */
@ConfigurationProperties(prefix = "forge.security")
public record ForgeSecurityProperties(
        /** token 有效期：签发 TTL + 滑动续期窗口（TokenAuthFilter 每次命中刷新）。 */
        @DefaultValue("8h") Duration tokenTtl,
        /** 免认证路径（登录、健康检查、API 文档）。 */
        @DefaultValue({"/app-api/auth/login", "/admin-api/auth/login", "/actuator/health",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui/index.html"})
        List<String> permitAll) {
}
