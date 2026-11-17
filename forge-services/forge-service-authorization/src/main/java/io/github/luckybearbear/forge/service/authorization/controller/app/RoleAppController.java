package io.github.luckybearbear.forge.service.authorization.controller.app;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.authorization.app.RoleDTO;
import io.github.luckybearbear.forge.service.authorization.app.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理接口（双端前缀自动前插 /app-api）：/app-api/roles CRUD + PUT /{id}/permissions
 * （全量替换授权）+ PUT /{id}/data-scope（数据权限范围）——作用域为当前认证租户（判别式隔离，
 * 跨租户 id 命不中 → AUTH-0404）。
 *
 * <p>类级 {@code @PreAuthorize("hasAuthority('role:manage')")}：权限点由本服务字典 seed 分配；
 * 方法安全由 forge-security-starter 的 {@code @EnableMethodSecurity} 激活（Task 4 裁定归属）。
 * 控制器薄层：校验 + Result 包装，业务语义全在 {@link RoleService}。
 */
@RestController
@RequestMapping("/roles")
@PreAuthorize("hasAuthority('role:manage')")
public class RoleAppController {

    private final RoleService roleService;

    public RoleAppController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public Result<RoleDTO> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.ok(roleService.create(request.code(), request.name(), request.dataScope()));
    }

    @GetMapping
    public Result<List<RoleDTO>> list() {
        return Result.ok(roleService.findAll());
    }

    @GetMapping("/{id}")
    public Result<RoleDTO> get(@PathVariable String id) {
        return Result.ok(roleService.findById(id));
    }

    @PutMapping("/{id}")
    public Result<RoleDTO> update(@PathVariable String id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.ok(roleService.update(id, request.name(), request.dataScope()));
    }

    @PutMapping("/{id}/data-scope")
    public Result<RoleDTO> updateDataScope(@PathVariable String id,
            @Valid @RequestBody UpdateDataScopeRequest request) {
        return Result.ok(roleService.updateDataScope(id, request.dataScope()));
    }

    @PutMapping("/{id}/permissions")
    public Result<RoleDTO> assignPermissions(@PathVariable String id,
            @Valid @RequestBody AssignRolePermissionsRequest request) {
        return Result.ok(roleService.assignPermissions(id, request.permissionIds()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        roleService.delete(id);
        return Result.ok();
    }
}
