package io.github.luckybearbear.forge.service.authorization.app;

import io.github.luckybearbear.forge.contract.authorization.AuthorizationErrorCodes;
import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionEntity;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionRepository;
import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限点字典服务（admin 端 + 菜单查询）：create/update/delete/find/全量 + 可见菜单树
 * {@link #visibleMenus(Set)} + admin 字典树 {@link #tree()}。auth_permission 为全局表——
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
 *
 * <p><b>菜单合一模型（Task 1）</b>：菜单=带路由元数据的权限点。visibleMenus 可见性规则：
 * MENU 按 code∈authorities 过滤（{@link #PLATFORM_AUTHORITY} 超级全通过）；DIR 不参与鉴权，
 * 由后代可见性决定（祖先链自底向上补全，空 DIR 折叠不返回）；BUTTON/API 永不进可见菜单树。
 * tree() 为 admin 字典树：全量含 BUTTON/API（含空 DIR——字典管理需见全部行）。
 * type=DIR 时路由字段可不填——service 不强校验（渲染层约定，见模块 README）。
 */
@Service
public class PermissionService {

    /**
     * 平台超级 authority：authorities 含之即全 MENU 可见（不逐 code 比对）。与 identity 的
     * AuthService.PLATFORM_AUTHORITY 同值同规则（admin 端平台 token grant=[PLATFORM]）——
     * 指令/菜单/链级门禁三处统一常量；前端 v-permission（forge-web Task 3）同规则。
     */
    public static final String PLATFORM_AUTHORITY = "PLATFORM";

    /** 同级排序：sort 升序（null 最后），同序按 code 定序（稳定可重现）。 */
    private static final Comparator<PermissionEntity> CHILD_ORDER =
            Comparator.comparing((PermissionEntity row) -> row.getSort() == null ? Integer.MAX_VALUE : row.getSort())
                    .thenComparing(PermissionEntity::getCode);

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    /**
     * 新增权限点：code 全局唯一（AUTH-0409 覆写文案）；六列菜单元数据可空全收（type=DIR 时
     * 路由字段可不填——不强校验）；visible 缺省 TRUE（对齐列 DEFAULT TRUE）。
     */
    @Transactional
    public PermissionDTO create(String code, String name, PermissionType type, String parentId, String path,
            String component, String icon, Integer sort, Boolean visible) {
        repository.findByCode(code).ifPresent(existing -> {
            throw duplicatedCode(code);
        });
        PermissionEntity entity = new PermissionEntity(code, name, type);
        entity.setParentId(parentId);
        entity.setPath(path);
        entity.setComponent(component);
        entity.setIcon(icon);
        entity.setSort(sort);
        entity.setVisible(visible == null ? Boolean.TRUE : visible);
        return toDto(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<PermissionDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PermissionDTO findById(String id) {
        return toDto(requirePermission(id));
    }

    /** 部分更新：null 字段不改（code 不可改——字典锚点）；parentId 传空串=挂根。 */
    @Transactional
    public PermissionDTO update(String id, String name, PermissionType type, String parentId, String path,
            String component, String icon, Integer sort, Boolean visible) {
        PermissionEntity entity = requirePermission(id);
        if (name != null) {
            entity.setName(name);
        }
        if (type != null) {
            entity.setType(type);
        }
        if (parentId != null) {
            entity.setParentId(parentId);
        }
        if (path != null) {
            entity.setPath(path);
        }
        if (component != null) {
            entity.setComponent(component);
        }
        if (icon != null) {
            entity.setIcon(icon);
        }
        if (sort != null) {
            entity.setSort(sort);
        }
        if (visible != null) {
            entity.setVisible(visible);
        }
        return toDto(entity); // 托管态脏检查随事务提交落库
    }

    /** 软删（悬挂引用语义见类 javadoc）。 */
    @Transactional
    public void delete(String id) {
        PermissionEntity entity = requirePermission(id);
        repository.delete(entity);
    }

    /**
     * 可见菜单树（identity /menus 经契约 {@code MenuQueryService} 消费）：
     * <ol>
     *   <li>rows（字典全量）→ 可见 MENU 集：type=MENU 且（超级或 code∈authorities）</li>
     *   <li>DIR 链补全（自底向上）：沿 parentId 上溯标记祖先——有可见后代的 DIR 可见；
     *       空 DIR 天然不在集内（折叠）；环数据防御（walked 集 + 已可见短路）</li>
     *   <li>树组装：roots=parentId 空/空白（悬挂父按根呈现——数据防御）；children 按
     *       {@link #CHILD_ORDER} 升序稳定排序</li>
     *   <li>MenuNodeDTO 递归（DIR 也返回——菜单树渲染需要容器节点）</li>
     * </ol>
     * BUTTON/API 不进可见树；DIR/BUTTON/API 的 code 出现在 authorities 中不产生节点
     * （鉴权与可见性分离：只有 MENU 型以 code 过滤）。
     */
    @Transactional(readOnly = true)
    public List<MenuNodeDTO> visibleMenus(Set<String> authorities) {
        List<PermissionEntity> rows = repository.findAll();
        boolean platform = authorities.contains(PLATFORM_AUTHORITY);

        Map<String, PermissionEntity> byId = rows.stream()
                .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));
        Set<String> visibleIds = new HashSet<>();
        for (PermissionEntity row : rows) {
            if (row.getType() == PermissionType.MENU && (platform || authorities.contains(row.getCode()))) {
                visibleIds.add(row.getId());
            }
        }

        // DIR 祖先链补全：自每个可见节点上溯；祖先已可见即短路（其链已被此前的上溯补全）
        for (String id : Set.copyOf(visibleIds)) {
            Set<String> walked = new HashSet<>(); // 环防御（parentId 环不致死循环）
            for (String cursor = parentOf(byId, id); cursor != null && walked.add(cursor);
                    cursor = parentOf(byId, cursor)) {
                if (!visibleIds.add(cursor)) {
                    break;
                }
            }
        }

        return assembleTree(byId, visibleIds);
    }

    /** admin 字典树：全量（含 BUTTON/API 与空 DIR——字典管理须见全部行），children 组装同序。 */
    @Transactional(readOnly = true)
    public List<MenuNodeDTO> tree() {
        Map<String, PermissionEntity> byId = repository.findAll().stream()
                .collect(Collectors.toMap(PermissionEntity::getId, Function.identity()));
        return assembleTree(byId, byId.keySet());
    }

    /** 批量校验并返回实体（RoleService.assignPermissions 用）：任一缺失 → AUTH-0414。 */
    @Transactional(readOnly = true)
    public List<PermissionEntity> requireAllByIds(Set<String> permissionIds) {
        List<PermissionEntity> found = repository.findAllById(permissionIds);
        if (found.size() != permissionIds.size()) {
            Set<String> foundIds = found.stream().map(PermissionEntity::getId).collect(Collectors.toSet());
            String missing = permissionIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse("?");
            throw BusinessException.of(AuthorizationErrorCodes.AUTH_PERMISSION_NOT_FOUND, missing);
        }
        return found;
    }

    /** 组装：includedIds 内的行成树——roots=parentId 空/空白或父不在集内（悬挂按根呈现）。 */
    private static List<MenuNodeDTO> assembleTree(Map<String, PermissionEntity> byId, Set<String> includedIds) {
        Map<String, List<PermissionEntity>> childrenByParent = new HashMap<>();
        List<PermissionEntity> roots = new ArrayList<>();
        for (PermissionEntity row : byId.values()) {
            if (!includedIds.contains(row.getId())) {
                continue;
            }
            String parentId = row.getParentId();
            if (isBlank(parentId) || !includedIds.contains(parentId)) {
                roots.add(row);
            } else {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(row);
            }
        }
        return toNodes(roots, childrenByParent);
    }

    private static List<MenuNodeDTO> toNodes(List<PermissionEntity> rows,
            Map<String, List<PermissionEntity>> childrenByParent) {
        return rows.stream()
                .sorted(CHILD_ORDER)
                .map(row -> toNode(row, childrenByParent))
                .toList();
    }

    private static MenuNodeDTO toNode(PermissionEntity row, Map<String, List<PermissionEntity>> childrenByParent) {
        return new MenuNodeDTO(row.getId(), row.getParentId(), row.getType().name(), row.getCode(), row.getName(),
                row.getPath(), row.getComponent(), row.getIcon(), row.getSort(), row.getVisible(),
                toNodes(childrenByParent.getOrDefault(row.getId(), List.of()), childrenByParent));
    }

    private static String parentOf(Map<String, PermissionEntity> byId, String id) {
        PermissionEntity row = byId.get(id);
        return row == null ? null : row.getParentId();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
