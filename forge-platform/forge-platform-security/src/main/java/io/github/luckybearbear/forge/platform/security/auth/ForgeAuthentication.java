package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ForgeAuthentication extends AbstractAuthenticationToken {

    private final String userId;
    private final String tenantId;
    private final String userType;
    private final Object credentials;

    private ForgeAuthentication(String userId, String tenantId, String userType,
                                Object credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.tenantId = tenantId;
        this.userType = userType;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    public static ForgeAuthentication of(StoredToken stored, String rawToken) {
        var authorities = stored.grant().authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        return new ForgeAuthentication(stored.grant().userId(), stored.grant().tenantId(),
                stored.grant().userType(), rawToken, authorities);
    }

    @Override public Object getCredentials() { return credentials; }
    @Override public Object getPrincipal() { return userId; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getUserType() { return userType; }
}
