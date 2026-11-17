package io.github.luckybearbear.forge.service.identity.app;

import io.github.luckybearbear.forge.contract.identity.IdentityErrorCodes;
import io.github.luckybearbear.forge.contract.identity.UserCreated;
import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.contract.identity.UserPasswordChanged;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.password.PasswordHasher;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserEntity;
import io.github.luckybearbear.forge.service.identity.domain.IdentityUserRepository;
import io.github.luckybearbear.forge.service.identity.domain.UserStatus;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户用户管理：create（租户内 username 唯一 + 初始密码弱校验 + UserCreated 事件）、
 * changePassword（旧密码校验 + 弱校验 + UserPasswordChanged 事件）、enable/disable、findById。
 *
 * <p><b>租户上下文</b>：全部操作在租户视角下执行（web 请求链由 TokenAuthFilter 填充，测试/异步
 * 侧 TenantTestSupport 式显式 set）——{@code @TenantId} 判别式天然隔离跨租户访问（findById 命不中
 * 他租户 id → IDENT-0404），写入自动落当前租户 tenant_id；平台视角（ignore）写入由平台写守护
 * fail-fast 拒绝（tenant_id='0' 黑洞防护）。
 *
 * <p>事件经 {@link ApplicationEventPublisher} 业务事务内发布（spec §10）。payload 显式携带
 * tenantId：create 侧取当前租户上下文（@TenantId 插入时才绑定，publish 时实体字段尚未回填）；
 * changePassword 侧实体已加载、直接取 {@code getTenantId()}。
 *
 * <p><b>弱密码规则（IDENT-0400）</b>：≥8 位且同时含字母与数字——简单规则先行，复杂度策略
 * （字典/历史密码/自适应）属后续演进；规则对初始密码与改密新密码一致。
 */
@Service
public class UserService {

    private final IdentityUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(IdentityUserRepository repository, PasswordHasher passwordHasher,
                       ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.eventPublisher = eventPublisher;
    }

    /** 创建用户：租户内 username 唯一（IDENT-0409/409）；初始状态 ENABLED；发布 UserCreated。 */
    @Transactional
    public UserDTO create(String username, String phone, String initialPassword) {
        repository.findByUsername(username).ifPresent(existing -> {
            throw BusinessException.of(IdentityErrorCodes.IDENT_USERNAME_DUPLICATED, username).withHttpStatus(409);
        });
        requireStrongPassword(initialPassword);
        IdentityUserEntity saved = repository.save(
                new IdentityUserEntity(username, phone, passwordHasher.hash(initialPassword), UserStatus.ENABLED));
        eventPublisher.publishEvent(new UserCreated(
                TenantContextHolder.getRequiredTenantId(), saved.getId(), saved.getUsername(), Instant.now()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public UserDTO findById(String userId) {
        return toDto(requireUser(userId));
    }

    /**
     * 改密：旧密码错 IDENT-0401（与登录同码——防以改密通道枚举用户/探测密码）；
     * 新密码弱 IDENT-0400；成功后发布 UserPasswordChanged（token/权限缓存侧据此失效既有会话——
     * 既有 token 的吊销由监听方承担，本服务不代持 token 索引）。
     */
    @Transactional
    public UserDTO changePassword(String userId, String oldPassword, String newPassword) {
        IdentityUserEntity user = requireUser(userId);
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw BusinessException.of(IdentityErrorCodes.IDENT_BAD_CREDENTIALS);
        }
        requireStrongPassword(newPassword);
        user.setPasswordHash(passwordHasher.hash(newPassword));
        eventPublisher.publishEvent(new UserPasswordChanged(user.getTenantId(), user.getId(), Instant.now()));
        return toDto(user);
    }

    /** 启用（幂等）：任意状态 → ENABLED。 */
    @Transactional
    public UserDTO enable(String userId) {
        IdentityUserEntity user = requireUser(userId);
        user.setStatus(UserStatus.ENABLED);
        return toDto(user);
    }

    /** 停用（幂等）：任意状态 → DISABLED；停用用户登录按坏凭据拒绝（AuthService，不泄露停用状态）。 */
    @Transactional
    public UserDTO disable(String userId) {
        IdentityUserEntity user = requireUser(userId);
        user.setStatus(UserStatus.DISABLED);
        return toDto(user);
    }

    private IdentityUserEntity requireUser(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> BusinessException.of(IdentityErrorCodes.IDENT_USER_NOT_FOUND, userId)
                        .withHttpStatus(404));
    }

    /** 弱密码校验（IDENT-0400）：≥8 位且同时含字母与数字（简单规则，见类 javadoc）。 */
    private void requireStrongPassword(String password) {
        boolean strong = password != null && password.length() >= 8
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) {
            throw BusinessException.of(IdentityErrorCodes.IDENT_PASSWORD_TOO_WEAK);
        }
    }

    private UserDTO toDto(IdentityUserEntity entity) {
        return new UserDTO(entity.getId(), entity.getTenantId(), entity.getUsername(),
                entity.getPhone(), entity.getStatus().name());
    }
}
