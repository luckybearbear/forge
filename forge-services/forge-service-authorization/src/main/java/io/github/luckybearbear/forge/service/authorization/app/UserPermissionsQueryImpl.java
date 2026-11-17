package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.authorization.UserPermissionsQuery;
import io.github.luckybearbear.forge.platform.cache.CacheFacade;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 契约 {@link UserPermissionsQuery} 实现：用户在指定租户内的权限点编码全集——identity 登录
 * grant 装配的跨模块入口（栖身 app 包，随 AuthorizationServiceAutoConfiguration 扫描暴露为契约 bean）。
 *
 * <p><b>缓存（程序式，非 @Cacheable）</b>：key {@code auth:perm:{userId}} TTL 5min，经
 * {@link CacheFacade} 落为 {@code t:{tenantId}:auth:perm:{userId}}（快照天然租户隔离）。
 * 值存 {@code List<String>}（Jackson default-typing 往返最稳的集合载体），命中侧统一转
 * {@code Set<String>}。失效由域内写路径显式 evict（RoleService.assignPermissions /
 * UserRoleService.assignToUser）——RolePermissionsChanged 事件之外的即时通道。
 *
 * <p><b>租户上下文与事务（identity 登录范式）</b>：登录链调用时线程无租户上下文——以参数
 * tenantId 显式 set（保存/还原进入前状态），DB 读经 REQUIRES_NEW 只读事务：新独立事务开新会话，
 * 保证 @TenantId 判别式在 setTenantId <b>之后</b>解析（消费应用开 OSIV 时请求级会话早已开启、
 * 判别式已解析为平台租户——REQUIRED 挂其上恒查空，与 AuthService.findByUsernameUnderTenant 同理）。
 */
@Service
public class UserPermissionsQueryImpl implements UserPermissionsQuery {

    /** 权限快照缓存 TTL（写入与失效窗口一致——显式 evict 为主，TTL 兜底）。 */
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RolePermissionRepository rolePermissionRepository;
    private final CacheFacade cacheFacade;
    private final TransactionTemplate requiresNewReadTx;

    public UserPermissionsQueryImpl(RolePermissionRepository rolePermissionRepository,
            CacheFacade cacheFacade, PlatformTransactionManager transactionManager) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.cacheFacade = cacheFacade;
        this.requiresNewReadTx = new TransactionTemplate(transactionManager);
        this.requiresNewReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewReadTx.setReadOnly(true);
    }

    /** 快照逻辑 key（域内写路径失效共用；物理键由 CacheFacade 加租户前缀）。 */
    static String cacheKey(String userId) {
        return "auth:perm:" + userId;
    }

    @Override
    public Set<String> authoritiesOf(String tenantId, String userId) {
        String key = cacheKey(userId);
        Optional<String> previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        TenantContextHolder.setTenantId(tenantId);
        try {
            Optional<Object> cached = cacheFacade.get(key);
            if (cached.isPresent()) {
                return toAuthoritySet(cached.get());
            }
            List<String> authorities = requiresNewReadTx.execute(tx ->
                    rolePermissionRepository.findAuthorityCodes(userId));
            List<String> snapshot = new ArrayList<>(authorities);
            cacheFacade.put(key, snapshot, CACHE_TTL);
            return Set.copyOf(snapshot);
        } finally {
            // 还原进入前状态（登录链无上下文 → clear；既有租户/平台视角链 → 原样恢复）
            TenantContextHolder.clear();
            previousTenant.ifPresent(TenantContextHolder::setTenantId);
            if (previousIgnore) {
                TenantContextHolder.setIgnore(true);
            }
        }
    }

    /** 命中侧归一：序列化载体为 List（或集合演化中的其他 Collection 形态）→ 不可变 Set。 */
    private static Set<String> toAuthoritySet(Object cached) {
        if (cached instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        throw new IllegalStateException("unexpected cached value type: " + cached.getClass());
    }
}
