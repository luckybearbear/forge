package io.github.luckybearbear.forge.service.authorization.controller.admin;

import io.github.luckybearbear.forge.contract.authorization.MenuNodeDTO;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.authorization.app.PermissionDTO;
import io.github.luckybearbear.forge.service.authorization.app.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点字典接口（双端前缀自动前插 /admin-api）：/admin-api/permissions CRUD + 字典树。
 * auth_permission 为全局表——平台管理员（PLATFORM token，grant 无租户）直查直写。
 *
 * <p>无 @PreAuthorize：admin 端平台 token 恒空权限集（grant 语义），门禁是"认证即平台侧"
 * （SecurityFilterChain authenticated）；租户侧 role:manage 门禁在 /app-api 面。
 * 控制器薄层：业务语义全在 {@link PermissionService}。
 *
 * <p><b>GET 列表默认树形</b>（菜单切片 Task 1）：返回 {@code tree()} 全量字典树（含
 * BUTTON/API 与空 DIR——字典管理视角），节点即 {@link MenuNodeDTO}（与 /menus 可见菜单树
 * 同构）。可见性过滤树（MENU/DIR）在租户侧 GET /app-api/auth/menus（identity 经契约消费）。
 */
@RestController
@RequestMapping("/permissions")
public class PermissionAdminController {

    private final PermissionService permissionService;

    public PermissionAdminController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    public Result<PermissionDTO> create(@Valid @RequestBody CreatePermissionRequest request) {
        return Result.ok(permissionService.create(request.code(), request.name(), request.type(),
                request.parentId(), request.path(), request.component(), request.icon(), request.sort(),
                request.visible()));
    }

    /** 字典树（默认树形，全量含 BUTTON/API——children 按 sort 升序）。 */
    @GetMapping
    public Result<List<MenuNodeDTO>> list() {
        return Result.ok(permissionService.tree());
    }

    @GetMapping("/{id}")
    public Result<PermissionDTO> get(@PathVariable String id) {
        return Result.ok(permissionService.findById(id));
    }

    @PutMapping("/{id}")
    public Result<PermissionDTO> update(@PathVariable String id,
            @Valid @RequestBody UpdatePermissionRequest request) {
        return Result.ok(permissionService.update(id, request.name(), request.type(), request.parentId(),
                request.path(), request.component(), request.icon(), request.sort(), request.visible()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        permissionService.delete(id);
        return Result.ok();
    }
}
