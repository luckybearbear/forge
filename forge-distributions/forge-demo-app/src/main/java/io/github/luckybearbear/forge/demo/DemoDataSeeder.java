package io.github.luckybearbear.forge.demo;

import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.service.authorization.app.RoleDTO;
import io.github.luckybearbear.forge.service.authorization.app.RoleService;
import io.github.luckybearbear.forge.service.authorization.app.TenantInitializationService;
import io.github.luckybearbear.forge.service.authorization.app.UserRoleService;
import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import io.github.luckybearbear.forge.service.identity.app.AppLoginRequest;
import io.github.luckybearbear.forge.service.identity.app.AuthService;
import io.github.luckybearbear.forge.service.identity.app.LoginResponse;
import io.github.luckybearbear.forge.service.identity.app.UserService;
import io.github.luckybearbear.forge.service.tenant.app.TenantService;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo 种子数据（@Profile({"demo","test"})，Task 8）：认证全链路体验的初始数据——
 * 经<b>服务 API/契约</b>铺底（不走跨域 SQL，与生产链路同一语义）：
 *
 * <ul>
 *   <li>两租户：t-mfg（精密制造）/ t-mold（模具车间），创建即 INIT → 显式 enable（登录要求
 *       ENABLED）；幂等：findByCode 命中即跳过创建。</li>
 *   <li>每租户一管理员：mfg-admin / mold-admin（口令 {@link #ADMIN_PASSWORD}，bcrypt 经
 *       identity 的 UserService）。TenantCreated 事件已由 authorization 的
 *       TenantCreatedListener 异步建好 ADMIN 角色（全权限）——本 seeder 重试等待该角色就绪后
 *       经 UserRoleService 绑定（幂等：已持有即跳过）。</li>
 *   <li>t-mold 受限用户 mold-viewer（口令 {@link #VIEWER_PASSWORD}）：自建 VIEWER 角色，仅授
 *       权限字典 seed 的 mold:read——演示 @PreAuthorize 403 与「授权变更后重登生效」语义。</li>
 * </ul>
 *
 * <p><b>异步就绪等待</b>：TenantCreatedListener 是 @ApplicationModuleListener（事务提交后异步
 * 投递），ADMIN 角色落库晚于 TenantService.create 返回——按角色 code 轮询（≤
 * {@link #ROLE_WAIT}，200ms 间隔；main 代码不引 Awaitility，手写重试）。</p>
 *
 * <p><b>权限快照语义（重要）</b>：登录时权限集进 TokenGrant 快照，且 authorization 侧权限快照
 * 缓存（auth:perm:{userId}，TTL 5min）在授权变更时被显式 evict——<b>改权限后重新登录即取新集</b>；
 * 既有 token 的快照冻结至过期/登出，Phase 3 才引入 token 内权限刷新。DemoAuthFlowIntegrationTest
 * 场景 5 断言的正是这一边界。</p>
 *
 * <p>固定权限 id（权限字典 seed，V202611181300 迁移）直接以常量引用：字典行由迁移保证在场，
 * 消费方无需经 admin 端点反查。</p>
 *
 * <p><b>栖身根包而非 config/ 子包（实证裁定）</b>：Modulith 把根包的每个直接子包视为模块——
 * config/ 会成为第三模块，ModulithBoundaryTest.containsExactlyInAnyOrder("mold","maintenance")
 * 失败实证；主包根级的类不属于任何模块（与 ForgeDemoApplication 同级），边界测试不受影响。</p>
 */
@Component
@Profile({"demo", "test"})
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String MFG_TENANT_CODE = "t-mfg";
    static final String MOLD_TENANT_CODE = "t-mold";
    static final String ADMIN_PASSWORD = "Admin@123";
    static final String VIEWER_PASSWORD = "Viewer@123";
    static final String VIEWER_ROLE_CODE = "VIEWER";

    /**
     * 权限字典 seed 固定 id（service-authorization V202611181300）：mold:read。包私有共享
     * （评审 Minor-6）：DemoAuthFlowIntegrationTest 引用同一常量（场景 5 授权变更/基线恢复用），
     * 单一来源防双份硬编码漂移。
     */
    static final String MOLD_READ_PERMISSION_ID = "0194b480-0000-7000-8000-000000000005";

    /** TenantCreatedListener 异步建 ADMIN 角色的等待上限（轮询间隔 200ms）。 */
    private static final Duration ROLE_WAIT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MS = 200;

    private final TenantService tenantService;
    private final UserService userService;
    private final AuthService authService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;

    public DemoDataSeeder(TenantService tenantService, UserService userService, AuthService authService,
            RoleService roleService, UserRoleService userRoleService) {
        this.tenantService = tenantService;
        this.userService = userService;
        this.authService = authService;
        this.roleService = roleService;
        this.userRoleService = userRoleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantDTO mfg = ensureTenant(MFG_TENANT_CODE, "精密制造");
        TenantDTO mold = ensureTenant(MOLD_TENANT_CODE, "模具车间");
        ensureAdmin(mfg, "mfg-admin");
        ensureAdmin(mold, "mold-admin");
        ensureViewer(mold);
        log.info("demo seed ready: tenants [t-mfg(mfg-admin), t-mold(mold-admin, mold-viewer)]");
    }

    /** 租户幂等铺底：findByCode 命中即复用；缺失则 create + enable（登录要求 ENABLED）。 */
    private TenantDTO ensureTenant(String code, String name) {
        try {
            TenantDTO existing = tenantService.findByCode(code);
            return tenantService.enable(existing.id()); // 幂等（已 ENABLED 无副作用）
        } catch (BusinessException ex) {
            if (!TenantErrorCodes.TENANT_NOT_FOUND.code().equals(ex.errorCode().code())) {
                throw ex;
            }
            TenantDTO created = tenantService.create(code, name, null, null);
            return tenantService.enable(created.id());
        }
    }

    /** 管理员幂等铺底：建用户 → 等 ADMIN 角色异步就绪 → 绑定（已持有即跳过）。返回 userId。 */
    private String ensureAdmin(TenantDTO tenant, String username) {
        String userId = ensureUser(tenant, username, ADMIN_PASSWORD);
        String adminRoleId = awaitRoleByCode(tenant.id(), TenantInitializationService.DEFAULT_ROLE_CODE);
        ensureUserRole(tenant.id(), userId, adminRoleId);
        return userId;
    }

    /** 受限用户铺底：VIEWER 角色（仅 mold:read，全量替换幂等）+ mold-viewer 绑定。 */
    private void ensureViewer(TenantDTO tenant) {
        String viewerRoleId = inTenant(tenant.id(), () -> {
            String existing = roleIdByCode(tenant.id(), VIEWER_ROLE_CODE);
            String roleId = existing != null ? existing
                    : roleService.create(VIEWER_ROLE_CODE, "只读查看", DataScope.SELF).id();
            roleService.assignPermissions(roleId, Set.of(MOLD_READ_PERMISSION_ID)); // 全量替换幂等
            return roleId;
        });
        String userId = ensureUser(tenant, "mold-viewer", VIEWER_PASSWORD);
        ensureUserRole(tenant.id(), userId, viewerRoleId);
    }

    /** 用户幂等铺底：不存在则建（口令 bcrypt）；已存在（IDENT-0409）经登录定位 userId 并顺带验活口令。 */
    private String ensureUser(TenantDTO tenant, String username, String password) {
        return inTenant(tenant.id(), () -> {
            try {
                return userService.create(username, null, password).id();
            } catch (BusinessException ex) {
                if (!IdentityErrorCodes.IDENT_USERNAME_DUPLICATED.code().equals(ex.errorCode().code())) {
                    throw ex;
                }
                LoginResponse login = authService.appLogin(
                        new AppLoginRequest(tenant.code(), username, password));
                return login.userId();
            }
        });
    }

    /** 角色绑定幂等：已持有即跳过（全量替换语义下重复 assign 也幂等，先查可少一次写+evict）。 */
    private void ensureUserRole(String tenantId, String userId, String roleId) {
        inTenant(tenantId, () -> {
            if (!userRoleService.roleIdsOf(userId).contains(roleId)) {
                userRoleService.assignToUser(userId, Set.of(roleId));
            }
            return null;
        });
    }

    /**
     * 轮询等待租户内指定 code 的角色出现（TenantCreatedListener 异步建 ADMIN 角色的就绪等待，
     * ≤{@value #ROLE_WAIT}；重投/慢投超时按种子失败 fail-fast——profile 门控下宁可启动失败
     * 也不留半种子状态静默通过）。
     */
    private String awaitRoleByCode(String tenantId, String code) {
        long deadlineNanos = System.nanoTime() + ROLE_WAIT.toNanos();
        String roleId;
        while ((roleId = roleIdByCode(tenantId, code)) == null) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new IllegalStateException(
                        "demo seed: role " + code + " not ready in tenant " + tenantId
                                + " within " + ROLE_WAIT + " (TenantCreatedListener async?)");
            }
            sleepQuietly();
        }
        return roleId;
    }

    /** 租户上下文内按 code 找角色（找不到返回 null，供轮询/幂等判断）。 */
    private String roleIdByCode(String tenantId, String code) {
        return inTenant(tenantId, () -> roleService.findAll().stream()
                .filter(role -> role.code().equals(code))
                .map(RoleDTO::id)
                .findFirst()
                .orElse(null));
    }

    /**
     * 租户上下文包裹（seeder 运行线程无登录态）：set → action → finally 还原进入前状态。
     * 还原而非无条件 clear（可重入）：ensureViewer 内嵌 roleIdByCode→inTenant，内层 clear
     * 会抹掉外层租户（实证：assignPermissions 落入无上下文 → getRequiredTenantId 失败）。
     */
    private <T> T inTenant(String tenantId, Supplier<T> action) {
        String previous = TenantContextHolder.getTenantId().orElse(null);
        TenantContextHolder.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
            if (previous != null) {
                TenantContextHolder.setTenantId(previous);
            }
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("demo seed: interrupted while awaiting async role setup", ex);
        }
    }
}
