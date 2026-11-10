package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 认证 + 租户上下文填充（spec §6.1 修订：合并原 TenantContextFilter 职责）。 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenStore tokenStore;
    private final Duration refreshTtl;

    public TokenAuthFilter(TokenStore tokenStore, Duration refreshTtl) {
        this.tokenStore = tokenStore;
        this.refreshTtl = refreshTtl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearer(request);
        if (token != null) {
            tokenStore.resolve(token).ifPresent(stored -> {
                tokenStore.refresh(token, refreshTtl); // 滑动续期
                SecurityContextHolder.getContext().setAuthentication(ForgeAuthentication.of(stored, token));
                ThreadLocalUserProvider.set(new CurrentUser(stored.grant().userId(), stored.grant().userId()));
                if (stored.grant().tenantId() != null) {
                    TenantContextHolder.setTenantId(stored.grant().tenantId());
                }
            });
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            ThreadLocalUserProvider.clear();
            TenantContextHolder.clear();
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
