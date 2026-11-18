package io.github.luckybearbear.forge.authorization.flow;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationErrorCodes;
import io.github.luckybearbear.forge.contract.authorization.RolePermissionsChanged;
import io.github.luckybearbear.forge.contract.authorization.UserPermissionsQuery;
import io.github.luckybearbear.forge.contract.tenant.TenantCreated;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import io.github.luckybearbear.forge.service.authorization.AuthorizationServiceAutoConfiguration;
import io.github.luckybearbear.forge.service.authorization.app.PermissionDTO;
import io.github.luckybearbear.forge.service.authorization.app.PermissionService;
import io.github.luckybearbear.forge.service.authorization.app.RoleDTO;
import io.github.luckybearbear.forge.service.authorization.app.RoleService;
import io.github.luckybearbear.forge.service.authorization.app.TenantInitializationService;
import io.github.luckybearbear.forge.service.authorization.app.UserPermissionsQueryImpl;
import io.github.luckybearbear.forge.service.authorization.app.UserRoleService;
import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.RoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * service-authorization 集成测试（真实 PG+Redis 容器，service 方法级——不经 HTTP）：
 * 角色 CRUD/编码唯一/数据权限持久化、权限点字典、用户角色分配、权限快照缓存（命中-陈旧-即时失效
 * 三段证据：直接改库后 TTL 内仍读快照；assignPermissions/分配角色后即时反映）、TenantCreated
 * 异步建默认 ADMIN 角色（两租户互不可见 + 幂等）、双租户隔离（角色/分配/快照物理键）。
 *
 * <p><b>包位置在服务根包之外</b>（...forge.authorization.flow，identity 双测试类范式）：本服务自动
 * 配置以 {@code ...forge.service.authorization} 为根做 @ComponentScan——测试类栖身该包会令
 * TestApp/嵌套桩被扫描注册并交叉泄漏到其他上下文。服务 bean 经
 * @Import(AuthorizationServiceAutoConfiguration) 装配（imports 文件双路径同范式）。
 *
 * <p><b>TenantCreated 经事务内发布</b>：@ApplicationModuleListener 是 AFTER_COMMIT 语义
 * （TransactionalEventListener fallbackExecution=false——无事务发布直接丢弃），测试以
 * TransactionTemplate 包裹 publishEvent（与生产侧 TenantService.create 事务内发布同构）；
 * 异步消费以 Awaitility 有界等待（demo 范式）。
 */
