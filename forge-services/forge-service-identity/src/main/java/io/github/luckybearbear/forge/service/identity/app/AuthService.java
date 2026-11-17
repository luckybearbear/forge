package io.github.luckybearbear.forge.service.identity.app;

import io.github.luckybearbear.forge.contract.authorization.UserPermissionsQuery;
import io.github.luckybearbear.forge.contract.identity.UserPasswordChanged;
import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.contract.tenant.TenantErrorCodes;
import io.github.luckybearbear.forge.contract.tenant.TenantQueryService;
import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.auth.ForgeAuthentication;
import io.github.luckybearbear.forge.platform.security.password.PasswordHasher;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserEntity;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserRepository;
import io.github.luckybearbear.forge.service.identity.domain.PlatformAdminEntity;
import io.github.luckybearbear.forge.service.identity.domain.PlatformAdminRepository;
import io.github.luckybearbear.forge.service.identity.domain.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 双端登录编排：app 登录（租户校验→用户查询→密码→权限集→token）/ admin 登录（平台管理员，
 * 全局表直查）/ logout（吊销当前 token）/ me（SecurityContext 快照）/ admin 自助改密
 * （{@link #changeAdminPassword}——种子口令更换通道，旧 token 全吊销）。
 *
 * <p><b>登录上下文管理（@TenantId 判别式与租户上下文）</b>：
 * <ul>
 * <li>app 登录时请求无认证无租户上下文，而 {@code @TenantId} 过滤随<b>会话开启时</b>的
 *     租户上下文解析——用户查询前显式 {@code TenantContextHolder.setTenantId(tenant.id())}，
 *     查询完毕 finally {@code clear()}（TTL 泄漏防护；即便查询抛出也回收）。</li>
 * <li>租户用户查询经 {@link TransactionTemplate}（REQUIRES_NEW + readOnly）执行：新独立事务
 *     开启新会话，保证判别式在 setTenantId <b>之后</b>解析（无 OSIV 时即恢复正确租户）。
 *     但 <b>OSIV 开启时 REQUIRES_NEW 不足以恢复判别式租户</b>（demo 全栈实证：appLogin 恒
 *     IDENT-0401）——消费应用必须关闭 OSIV（{@code spring.jpa.open-in-view=false}，见
 *     docs/architecture/dependency-rules.md 与 demo application.yaml）；Phase 3 评估系统性方案。</li>
 * <li>admin 登录查 platform_admin 全局表（无 @TenantId）——无租户上下文直查即可，不涉及判别式。</li>
 * </ul>
 *
 * <p><b>坏凭据不泄露存在性</b>：用户不存在、密码错误、账号停用三种情形同一 {@code IDENT-0401}
 * 同一文案（defaultMessage 无占位符）——响应不可区分，防用户名枚举；且<b>恒时</b>（Task 4 安全
 * 输入 1）：三路径各执行恰一次等代价 bcrypt（未命中/停用对固定 dummy hash 比较，见
 * {@link #constantTimeVerified}），响应时间同样不可区分。
 *
 * <p><b>权限集装配（预检裁定）</b>：{@link UserPermissionsQuery} 经 {@link ObjectProvider} 注入——
 * service-authorization 未装配（identity 独立运行/测试）时缺省空权限集 + 首次 warn 一次；
 * Task 6 装配后登录 grant 自动携带真实权限点。
 *
 * <p><b>token 语义</b>：app 端 grant=(userId, tenantId, "MEMBER", 权限集)；admin 端
 * grant=(adminId, null, "PLATFORM", [PLATFORM])——tenantId=null 即平台 token（TokenAuthFilter 不填充
 * 租户上下文，2a 行为）；authorities 仅含 {@link #PLATFORM_AUTHORITY}（/admin-api/** 面准入，不含
 * 业务权限点——平台侧细粒度管控属后续演进）。TTL 取 {@code forge.security.token-ttl}（与
 * TokenAuthFilter 滑动续期窗口同源，默认 8h）。
 */
@Service
public class AuthService {

    /** app 端 token 的 userType 标记。 */
    public static final String USER_TYPE_MEMBER = "MEMBER";
    /** admin 端 token 的 userType 标记（平台管理员，无租户）。 */
    public static final String USER_TYPE_PLATFORM = "PLATFORM";
    /**
     * 平台 authority（Task 6 评审 Important-2）：admin 登录 grant 携带——security-starter 默认链以
     * {@code hasAuthority("PLATFORM")} 守 /admin-api/** 面（MEMBER token 403）。仅 admin 端 token 持有。
     */
    public static final String PLATFORM_AUTHORITY = "PLATFORM";

    /**
     * 恒时比较垫底 hash（Task 4 安全输入 1）：{@code "123456"} 的 bcrypt（{bcrypt} 前缀走
     * DelegatingPasswordEncoder 路由）。未命中用户/停用路径对它执行等代价比较——与真实校验
     * 同为一次 bcrypt（~10 cost），三路径耗时不可区分。
     */
    static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$cihGDNl8YWnShl6e/WBE9.iRwpgq6fDshMZXqmtPbX8kAnpCg8uzG";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantQueryService tenantQueryService;
    private final IdentityUserRepository userRepository;
    private final PlatformAdminRepository adminRepository;
    private final PasswordHasher passwordHasher;
    private final TokenStore tokenStore;
    private final ObjectProvider<UserPermissionsQuery> permissionsQueryProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration tokenTtl;
    private final TransactionTemplate tenantReadTx;
    private final AtomicBoolean permissionsAbsentWarned = new AtomicBoolean();

    public AuthService(TenantQueryService tenantQueryService,
                       IdentityUserRepository userRepository,
                       PlatformAdminRepository adminRepository,
                       PasswordHasher passwordHasher,
                       TokenStore tokenStore,
                       ObjectProvider<UserPermissionsQuery> permissionsQueryProvider,
                       ApplicationEventPublisher eventPublisher,
                       PlatformTransactionManager transactionManager,
                       @Value("${forge.security.token-ttl:8h}") Duration tokenTtl) {
        this.tenantQueryService = tenantQueryService;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordHasher = passwordHasher;
        this.tokenStore = tokenStore;
        this.permissionsQueryProvider = permissionsQueryProvider;
        this.eventPublisher = eventPublisher;
        this.tokenTtl = tokenTtl;
        this.tenantReadTx = new TransactionTemplate(transactionManager);
        this.tenantReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenantReadTx.setReadOnly(true);
    }

    /** app 端登录：租户校验（TENANT-0404[404]/TENANT-0423）→用户查询→密码→权限集→token。 */
    public LoginResponse appLogin(AppLoginRequest request) {
        TenantDTO tenant = tenantQueryService.findByCode(request.tenantCode())
                .orElseThrow(() -> BusinessException.of(TenantErrorCodes.TENANT_NOT_FOUND, request.tenantCode())
                        .withHttpStatus(404));
        if (!"ENABLED".equals(tenant.status())) {
            throw BusinessException.of(TenantErrorCodes.TENANT_DISABLED);
        }
        IdentityUserEntity user = findByUsernameUnderTenant(tenant.id(), request.username());
        if (!constantTimeVerified(user != null && user.getStatus() == UserStatus.ENABLED,
                request.password(), user == null ? null : user.getPasswordHash())) {
            // 用户不存在/账号停用/密码错误同码同文案（不泄露存在性）
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        Set<String> authorities = authoritiesOf(tenant.id(), user.getId());
        StoredToken token = tokenStore.create(
                new TokenGrant(user.getId(), tenant.id(), USER_TYPE_MEMBER, authorities), tokenTtl);
        return new LoginResponse(token.token(), token.expiresAt(), user.getId(), tenant.id());
    }

    /** admin 端登录：平台管理员（全局表直查）；grant.tenantId=null（平台 token 不填充租户上下文），
     *  authorities 携带 {@link #PLATFORM_AUTHORITY}（/admin-api/** 面的准入信号，评审 Important-2）。 */
    public LoginResponse adminLogin(AdminLoginRequest request) {
        PlatformAdminEntity admin = adminRepository.findByUsername(request.username()).orElse(null);
        if (!constantTimeVerified(admin != null && admin.getStatus() == UserStatus.ENABLED,
                request.password(), admin == null ? null : admin.getPasswordHash())) {
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        StoredToken token = tokenStore.create(
                new TokenGrant(admin.getId(), null, USER_TYPE_PLATFORM, Set.of(PLATFORM_AUTHORITY)), tokenTtl);
        return new LoginResponse(token.token(), token.expiresAt(), admin.getId(), null);
    }

    /** 登出：吊销当前请求 token（控制器自 Authorization Bearer 提取传入）；token 缺失/空白为无害幂等。 */
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            tokenStore.revoke(rawToken);
        }
    }

    /**
     * admin 自助改密（PUT /admin-api/auth/password，最终评审 Important-2——闭合种子口令 admin/admin123
     * 的默认凭据暴露面）：SecurityContext 取当前 {@link ForgeAuthentication} 的 userId 定位
     * platform_admin（全局表，无判别式）→ 旧密码错 {@code IDENT-0401}（与登录同码同文案，防以改密
     * 通道探测）→ 新密码弱 {@code IDENT-0400}（复用 {@link UserService#requireStrongPassword} 同一
     * 规则）→ 更新 hash → 发布 {@link UserPasswordChanged}（tenantId=null, userId=adminId）——
     * admin 端 grant 的 userId 即 adminId（见 {@link #adminLogin}），security-starter 监听器
     * revokeByUserId 即吊销该管理员全部既有 token（含本次改密所用 token——自助改密后需重登，安全方向）。
     */
    @Transactional
    public void changeAdminPassword(String oldPassword, String newPassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof ForgeAuthentication forge)) {
            // 不可达防线：/admin-api/** 面经 TokenAuthFilter 认证（PLATFORM authority）后才会到达
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        PlatformAdminEntity admin = adminRepository.findById(forge.getUserId()).orElse(null);
        // 恒时同路线（admin 主体未命中也对 DUMMY hash 执行等代价比较，不泄露存在性）
        boolean matched = passwordHasher.matches(oldPassword,
                admin != null ? admin.getPasswordHash() : DUMMY_PASSWORD_HASH);
        if (admin == null || !matched) {
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        UserService.requireStrongPassword(newPassword);
        admin.setPasswordHash(passwordHasher.hash(newPassword));
        eventPublisher.publishEvent(new UserPasswordChanged(null, admin.getId(), Instant.now()));
    }

    /** 当前认证主体快照（/me）：非 ForgeAuthentication（如未认证且被放行到达服务层）返回 null。 */
    public MeResponse currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ForgeAuthentication forge) {
            return new MeResponse(forge.getUserId(), forge.getTenantId(), forge.getUserType(),
                    forge.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toUnmodifiableSet()));
        }
        return null;
    }

    /**
     * 租户上下文内的用户名查询：setTenantId → 查询 → finally clear。
     * 查询走 REQUIRES_NEW 只读事务（新会话保证判别式在 setTenantId 之后解析）；注意 OSIV 开启时
     * 这仍不足以恢复判别式租户——消费应用必须关闭 OSIV（见类 javadoc"登录上下文管理"第 2 条）；
     * 未命中返回 null（与密码错误同路）。
     */
    private IdentityUserEntity findByUsernameUnderTenant(String tenantId, String username) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return tenantReadTx.execute(tx -> userRepository.findByUsername(username).orElse(null));
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 登录恒时比较（Task 4 安全输入 1）：每条路径（未命中/停用/密码错/成功）各执行<b>恰一次</b>
     * 等代价 bcrypt——主体未命中或停用时对固定 {@link #DUMMY_PASSWORD_HASH} 比较（短路 0 次的版本
     * 可经响应时间枚举用户名）。active=false 时比较结果被丢弃（停用账号即便口令正确也拒绝）。
     */
    private boolean constantTimeVerified(boolean active, String rawPassword, String realHash) {
        boolean matched = passwordHasher.matches(rawPassword, active ? realHash : DUMMY_PASSWORD_HASH);
        return active && matched;
    }

    /** 权限集：authorization 服务装配则按 (tenantId, userId) 查询；未装配缺省空集 + 首次 warn。 */
    private Set<String> authoritiesOf(String tenantId, String userId) {
        UserPermissionsQuery permissionsQuery = permissionsQueryProvider.getIfAvailable();
        if (permissionsQuery == null) {
            if (permissionsAbsentWarned.compareAndSet(false, true)) {
                log.warn("No UserPermissionsQuery bean assembled - login grants carry an empty authority set "
                        + "(assemble service-authorization to enable permission-driven grants)");
            }
            return Set.of();
        }
        return Set.copyOf(permissionsQuery.authoritiesOf(tenantId, userId));
    }
}
