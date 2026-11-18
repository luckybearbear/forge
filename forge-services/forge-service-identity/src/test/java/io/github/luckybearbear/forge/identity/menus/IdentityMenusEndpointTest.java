package io.github.luckybearbear.forge.identity.menus;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationConstants;
import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.contract.authorization.MenuQueryService;
import io.github.luckybearbear.forge.contract.authorization.UserPermissionsQuery;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.platform.security.auth.ForgeAuthentication;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.service.identity.IdentityServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.controller.admin.AuthAdminController;
import io.github.luckybearbear.forge.service.identity.controller.app.AuthAppController;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * /menus 端点实证（菜单切片 Task 1）：token 快照语义——authorities 直取
 * ForgeAuthentication（登录时冻结进 TokenGrant 的权限集）传给契约 {@link MenuQueryService}，
 * <b>不经 UserPermissionsQuery 重查</b>（在场桩返回 ghost:authority 且零调用——菜单面与权限门禁
 * 同一快照口径，授权变更后重登生效）。两态：stub PLATFORM → 全量；stub 受限 → 过滤。
 * admin 镜像（Task 2 review Important-1）：AuthAdminController 的 /admin-api/auth/menus 同实现
 * ——平台 token（authorities=[PLATFORM]，tenantId=null）→ 全 MENU 树。
 * 契约缺省态（ObjectProvider 空列表 → 空菜单）在 IdentityServiceIntegrationTest（其上下文无
 * MenuQueryService bean）。
 *
 * <p><b>包位置在服务根包之外</b>（...forge.identity.menus，login/permissions 测试类同范式）：
 * 独立分支包 + 桩经 @Bean 注册（@Bean 不被组件扫描发现），各上下文只见自己的桩。
 */