@SpringBootTest(classes = AuthorizationServiceIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AuthorizationServiceAutoConfiguration.class)
class AuthorizationServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String T1 = "t-auth-1";
    private static final String T2 = "t-auth-2";

    @SpringBootApplication
    static class TestApp {

        /** 契约事件记录器（同步 @EventListener）：@Bean 注册（identity 范式）——不依赖包扫描拾取。 */
        @org.springframework.context.annotation.Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    static class EventRecorder {

        final List<RolePermissionsChanged> rolePermissionsChanged = new CopyOnWriteArrayList<>();

        @EventListener
        void on(RolePermissionsChanged event) {
            rolePermissionsChanged.add(event);
        }
    }

    @Autowired RoleService roleService;
    @Autowired UserRoleService userRoleService;
    @Autowired PermissionService permissionService;
    @Autowired UserPermissionsQuery userPermissionsQuery; // 契约 bean
    @Autowired RoleRepository roleRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate rawRedis; // 原始 key 断言
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EventRecorder recorder;

    private Set<String> seedIds(String... codes) {
        return java.util.Arrays.stream(codes)
                .map(code -> permissionRepository.findByCode(code).orElseThrow().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void publishInTransaction(Object event) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    /** 角色 CRUD：编码租户内唯一（AUTH-0409）、部分更新、数据权限字段 DB 往返、软删后编码复用。 */
    @Test
    void roleCrudWithTenantScopedCodeUniquenessAndDataScopePersistence() {
        RoleDTO created = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("MOLD_KEEPER", "模具保管员", DataScope.DEPARTMENT));
        assertThat(created.id()).isNotBlank();
        assertThat(created.code()).isEqualTo("MOLD_KEEPER");
        assertThat(created.dataScope()).isEqualTo("DEPARTMENT");

        // 编码重复 → AUTH-0409/409（同租户）
        TenantTestSupport.runAs(T1, () -> assertThatThrownBy(() ->
                        roleService.create("MOLD_KEEPER", "第二名", DataScope.SELF))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(AuthorizationErrorCodes.AUTH_ROLE_CODE_DUPLICATED);
                    assertThat(((BusinessException) ex).errorCode().code()).isEqualTo("AUTH-0409");
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining("MOLD_KEEPER"));

        // 他租户可用同码（唯一性是 (tenant_id, code) 复合）
        RoleDTO otherTenant = TenantTestSupport.runAsResult(T2, () ->
                roleService.create("MOLD_KEEPER", "别家保管员", DataScope.SELF));
        assertThat(otherTenant.id()).isNotEqualTo(created.id());

        // 数据权限字段独立端点更新 + DB 往返（readOnly 事务真查库）
        TenantTestSupport.runAs(T1, () ->
                roleService.updateDataScope(created.id(), DataScope.DEPT_TREE));
        assertThat(TenantTestSupport.runAsResult(T1, () -> roleService.findById(created.id())).dataScope())
                .isEqualTo("DEPT_TREE");

        // 部分更新（name 改、dataScope null 不动）
        assertThat(TenantTestSupport.runAsResult(T1, () ->
                roleService.update(created.id(), "模具保管员2.0", null)).name()).isEqualTo("模具保管员2.0");
        assertThat(TenantTestSupport.runAsResult(T1, () ->
                roleService.findById(created.id())).dataScope()).isEqualTo("DEPT_TREE");

        // 软删（连带清授权/分配）→ 编码复用；查无此角色 → AUTH-0404/404
        TenantTestSupport.runAs(T1, () -> roleService.delete(created.id()));
        String deletedId = created.id();
        TenantTestSupport.runAs(T1, () -> assertThatThrownBy(() -> roleService.findById(deletedId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(deletedId));
        RoleDTO reborn = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("MOLD_KEEPER", "编码复用角色", DataScope.ALL));
        assertThat(reborn.id()).isNotEqualTo(deletedId);
        // 他租户软删不波及（T2 的同码角色仍在）
        assertThat(TenantTestSupport.runAsResult(T2, () ->
                roleService.findById(otherTenant.id())).id()).isEqualTo(otherTenant.id());
    }

    /** 权限点字典：seed 7 项在场、创建/重复 AUTH-0409（覆写文案）/缺失 AUTH-0414。 */
    @Test
    void permissionDictionaryCrudWithSeeds() {
        assertThat(TenantTestSupport.runAsResult(T1, () -> permissionService.findAll()))
                .extracting(PermissionDTO::code)
                .contains("user:manage", "role:manage", "org:manage",
                        "mold:create", "mold:read", "mold:repair", "maint:read");

        PermissionDTO created = permissionService.create("report:export", "报表导出", PermissionType.API,
                null, null, null, null, null, null);
        assertThat(permissionService.findById(created.id()).name()).isEqualTo("报表导出");

        // 重复编码：AUTH-0409 复用 + 权限点文案覆写（FOREIGN_CODES 不加新码）
        assertThatThrownBy(() -> permissionService.create("report:export", "重复", PermissionType.API,
                null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode().code()).isEqualTo("AUTH-0409");
                    assertThat(((BusinessException) ex).httpStatus()).isEqualTo(409);
                })
                .hasMessageContaining("权限点编码已存在");

        // 缺失引用：AUTH-0414
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(T1, () ->
                        roleService.assignPermissions("no-such-role", Set.of(created.id()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no-such-role");
        RoleDTO role = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("DICT_ROLE", "字典校验角色", DataScope.TENANT));
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(T1, () ->
                        roleService.assignPermissions(role.id(), Set.of(created.id(), "no-such-perm"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().code()).isEqualTo("AUTH-0414"))
                .hasMessageContaining("no-such-perm");
    }

    /**
     * 缓存三段证据（快照 key {@code t:{tenantId}:auth:perm:{userId}}，TTL 5min）：
     * ①首次查询落缓存（原始键存在）②TTL 内二次查询不触库（直接删库行后仍返回快照=命中）
     * ③RolePermissionsChanged 域内失效（assignPermissions 后即时反映新授权面）。
     */
    @Test
    void permissionSnapshotCacheHitStalenessAndImmediateInvalidation() {
        RoleDTO role = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("CACHE_ROLE", "缓存证据角色", DataScope.TENANT));
        Set<String> moldRead = seedIds("mold:read");
        Set<String> userManage = seedIds("user:manage");
        String userId = "u-cache-proof";

        TenantTestSupport.runAs(T1, () -> {
            roleService.assignPermissions(role.id(), moldRead);
            userRoleService.assignToUser(userId, Set.of(role.id()));
        });

        // ① 未命中 → 查库 → 落缓存（原始键经 redis-cli 式断言）
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("mold:read");
        Awaitility.await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(rawRedis.hasKey("t:" + T1 + ":auth:perm:" + userId)).isTrue());

        // ② 命中：绕过服务直接删光该角色授权行（无 evict）→ TTL 内仍返回快照（不再触库）
        TenantTestSupport.runAs(T1, () ->
                rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(role.id())));
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("mold:read");

        // ③ 即时失效：assignPermissions 换授权面（发布 RolePermissionsChanged + 显式 evict）
        recorder.rolePermissionsChanged.clear();
        TenantTestSupport.runAs(T1, () -> roleService.assignPermissions(role.id(), userManage));
        assertThat(recorder.rolePermissionsChanged)
                .anySatisfy(event -> {
                    assertThat(event.tenantId()).isEqualTo(T1);
                    assertThat(event.roleId()).isEqualTo(role.id());
                    assertThat(event.occurredAt()).isNotNull();
                });
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("user:manage");

        // 角色分配变更同样即时生效（assignToUser evict）：换绑新角色 → 新授权面
        RoleDTO second = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("CACHE_ROLE_2", "第二角色", DataScope.SELF));
        Set<String> moldCreate = seedIds("mold:create");
        TenantTestSupport.runAs(T1, () -> roleService.assignPermissions(second.id(), moldCreate));
        TenantTestSupport.runAs(T1, () -> userRoleService.assignToUser(userId, Set.of(second.id())));
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("mold:create");

        // 回收（空集）→ 快照即时清空
        TenantTestSupport.runAs(T1, () -> userRoleService.assignToUser(userId, Set.of()));
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId)).isEmpty();
    }

    /** 用户角色分配：生效/未知角色 AUTH-0404/回收；契约 bean 为本模块实现。 */
    @Test
    void userRoleAssignmentDrivesContractQuery() {
        assertThat(userPermissionsQuery).isInstanceOf(UserPermissionsQueryImpl.class);

        RoleDTO role = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("ASSIGN_ROLE", "分配角色", DataScope.DEPARTMENT));
        TenantTestSupport.runAs(T1, () ->
                roleService.assignPermissions(role.id(), seedIds("user:manage", "mold:repair")));
        String userId = "u-assign";

        assertThat(TenantTestSupport.runAsResult(T1, () ->
                userRoleService.assignToUser(userId, Set.of(role.id())))).containsExactly(role.id());
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("user:manage", "mold:repair");

        // 未知角色（本租户判别式下命不中）→ AUTH-0404
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(T1, () ->
                        userRoleService.assignToUser(userId, Set.of("no-such-role"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().code()).isEqualTo("AUTH-0404"))
                .hasMessageContaining("no-such-role");
    }

    /**
     * TenantCreated 异步建默认 ADMIN 角色：全量权限点授权 + dataScope ALL；两租户互不可见
     * （同码不同行）；重投幂等（不重复建角/授权）。
     */
    @Test
    void tenantCreatedInitializesDefaultAdminRolePerTenantIdempotently() {
        // 字典为全局共享（他测试可能追加条目）——以测试时刻字典规模为"全量"基准
        int dictionarySize = permissionRepository.findAll().size();
        publishInTransaction(new TenantCreated(T1, "acme", "Acme 公司", Instant.now()));
        String roleId = awaitDefaultAdminRole(T1);
        assertThat(TenantTestSupport.runAsResult(T1, () ->
                rolePermissionRepository.findByRoleId(roleId))).hasSize(dictionarySize); // 字典全量

        publishInTransaction(new TenantCreated(T2, "globex", "Globex 公司", Instant.now()));
        String otherRoleId = awaitDefaultAdminRole(T2);
        assertThat(otherRoleId).isNotEqualTo(roleId); // 租户各持一行（同码 ADMIN，判别式互不可见）

        // 互不可见：T1 视角只见自己的 ADMIN
        assertThat(TenantTestSupport.runAsResult(T1, () ->
                roleRepository.findByCode(TenantInitializationService.DEFAULT_ROLE_CODE)))
                .hasValueSatisfying(role -> {
                    assertThat(role.getId()).isEqualTo(roleId);
                    assertThat(role.getDataScope()).isEqualTo(DataScope.ALL);
                });

        // 幂等：同租户重投 → 仍一行 ADMIN、授权不重复（差集跳过）
        publishInTransaction(new TenantCreated(T1, "acme", "Acme 公司", Instant.now()));
        Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(TenantTestSupport.runAsResult(T1, () ->
                        rolePermissionRepository.findByRoleId(roleId))).hasSize(dictionarySize));
        assertThat(TenantTestSupport.runAsResult(T1, () -> roleRepository
                .findByCode(TenantInitializationService.DEFAULT_ROLE_CODE))).isPresent();
        assertThat(TenantTestSupport.runAsResult(T1, () ->
                roleRepository.findAll())).extracting("code")
                .filteredOn("ADMIN"::equals).hasSize(1);
    }

    /**
     * 删角色即时失效快照（评审 Important-1）：用户双角色快照缓存后删除其一——被删角色权限即时
     * 不再出现（不残留至 TTL），另一角色权限保留。
     */
    @Test
    void deletingRoleEvictsAffectedUserSnapshotsImmediately() {
        String userId = "u-role-delete";
        RoleDTO doomed = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("DOOMED_ROLE", "待删角色", DataScope.TENANT));
        RoleDTO survivor = TenantTestSupport.runAsResult(T1, () ->
                roleService.create("SURVIVOR_ROLE", "保留角色", DataScope.SELF));
        TenantTestSupport.runAs(T1, () -> {
            roleService.assignPermissions(doomed.id(), seedIds("mold:read"));
            roleService.assignPermissions(survivor.id(), seedIds("mold:create"));
            userRoleService.assignToUser(userId, Set.of(doomed.id(), survivor.id()));
        });

        // 快照已缓存（含两角色权限点）
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("mold:read", "mold:create");

        // 删除其一 → evict 生效：即时只余保留角色权限（未删的快照残留 = Important-1 缺陷）
        TenantTestSupport.runAs(T1, () -> roleService.delete(doomed.id()));
        assertThat(userPermissionsQuery.authoritiesOf(T1, userId))
                .containsExactlyInAnyOrder("mold:create");

        // 角色已软删：本租户再查 AUTH-0404
        String deletedId = doomed.id();
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(T1, () -> roleService.findById(deletedId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(deletedId);
    }

    /** 双租户隔离：快照物理键 t:{tenantId}:auth:perm:{userId}——同 userId 两租户各自成快照。 */
    @Test
    void snapshotKeysArePhysicallyTenantIsolated() {
        String sharedUserId = "u-shared-across-tenants";
        RoleDTO first = TenantTestSupport.runAsResult(T1, () -> {
            RoleDTO role = roleService.create("ISO_ROLE", "隔离角色", DataScope.TENANT);
            roleService.assignPermissions(role.id(), seedIds("user:manage"));
            userRoleService.assignToUser(sharedUserId, Set.of(role.id()));
            return role;
        });
        RoleDTO second = TenantTestSupport.runAsResult(T2, () -> {
            RoleDTO role = roleService.create("ISO_ROLE", "别家隔离角色", DataScope.SELF);
            roleService.assignPermissions(role.id(), seedIds("mold:create"));
            userRoleService.assignToUser(sharedUserId, Set.of(role.id()));
            return role;
        });

        // 同 userId：两租户各自查询互不串味
        assertThat(userPermissionsQuery.authoritiesOf(T1, sharedUserId))
                .containsExactlyInAnyOrder("user:manage");
        assertThat(userPermissionsQuery.authoritiesOf(T2, sharedUserId))
                .containsExactlyInAnyOrder("mold:create");

        // 两份快照物理共存（各自租户前缀）
        Awaitility.await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(rawRedis.hasKey("t:" + T1 + ":auth:perm:" + sharedUserId)).isTrue();
            assertThat(rawRedis.hasKey("t:" + T2 + ":auth:perm:" + sharedUserId)).isTrue();
        });

        // T1 失效只影响 T1 快照
        TenantTestSupport.runAs(T1, () ->
                roleService.assignPermissions(first.id(), Set.of()));
        assertThat(userPermissionsQuery.authoritiesOf(T1, sharedUserId)).isEmpty();
        assertThat(userPermissionsQuery.authoritiesOf(T2, sharedUserId))
                .containsExactlyInAnyOrder("mold:create");

        // 角色可见性：T1 查 T2 的角色 id → AUTH-0404（判别式隔离）
        String foreignRoleId = second.id();
        assertThatThrownBy(() -> TenantTestSupport.runAsResult(T1, () ->
                        roleService.findById(foreignRoleId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(foreignRoleId);
    }

    private String awaitDefaultAdminRole(String tenantId) {
        var roleId = new java.util.concurrent.atomic.AtomicReference<String>();
        Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                roleId.set(TenantTestSupport.runAsResult(tenantId, () -> roleRepository
                        .findByCode(TenantInitializationService.DEFAULT_ROLE_CODE)
                        .orElseThrow(() -> new IllegalStateException("ADMIN role not initialized yet"))
                        .getId())));
        return roleId.get();
    }
}
