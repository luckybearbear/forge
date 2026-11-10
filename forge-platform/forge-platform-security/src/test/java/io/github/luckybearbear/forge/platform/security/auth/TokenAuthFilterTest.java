package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.token.InMemoryTokenStore;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class TokenAuthFilterTest {

    private final TokenStore store = new InMemoryTokenStore();
    private final ThreadLocalUserProvider provider = new ThreadLocalUserProvider();
    private final TokenAuthFilter filter = new TokenAuthFilter(store, Duration.ofHours(1));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        ThreadLocalUserProvider.clear();
    }

    @Test
    void validTokenFillsSecurityAndTenantContext() throws ServletException, IOException {
        StoredToken token = store.create(
                new TokenGrant("u-1", "t-1", "MEMBER", Set.of("mold:create")), Duration.ofHours(1));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-api/x");
        request.addHeader("Authorization", "Bearer " + token.token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        // jakarta.servlet.Servlet 非 函数接口（多个抽象方法），lambda 链尾不可用——匿名 HttpServlet 承载链内断言
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                assertThat(auth).isInstanceOf(ForgeAuthentication.class);
                assertThat(((ForgeAuthentication) auth).getTenantId()).isEqualTo("t-1");
                assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo("t-1");
                assertThat(provider.currentUser()).contains(new CurrentUser("u-1", "u-1"));
            }
        });

        filter.doFilter(request, response, chain);

        // finally 清理：链结束后三处均空
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isEmpty();
        assertThat(provider.currentUser()).isEmpty();
    }

    @Test
    void missingOrInvalidTokenPassesThroughUnauthenticated() throws ServletException, IOException {
        for (String header : new String[] {null, "Bearer invalid"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-api/x");
            if (header != null) {
                request.addHeader("Authorization", header);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).as(header).isNull();
        }
    }

    @Test
    void validTokenIsRefreshed() throws ServletException, IOException {
        StoredToken token = store.create(
                new TokenGrant("u-1", "t-1", "MEMBER", Set.of()), Duration.ofMillis(200));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("Authorization", "Bearer " + token.token());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        waitQuietly(250); // 原过期点之后

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/x");
        second.addHeader("Authorization", "Bearer " + token.token());
        var chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            }
        });
        filter.doFilter(second, new MockHttpServletResponse(), chain); // 续期后仍可认证
    }

    private static void waitQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