@SpringBootTest(classes = IdentityMenusEndpointTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(IdentityServiceAutoConfiguration.class)
class IdentityMenusEndpointTest extends AbstractIntegrationTest {

    private static final String TENANT_ID = "t-menus";

    @SpringBootApplication
    static class TestApp {

        @Bean
        StubTenantQueryService stubTenantQueryService() {
            return new StubTenantQueryService();
        }

        /** 干扰桩：若 /menus 误走 UserPermissionsQuery，会返回 ghost:authority 且计数 >0。 */
        @Bean
        StubPermissionsQuery stubPermissionsQuery() {
            return new StubPermissionsQuery();
        }

        @Bean
        StubMenuQueryService stubMenuQueryService() {
            return new StubMenuQueryService();
        }
    }

    static class StubTenantQueryService implements TenantQueryService {

        private final Map<String, TenantDTO> tenantsByCode = new ConcurrentHashMap<>(Map.of("menus",
                new TenantDTO(TENANT_ID, "menus", "菜单租户", null, "ENABLED", null)));

        @Override
        public Optional<TenantDTO> findById(String tenantId) {
            return tenantsByCode.values().stream().filter(t -> t.id().equals(tenantId)).findFirst();
        }

        @Override
        public Optional<TenantDTO> findByCode(String code) {
            return Optional.ofNullable(tenantsByCode.get(code));
        }
    }

    static class StubPermissionsQuery implements UserPermissionsQuery {

        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Set<String> authoritiesOf(String tenantId, String userId) {
            calls.incrementAndGet();
            return Set.of("ghost:authority");
        }
    }

    /** 契约桩：记录收到的 authorities（快照直传证据）+ 按 PLATFORM/受限两态回放可见性语义。 */
    static class StubMenuQueryService implements MenuQueryService {

        final List<Set<String>> received = new CopyOnWriteArrayList<>();

        @Override
        public List<MenuNodeDTO> visibleMenus(Set<String> authorities) {
            received.add(Set.copyOf(authorities));
            if (authorities.contains(AuthorizationConstants.PLATFORM_AUTHORITY)) {
                return List.of(menuNode("dashboard"), dirNode());
            }
            return authorities.contains("dashboard") ? List.of(menuNode("dashboard")) : List.of();
        }
    }

    @Autowired AuthAppController authAppController;
    @Autowired AuthAdminController authAdminController;
    @Autowired StubMenuQueryService menus;
    @Autowired StubPermissionsQuery permissionsQuery;

    @Test
    void platformTokenSnapshotYieldsFullMenuTree() {
        Result<List<MenuNodeDTO>> result = runAsMemberReturning(Set.of("PLATFORM"),
                () -> authAppController.menus());

        assertThat(flattenCodes(result.data()))
                .containsExactlyInAnyOrder("dashboard", "sys-dir", "tenant:manage");
        // 快照直传：契约收到 token 的 authorities 原集（而非 UserPermissionsQuery 的 ghost 集，且其零调用）
        assertThat(menus.received.getLast()).containsExactly("PLATFORM");
        assertThat(permissionsQuery.calls.get()).isZero();
    }

    @Test
    void restrictedTokenSnapshotYieldsFilteredMenus() {
        Result<List<MenuNodeDTO>> result = runAsMemberReturning(Set.of("dashboard"),
                () -> authAppController.menus());

        assertThat(flattenCodes(result.data())).containsExactly("dashboard");
        assertThat(menus.received.getLast()).containsExactly("dashboard");
        assertThat(permissionsQuery.calls.get()).isZero();
    }

    /**
     * admin 镜像端点（/admin-api/auth/menus，Task 2 review Important-1）：平台 token 语义
     * （grant.tenantId=null、USER_TYPE_PLATFORM、authorities=[PLATFORM]——adminLogin 冻结形状）
     * → 快照直传 + 全 MENU 树；与 app 面同走 UserPermissionsQuery 零调用口径。
     */
    @Test
    void adminMenusMirrorYieldsFullMenuTreeForPlatformToken() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(ForgeAuthentication.of(
                new StoredToken("raw-admin-menu-token",
                        new TokenGrant("u-admin-menus", null, AuthService.USER_TYPE_PLATFORM,
                                Set.of(AuthorizationConstants.PLATFORM_AUTHORITY)),
                        Instant.now().plus(Duration.ofMinutes(5))),
                "raw-admin-menu-token"));
        SecurityContextHolder.setContext(context);
        Result<List<MenuNodeDTO>> result;
        try {
            result = authAdminController.menus();
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(flattenCodes(result.data()))
                .containsExactlyInAnyOrder("dashboard", "sys-dir", "tenant:manage");
        assertThat(menus.received.getLast()).containsExactly("PLATFORM");
        assertThat(permissionsQuery.calls.get()).isZero();
    }

    // ---- helpers ----

    /** SecurityContext 填充 ForgeAuthentication（TokenAuthFilter 同构：grant → authorities 快照）。 */
    private <T> T runAsMemberReturning(Set<String> authorities, java.util.function.Supplier<T> action) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(ForgeAuthentication.of(
                new StoredToken("raw-menu-token",
                        new TokenGrant("u-menus", TENANT_ID, AuthService.USER_TYPE_MEMBER, authorities),
                        Instant.now().plus(Duration.ofMinutes(5))),
                "raw-menu-token"));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static MenuNodeDTO menuNode(String code) {
        return new MenuNodeDTO("id-" + code, null, "MENU", code, code, "/" + code, null, null, null, true,
                List.of());
    }

    private static MenuNodeDTO dirNode() {
        return new MenuNodeDTO("id-sys-dir", null, "DIR", "sys-dir", "系统管理", null, null, null, 100, true,
                List.of(new MenuNodeDTO("id-tenant-manage", "id-sys-dir", "MENU", "tenant:manage",
                        "租户管理", "/system/tenants", null, null, 110, true, List.of())));
    }

    private static List<String> flattenCodes(List<MenuNodeDTO> nodes) {
        List<String> codes = new ArrayList<>();
        appendCodes(nodes, codes);
        return codes;
    }

    private static void appendCodes(List<MenuNodeDTO> nodes, List<String> sink) {
        for (MenuNodeDTO node : nodes) {
            sink.add(node.code());
            appendCodes(node.children(), sink);
        }
    }
}
