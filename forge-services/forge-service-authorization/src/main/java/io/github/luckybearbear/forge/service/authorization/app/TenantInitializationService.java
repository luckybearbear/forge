package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.service.authorization.domain.DataScope;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.RoleEntity;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.RolePermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.RoleRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户授权初始化：默认 ADMIN 角色（code {@value #DEFAULT_ROLE_CODE}，dataScope ALL）+ 字典全量
 * 权限点授权。由 {@link TenantCreatedListener} 消费 TenantCreated 后调用。
 *
 * <p><b>REQUIRES_NEW</b>（identity 登录范式）：listener 异步线程无外层事务，显式独立事务落库
 * ——提交即生效，不随事件发布方（租户创建早已提交）或监听重试纠缠。<b>幂等</b>：重投/回放时
 * 已有 ADMIN 角色与既有授权跳过（findByCode + 已授权集合差集），不产生重复行。
 *
 * <p>平台写守护不触发：listener 已按事件 tenantId 显式 set 租户上下文（非平台视角），
 * 判别式解析为目标租户，租户表写入合法。
 */
@Service
public class TenantInitializationService {

    public static final String DEFAULT_ROLE_CODE = "ADMIN";

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public TenantInitializationService(RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    /** 当前租户上下文内初始化默认角色 + 全量授权（幂等）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializeDefaultAuthorization() {
        RoleEntity adminRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseGet(() -> roleRepository.save(
                        new RoleEntity(DEFAULT_ROLE_CODE, "管理员", DataScope.ALL)));

        Set<String> grantedPermissionIds = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .map(RolePermissionEntity::getPermissionId)
                .collect(Collectors.toSet());
        for (PermissionEntity permission : permissionRepository.findAll()) {
            if (grantedPermissionIds.contains(permission.getId())) {
                continue;
            }
            rolePermissionRepository.save(new RolePermissionEntity(adminRole.getId(), permission.getId()));
        }
    }
}
