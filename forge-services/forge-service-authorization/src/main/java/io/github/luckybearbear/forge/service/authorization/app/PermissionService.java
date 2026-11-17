package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationErrorCodes;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限点字典服务（admin 端）：create/update/delete/find/全量。auth_permission 为全局表——
 * 本服务在平台视角（无租户上下文）下运行即可（admin 端 token 不填租户）。
 *
 * <p><b>错误码语义（FOREIGN_CODES 纪律：不加新码）</b>：
 * <ul>
 *   <li>缺失引用：AUTH-0414（权限点不存在）</li>
 *   <li>编码冲突：复用 AUTH-0409（授权域编码唯一冲突，409）——但 defaultMessage 为角色文案，
 *       故经 {@code BusinessException(errorCode, message)} 覆写为权限点文案（枚举码不复制的代价）</li>
 * </ul>
 *
 * <p><b>delete 语义</b>：直接软删（无引用校验）——auth_role_permission 对已删权限点的悬挂引用在
 * 授权查询侧自然失效（findAuthorityCodes 经 permission 存活行 join）；租户侧重授权时引用新字典行。
 */
@Service
public class PermissionService {

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    /** 新增权限点：code 全局唯一（AUTH-0409 覆写文案）。 */
    @Transactional
    public PermissionDTO create(String code, String name, PermissionType type) {
        repository.findByCode(code).ifPresent(existing -> {
            throw duplicatedCode(code);
        });
        return toDto(repository.save(new PermissionEntity(code, name, type)));
    }

    @Transactional(readOnly = true)
    public List<PermissionDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PermissionDTO findById(String id) {
        return toDto(requirePermission(id));
    }

    /** 部分更新：name/type 传 null 即不改。 */
    @Transactional
    public PermissionDTO update(String id, String name, PermissionType type) {
        PermissionEntity entity = requirePermission(id);
        if (name != null) {
            entity.setName(name);
        }
        if (type != null) {
            entity.setType(type);
        }
        return toDto(entity); // 托管态脏检查随事务提交落库
    }

    /** 软删（悬挂引用语义见类 javadoc）。 */
    @Transactional
    public void delete(String id) {
        PermissionEntity entity = requirePermission(id);
        repository.delete(entity);
    }

    /** 批量校验并返回实体（RoleService.assignPermissions 用）：任一缺失 → AUTH-0414。 */
    @Transactional(readOnly = true)
    public List<PermissionEntity> requireAllByIds(Set<String> permissionIds) {
        List<PermissionEntity> found = repository.findAllById(permissionIds);
        if (found.size() != permissionIds.size()) {
            Set<String> foundIds = found.stream().map(PermissionEntity::getId).collect(java.util.stream.Collectors.toSet());
            String missing = permissionIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse("?");
            throw BusinessException.of(AuthorizationErrorCodes.AUTH_PERMISSION_NOT_FOUND, missing);
        }
        return found;
    }

    private PermissionEntity requirePermission(String id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.of(AuthorizationErrorCodes.AUTH_PERMISSION_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    private static BusinessException duplicatedCode(String code) {
        // AUTH-0409 复用 + 文案覆写（不加新码纪律；enum defaultMessage 为角色文案）
        return new BusinessException(AuthorizationErrorCodes.AUTH_ROLE_CODE_DUPLICATED,
                "权限点编码已存在: " + code).withHttpStatus(409);
    }

    private PermissionDTO toDto(PermissionEntity entity) {
        return new PermissionDTO(entity.getId(), entity.getCode(), entity.getName(), entity.getType().name());
    }
}
