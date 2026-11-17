package io.github.luckybearbear.forge.identity.login;

import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.contract.identity.UserCreated;
import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.contract.identity.UserPasswordChanged;
import io.github.luckybearbear.forge.contract.identity.UserQueryService;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantErrorCodes;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import io.github.luckybearbear.forge.service.identity.IdentityServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.identity.app.AdminLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AppLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.app.LoginResponse;
import io.github.luckybearbear.forge.service.identity.app.UserService;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserRepository;
import io.github.luckybearbear.forge.starter.security.RedisTokenStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * service-identity 集成测试（真实 PG+Redis 容器，service 方法级——不经 HTTP）：双端登录/登出/
 * 改密全语义、坏凭据不泄露存在性、租户判别式隔离、契约 UserQueryService 租户视角、
 * UserPermissionsQuery 缺省空集（本上下文无该 bean——装配侧见 IdentityServicePermissionsGrantTest）。
 *
 * <p>TenantQueryService 为内存桩（identity 不依赖 service-tenant，契约消费即可）；token 生命周期
 * 走 security-starter 的 RedisTokenStore（真实 redis 容器，@ServiceConnection 直连）。
 */
@SpringBootTest(classes = IdentityServiceIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(IdentityServiceAutoConfiguration.class)
class IdentityServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String TENANT_ACME = "t-acme";
    private static final String TENANT_OTHER = "t-other";

    /** 测试包位于服务根包（io.github...service.identity）之外的独立分支（...forge.identity.login）：
     * 本服务自动配置以服务根包做 @ComponentScan——若测试类栖身该包（tenant 单测试类范式），本模块
     * 第二个测试上下文（IdentityServicePermissionsGrantTest）经 @Import 自动配置时会把主测试的
     * TestApp（本身即 @Configuration）当作扫描发现一并注册，桩 bean 交叉泄漏/按名冲突
     * （BeanDefinitionOverrideException 实证）。独立分支包 + @Bean 桩注册使各上下文只见自己的桩。
     * 服务 bean 装配不受影响（@Import(IdentityServiceAutoConfiguration) 显式引入，imports 文件双路径同 tenant 范式）。 */
    @SpringBootApplication
    static class TestApp {

        @Bean
        StubTenantQueryService stubTenantQueryService() {
            return new StubTenantQueryService();
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    /** 租户契约桩：状态可控（ENABLED/DISABLED/缺失 → TENANT-0423/不存在）。 */
    static class StubTenantQueryService implements TenantQueryService {

        final Map<String, TenantDTO> tenantsByCode = new ConcurrentHashMap<>();

        void register(TenantDTO tenant) {
            tenantsByCode.put(tenant.code(), tenant);
        }

        @Override
        public Optional<TenantDTO> findById(String tenantId) {
            return tenantsByCode.values().stream().filter(t -> t.id().equals(tenantId)).findFirst();
        }

        @Override
        public Optional<TenantDTO> findByCode(String code) {
            return Optional.ofNullable(tenantsByCode.get(code));
        }
    }

    /** 契约事件记录器（同步 @EventListener，同 tenant 范式；@Bean 注册见 TestApp）。 */
    static class EventRecorder {

        final List<UserCreated> created = new CopyOnWriteArrayList<>();
        final List<UserPasswordChanged> passwordChanged = new CopyOnWriteArrayList<>();

        @EventListener
        void on(UserCreated event) {
            created.add(event);
        }

        @EventListener
        void on(UserPasswordChanged event) {
            passwordChanged.add(event);
        }
    }

    @Autowired AuthService authService;
    @Autowired UserService userService;
    @Autowired UserQueryService userQueryService; // 契约 bean（模块根包 IdentityUserQueryServiceImpl）
    @Autowired IdentityUserRepository userRepository;
    @Autowired TokenStore tokenStore;
    @Autowired StubTenantQueryService tenants;
    @Autowired EventRecorder recorder;

    private static TenantDTO tenant(String id, String code, String status) {
        return new TenantDTO(id, code, code + " 公司", null, status, null);
    }

    private static AppLoginRequest appLogin(String tenantCode, String username, String password) {
        return new AppLoginRequest(tenantCode, username, password);
    }

    @Test
    void appLoginIssuesTenantTokenWithGrantSemantics() {
        // security-starter Redis 栈在类路径 → 必须装配 RedisTokenStore（真实容器，非 InMemory 兜底）
        assertThat(tokenStore).isInstanceOf(RedisTokenStore.class);

        tenants.register(tenant(TENANT_ACME, "acme", "ENABLED"));
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("alice", "13800000000", "Passw0rd123"));

        // 登录调用线程无租户上下文（未认证）——service 内部显式 set/finally clear
        assertThat(TenantContextHolder.getTenantId()).isEmpty();
        LoginResponse response = authService.appLogin(appLogin("acme", "alice", "Passw0rd123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.userId()).isEqualTo(created.id());
        assertThat(response.tenantId()).isEqualTo(TENANT_ACME);
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());

        // token 可 resolve，grant 双端语义：app = (userId, tenantId, MEMBER, 权限集)
        Optional<StoredToken> resolved = tokenStore.resolve(response.token());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().grant().userId()).isEqualTo(created.id());
        assertThat(resolved.get().grant().tenantId()).isEqualTo(TENANT_ACME);
        assertThat(resolved.get().grant().userType()).isEqualTo(AuthService.USER_TYPE_MEMBER);
        // 本上下文无 UserPermissionsQuery bean → 缺省空集 + warn 一次（预检裁定）
        assertThat(resolved.get().grant().authorities()).isEmpty();

        // finally clear 卫生：登录编排后 TTL 上下文无残留（租户 id 与 ignore 均净）
        assertThat(TenantContextHolder.getTenantId()).isEmpty();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void appLoginRejectsUnknownTenantWithTenant0404And404() {
        assertThatThrownBy(() -> authService.appLogin(appLogin("ghost", "alice", "Passw0rd123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(TenantErrorCodes.TENANT_NOT_FOUND);
                    assertThat(be.errorCode().code()).isEqualTo("TENANT-0404");
                    assertThat(be.httpStatus()).isEqualTo(404);
                })
                .hasMessageContaining("ghost");
    }

    @Test
    void appLoginRejectsDisabledTenantWithTenant0423() {
        tenants.register(tenant("t-stopped", "stopped", "DISABLED"));
        TenantTestSupport.runAs("t-stopped", () ->
                userService.create("sealed", null, "Passw0rd123"));

        assertThatThrownBy(() -> authService.appLogin(appLogin("stopped", "sealed", "Passw0rd123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(TenantErrorCodes.TENANT_DISABLED))
                .hasMessageContaining("租户已停用");
    }

    /** 不泄露存在性：用户不存在/密码错误/账号停用 → 同码 IDENT-0401 同文案（防用户名枚举）。 */
    @Test
    void appLoginBadCredentialsShareIdenticalIdent0401WithoutExistenceLeak() {
        tenants.register(tenant(TENANT_ACME, "leak", "ENABLED"));
        TenantTestSupport.runAs(TENANT_ACME, () -> userService.create("known", null, "Passw0rd123"));

        BusinessException unknownUser = catchLogin(appLogin("leak", "nobody", "Passw0rd123"));
        BusinessException wrongPassword = catchLogin(appLogin("leak", "known", "WrongPass1x"));

        TenantTestSupport.runAs(TENANT_ACME, () -> userService.disable(
                userRepository.findByUsername("known").orElseThrow().getId()));
        BusinessException disabledUser = catchLogin(appLogin("leak", "known", "Passw0rd123"));

        for (BusinessException ex : List.of(unknownUser, wrongPassword, disabledUser)) {
            assertThat(ex.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
            assertThat(ex.errorCode().code()).isEqualTo("IDENT-0401");
            assertThat(ex.httpStatus()).isNull(); // 默认 200——由统一 Result 体承载错误码
            assertThat(ex.getMessage()).isEqualTo("用户名或密码错误");
        }
        assertThat(unknownUser.getMessage()).isEqualTo(wrongPassword.getMessage())
                .isEqualTo(disabledUser.getMessage());
    }

    @Test
    void adminLoginIssuesPlatformTokenWithNullTenantGrant() {
        // 种子管理员（Flyway：admin/admin123，README 记初始口令）
        LoginResponse response = authService.adminLogin(new AdminLoginRequest("admin", "admin123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.tenantId()).isNull();
        assertThat(response.expiresAt()).isAfter(java.time.Instant.now());

        // grant：admin 端 = (adminId, null, PLATFORM, 空集)——tenantId=null 即平台 token
        Optional<StoredToken> resolved = tokenStore.resolve(response.token());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().grant().tenantId()).isNull();
        assertThat(resolved.get().grant().userType()).isEqualTo(AuthService.USER_TYPE_PLATFORM);
        assertThat(resolved.get().grant().authorities()).isEmpty();

        // admin 侧同样不泄露存在性：坏密码/未知管理员同码同文案
        assertThat(catchAdminLogin("admin", "wrong-pass").getMessage())
                .isEqualTo(catchAdminLogin("ghost-admin", "admin123").getMessage())
                .isEqualTo("用户名或密码错误");
    }

    @Test
    void logoutRevokesTokenSoResolveBecomesEmpty() {
        tenants.register(tenant(TENANT_ACME, "bye", "ENABLED"));
        TenantTestSupport.runAs(TENANT_ACME, () -> userService.create("byebye", null, "Passw0rd123"));
        LoginResponse response = authService.appLogin(appLogin("bye", "byebye", "Passw0rd123"));
        assertThat(tokenStore.resolve(response.token())).isPresent();

        authService.logout(response.token()); // 控制器自 Authorization Bearer 提取 token 传入

        assertThat(tokenStore.resolve(response.token())).isEmpty();
    }

    @Test
    void changePasswordRejectsWrongOldPasswordWithIdent0401() {
        tenants.register(tenant(TENANT_ACME, "pwd-old", "ENABLED"));
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("oldpass", null, "Passw0rd123"));

        BusinessException ex = TenantTestSupport.runAsResult(TENANT_ACME, () ->
                catchChangePassword(created.id(), "WrongOld1x", "NewPass456x"));
        assertThat(ex.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        assertThat(ex.errorCode().code()).isEqualTo("IDENT-0401");
    }

    @Test
    void changePasswordRejectsWeakNewPasswordWithIdent0400() {
        tenants.register(tenant(TENANT_ACME, "pwd-weak", "ENABLED"));
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("weaknew", null, "Passw0rd123"));

        // 旧密码正确但新密码弱：太短 / 无数字 / 无字母 —— 均 IDENT-0400
        for (String weak : new String[]{"Ab1_9xy", "abcdefgh", "12345678"}) {
            BusinessException ex = TenantTestSupport.runAsResult(TENANT_ACME, () ->
                    catchChangePassword(created.id(), "Passw0rd123", weak));
            assertThat(ex.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_PASSWORD_TOO_WEAK);
            assertThat(ex.errorCode().code()).isEqualTo("IDENT-0400");
        }
    }

    @Test
    void changePasswordPublishesEventAndOldPasswordStopsWorking() {
        tenants.register(tenant(TENANT_ACME, "pwd-ok", "ENABLED"));
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("rotate", null, "Passw0rd123"));
        assertThat(recorder.passwordChanged.stream().filter(e -> e.userId().equals(created.id())))
                .isEmpty();

        UserDTO changed = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.changePassword(created.id(), "Passw0rd123", "NewPass456x"));
        assertThat(changed.id()).isEqualTo(created.id());

        // UserPasswordChanged 事件（业务事务内发布，payload 显式 tenantId/userId）
        List<UserPasswordChanged> events = recorder.passwordChanged.stream()
                .filter(e -> e.userId().equals(created.id())).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().tenantId()).isEqualTo(TENANT_ACME);
        assertThat(events.getFirst().occurredAt()).isNotNull();

        // 旧密码失败、新密码可登录
        assertThat(catchLogin(appLogin("pwd-ok", "rotate", "Passw0rd123")).errorCode())
                .isEqualTo(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        assertThat(authService.appLogin(appLogin("pwd-ok", "rotate", "NewPass456x")).userId())
                .isEqualTo(created.id());
    }

    @Test
    void createUserDuplicateUsernameRejectedWithIdent0409AndSingleEvent() {
        tenants.register(tenant(TENANT_ACME, "dup", "ENABLED"));
        UserDTO first = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("carol", "13900000000", "Passw0rd123"));

        BusinessException ex = TenantTestSupport.runAsResult(TENANT_ACME, () -> {
            try {
                userService.create("carol", null, "Passw0rd123");
                return null;
            } catch (BusinessException be) {
                return be;
            }
        });
        assertThat(ex).isNotNull();
        assertThat(ex.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_USERNAME_DUPLICATED);
        assertThat(ex.errorCode().code()).isEqualTo("IDENT-0409");
        assertThat(ex.httpStatus()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("carol");

        // 第二次 create 未成功：仅一条 UserCreated（且字段齐备）
        List<UserCreated> events = recorder.created.stream()
                .filter(e -> e.username().equals("carol")).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().tenantId()).isEqualTo(TENANT_ACME);
        assertThat(events.getFirst().userId()).isEqualTo(first.id());
        assertThat(events.getFirst().occurredAt()).isNotNull();

        // 跨租户可重名（唯一性是 (tenant_id, username) 复合）
        tenants.register(tenant(TENANT_OTHER, "dup-other", "ENABLED"));
        UserDTO other = TenantTestSupport.runAsResult(TENANT_OTHER,
                () -> userService.create("carol", null, "Passw0rd123"));
        assertThat(other.id()).isNotEqualTo(first.id());
    }

    @Test
    void createUserRejectsWeakInitialPasswordWithIdent0400() {
        tenants.register(tenant(TENANT_ACME, "weak-init", "ENABLED"));
        BusinessException ex = TenantTestSupport.runAsResult(TENANT_ACME, () -> {
            try {
                userService.create("weakling", null, "short1");
                return null;
            } catch (BusinessException be) {
                return be;
            }
        });
        assertThat(ex.errorCode()).isEqualTo(IdentityErrorCodes.IDENT_PASSWORD_TOO_WEAK);
        assertThat(recorder.created.stream().filter(e -> e.username().equals("weakling"))).isEmpty();
    }

    /** 契约 UserQueryService 为租户视角：本租户命中、他租户/无上下文（平台视角）恒空（判别式隔离）。 */
    @Test
    void userQueryContractIsTenantScoped() {
        tenants.register(tenant(TENANT_ACME, "scope", "ENABLED"));
        tenants.register(tenant(TENANT_OTHER, "scope-other", "ENABLED"));
        UserDTO created = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userService.create("scoped", "13700000000", "Passw0rd123"));

        Optional<UserDTO> own = TenantTestSupport.runAsResult(TENANT_ACME,
                () -> userQueryService.findById(created.id()));
        assertThat(own).contains(new UserDTO(created.id(), TENANT_ACME, "scoped", "13700000000", "ENABLED"));

        Optional<UserDTO> foreign = TenantTestSupport.runAsResult(TENANT_OTHER,
                () -> userQueryService.findById(created.id()));
        assertThat(foreign).isEmpty(); // 他租户视角不可见（同 username 唯一索引也按 (tenant_id, username)）

        assertThat(userQueryService.findById(created.id())).isEmpty(); // 无上下文=平台视角，租户表恒空
    }

    private BusinessException catchLogin(AppLoginRequest request) {
        try {
            authService.appLogin(request);
            throw new AssertionError("expected BusinessException");
        } catch (BusinessException ex) {
            return ex;
        }
    }

    private BusinessException catchAdminLogin(String username, String password) {
        try {
            authService.adminLogin(new AdminLoginRequest(username, password));
            throw new AssertionError("expected BusinessException");
        } catch (BusinessException ex) {
            return ex;
        }
    }

    private BusinessException catchChangePassword(String userId, String oldPassword, String newPassword) {
        try {
            userService.changePassword(userId, oldPassword, newPassword);
            throw new AssertionError("expected BusinessException");
        } catch (BusinessException ex) {
            return ex;
        }
    }
}
