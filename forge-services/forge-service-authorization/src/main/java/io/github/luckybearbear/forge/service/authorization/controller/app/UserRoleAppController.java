package io.github.luckybearbear.forge.service.authorization.controller.app;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.authorization.app.UserRoleService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色分配接口（双端前缀自动前插 /app-api）：PUT /app-api/users/{userId}/roles（全量替换，
 * 空集 = 回收）+ GET 查询当前角色集。userId 逻辑引用 identity 用户（无跨域耦合）。
 *
 * <p>类级 {@code @PreAuthorize("hasAuthority('role:manage')")}：角色分配属授权管理面（而非用户
 * 资料管理 user:manage）；方法安全由 forge-security-starter 激活。控制器薄层。
 */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('role:manage')")
public class UserRoleAppController {

    private final UserRoleService userRoleService;

    public UserRoleAppController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @PutMapping("/{userId}/roles")
    public Result<Set<String>> assignRoles(@PathVariable String userId,
            @Valid @RequestBody AssignUserRolesRequest request) {
        return Result.ok(userRoleService.assignToUser(userId, request.roleIds()));
    }

    @GetMapping("/{userId}/roles")
    public Result<Set<String>> rolesOf(@PathVariable String userId) {
        return Result.ok(userRoleService.roleIdsOf(userId));
    }
}
