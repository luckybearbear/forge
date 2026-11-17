package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationErrorCodes;
import io.github.luckybearbear.forge.contract.authorization.RolePermissionsChanged;
import io.github.luckybearbear.forge.platform.cache.CacheFacade;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import io.github.luckybearbear.forge.service.authorization.domain.RoleEntity;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.RoleRepository;
import io.github.luckybearbear.forge.service.authorization.domain.UserRoleRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色应用服务（app 端，全部操作于当前租户上下文内——web 链由 TokenAuthFilter 填充）：
 * CRUD（code 租户内唯一 AUTH-0409）、assignPermissions（全量替换 + 发布 RolePermissionsChanged +
 * 按 userId 显式失效权限快照缓存）、dataScope 更新。
 *
 * <p><b>缓存失效策略（程序式，非 @Cacheable）</b>：快照 key {@code auth:perm:{userId}}（经
 * CacheFacade 自动加 {@code t:{tenantId}:} 前缀——失效与写入同在租户上下文，物理键一致）。
 * assignPermissions 先取该角色现有用户（删除前），替换授权后逐一 evict——改权限在 5min TTL
 * 窗口内即时生效。RolePermissionsChanged 事件供域外消费（modulith 登记表），域内失效以显式
 * evict 为准（同事务同步完成，无异步窗口）。
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionService permissionService;
    private final CacheFacade cacheFacade;
    private final ApplicationEventPublisher eventPublisher;

    public RoleService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository,
            UserRoleRepository userRoleRepository, PermissionService permissionService,
            CacheFacade cacheFacade, ApplicationEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionService = permissionService;
        this.cacheFacade = cacheFacade;
        this.eventPublisher = eventPublisher;
    }

    /** 创建角色：code 租户内唯一（AUTH-0409/409）。 */
    @Transactional
    public RoleDTO create(String code, String name, DataScope dataScope) {
        roleRepository.findByCode(code).ifPresent(existing -> {
            throw BusinessException.of(AuthorizationErrorCodes.AUTH_ROLE_CODE_DUPLICATED, code)
                    .withHttpStatus(409);
        });
        return toDto(roleRepository.save(new RoleEntity(code, name, dataScope)));
    }

    @Transactional(readOnly = true)
    public RoleDTO findById(String id) {
        return toDto(requireRole(id));
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> findAll() {
        return roleRepository.findAll().stream().map(this::toDto).toList();
    }

    /** 部分更新：name/dataScope 传 null 即不改。 */
    @Transactional
    public RoleDTO update(String id, String name, DataScope dataScope) {
        RoleEntity entity = requireRole(id);
        if (name != null) {
            entity.setName(name);
        }
        if (dataScope != null) {
            entity.setDataScope(dataScope);
        }
        return toDto(entity);
    }

    /** 数据权限范围更新（独立端点 PUT /{id}/data-scope）。 */
    @Transactional
    public RoleDTO updateDataScope(String id, DataScope dataScope) {
        RoleEntity entity = requireRole(id);
        entity.setDataScope(dataScope);
        return toDto(entity);
    }

    /** 删除（软删）：连带清理本租户内该角色的授权与用户分配（同租户表，判别式可见）。 */
    @Transactional
    public void delete(String id) {
        RoleEntity entity = requireRole(id);
        rolePermissionRepository.deleteByRoleId(id);
        userRoleRepository.deleteByRoleId(id);
        roleRepository.delete(entity);
    }

    /**
     * 全量替换角色授权：任一权限点缺失 AUTH-0414；替换后发布 RolePermissionsChanged（业务事务内，
     * modulith 登记随原子提交）并按持有该角色的 userId 显式 evict 快照缓存（即时失效）。
     */
    @Transactional
    public RoleDTO assignPermissions(String roleId, Set<String> permissionIds) {
        requireRole(roleId);
        permissionService.requireAllByIds(permissionIds);

        // 失效名单先取（deleteByRoleId 后 user_role 不受影响——按角色取用户分配，非授权）
        Set<String> affectedUserIds = userRoleRepository.findByRoleId(roleId).stream()
                .map(userRole -> userRole.getUserId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        rolePermissionRepository.deleteByRoleId(roleId);
        permissionIds.forEach(permissionId ->
                rolePermissionRepository.save(new RolePermissionEntity(roleId, permissionId)));

        eventPublisher.publishEvent(new RolePermissionsChanged(
                TenantContextHolder.getRequiredTenantId(), roleId, Instant.now()));
        affectedUserIds.forEach(userId -> cacheFacade.evict(UserPermissionsQueryImpl.cacheKey(userId)));
        return findById(roleId);
    }

    private RoleEntity requireRole(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(AuthorizationErrorCodes.AUTH_ROLE_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    private RoleDTO toDto(RoleEntity entity) {
        return new RoleDTO(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDataScope().name(), entity.getTenantId());
    }
}
