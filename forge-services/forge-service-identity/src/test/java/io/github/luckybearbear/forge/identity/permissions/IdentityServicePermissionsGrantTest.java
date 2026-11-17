package io.github.luckybearbear.forge.identity.permissions;

import io.github.luckybearbear.forge.contract.authorization.UserPermissionsQuery;
import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import io.github.luckybearbear.forge.service.identity.IdentityServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.identity.app.AppLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.app.LoginResponse;
import io.github.luckybearbear.forge.service.identity.app.UserService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserPermissionsQuery 装配侧登录实证（与 IdentityServiceIntegrationTest 互补）：
 * authorization 服务在场时（Task 6 起真实装配；此处桩化），登录 grant 携带按
 * (tenantId, userId) 查得的权限点集合——ObjectProvider 注入在 bean 存在/缺失两态下均按预检裁定工作。
 *
 * <p><b>包位置为何在 service 根包之外</b>（io.github...forge.identity.permissions，主测试类同理在
 * ...identity.login）：本服务的自动配置以 {@code io.github.luckybearbear.forge.service.identity}
 * 为根做 @ComponentScan——若测试类栖身该包，其 TestApp（本身即 @Configuration）与嵌套桩会被
 * 本模块其他测试上下文经自动配置扫描一并注册（BeanDefinitionOverrideException/桩交叉泄漏实证），
 * UserPermissionsQuery 桩泄入主上下文更会破坏"缺省空集"用例。独立分支包 + 桩经 @Bean 注册
 * （@Bean 不被组件扫描发现）使各上下文只见自己的桩；服务 bean 经
 * @Import(IdentityServiceAutoConfiguration) 装配，不受测试包位置影响。
 */
@SpringBootTest(classes = IdentityServicePermissionsGrantTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(IdentityServiceAutoConfiguration.class)
class IdentityServicePermissionsGrantTest extends AbstractIntegrationTest {

    private static final String TENANT_ID = "t-grant";

    @SpringBootApplication
    static class TestApp {

        @Bean
        StubTenantQueryService stubTenantQueryService() {
            return new StubTenantQueryService();
        }

        @Bean
        StubPermissionsQuery stubPermissionsQuery() {
            return new StubPermissionsQuery();
        }
    }

    static class StubTenantQueryService implements TenantQueryService {

        private final Map<String, TenantDTO> tenantsByCode = new ConcurrentHashMap<>(
                Map.of("granted", new TenantDTO(TENANT_ID, "granted", "授权租户", null, "ENABLED", null)));

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

        @Override
        public Set<String> authoritiesOf(String tenantId, String userId) {
            return Set.of("user:manage", "mold:read");
        }
    }

    @Autowired AuthService authService;
    @Autowired UserService userService;
    @Autowired TokenStore tokenStore;

    @Test
    void loginGrantCarriesPermissionAuthorities() {
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ID,
                () -> userService.create("granted", null, "Passw0rd123"));

        LoginResponse response = authService.appLogin(
                new AppLoginRequest("granted", "granted", "Passw0rd123"));

        assertThat(tokenStore.resolve(response.token()))
                .hasValueSatisfying(stored -> {
                    assertThat(stored.grant().authorities())
                            .containsExactlyInAnyOrder("user:manage", "mold:read");
                    assertThat(stored.grant().tenantId()).isEqualTo(TENANT_ID);
                    assertThat(stored.grant().userId()).isEqualTo(created.id());
                    assertThat(stored.grant().userType()).isEqualTo(AuthService.USER_TYPE_MEMBER);
                });
    }
}
