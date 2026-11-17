package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationErrorCodes;
import io.github.luckybearbear.forge.platform.cache.CacheFacade;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.service.authorization.domain.RoleRepository;
import io.github.luckybearbear.forge.service.authorization.domain.UserRoleEntity;
import io.github.luckybearbear.forge.service.authorization.domain.UserRoleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户角色分配服务（app 端，当前租户上下文内）：assignToUser 为<b>全量替换</b>语义
 * （空集 = 回收全部角色）；分配/回收后显式 evict 该 userId 的权限快照缓存——变更在
 * 5min TTL 窗口内即时生效（无 RolePermissionsChanged 联动：分配不改变角色本身的授权面）。
 */
@Service
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final CacheFacade cacheFacade;

    public UserRoleService(UserRoleRepository userRoleRepository, RoleRepository roleRepository,
            CacheFacade cacheFacade) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.cacheFacade = cacheFacade;
    }

    /**
     * 全量替换用户的角色集：任一角色不存在（本租户判别式下）→ AUTH-0404；随后 evict 快照。
     *
     * <p><b>evict 时序注记（cache-aside 竞态）</b>：evict 在业务事务内、DB 写后执行（先写后逐出）——
     * 并发读在「读旧库 → 本事务提交+evict 之后」回填缓存的窗口可短暂重填陈旧快照（经典竞态）；
     * TTL 5min 有界兜底，强一致失效（双删/版本化 key）属后续演进。
     */
    @Transactional
    public Set<String> assignToUser(String userId, Set<String> roleIds) {
        Set<String> missing = roleIds.stream()
                .filter(roleId -> roleRepository.findById(roleId).isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!missing.isEmpty()) {
            throw BusinessException.of(AuthorizationErrorCodes.AUTH_ROLE_NOT_FOUND, missing.iterator().next())
                    .withHttpStatus(404);
        }

        userRoleRepository.deleteByUserId(userId);
        roleIds.forEach(roleId -> userRoleRepository.save(new UserRoleEntity(userId, roleId)));

        cacheFacade.evict(UserPermissionsQueryImpl.cacheKey(userId));
        return roleIdsOf(userId);
    }

    /** 用户当前角色 id 集（本租户）。 */
    @Transactional(readOnly = true)
    public Set<String> roleIdsOf(String userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

}
