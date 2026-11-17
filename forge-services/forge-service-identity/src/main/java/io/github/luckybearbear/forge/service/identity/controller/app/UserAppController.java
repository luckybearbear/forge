package io.github.luckybearbear.forge.service.identity.controller.app;

import io.github.luckybearbear.forge.contract.identity.UserDTO;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.identity.app.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户用户管理接口（双端前缀自动前插 /app-api）：/app-api/users CRUD——创建/改密/启停/查询，
 * 作用域为当前认证租户（TokenAuthFilter 填充的租户上下文，判别式天然隔离）。
 *
 * <p>类级 {@code @PreAuthorize("hasAuthority('user:manage')")}：权限点由 service-authorization
 * 分配（权限点 seed 见 Task 6）；方法安全由 IdentityServiceAutoConfiguration 的
 * {@code @EnableMethodSecurity} 激活，未授权访问输出 403（FORGE-0403 统一体）。
 * 控制器薄层：校验 + Result 包装，业务语义（错误码/事件）全在 {@link UserService}。
 */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('user:manage')")
public class UserAppController {

    private final UserService userService;

    public UserAppController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public Result<UserDTO> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request.username(), request.phone(), request.password()));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> get(@PathVariable String id) {
        return Result.ok(userService.findById(id));
    }

    @PutMapping("/{id}/password")
    public Result<UserDTO> changePassword(@PathVariable String id,
            @Valid @RequestBody ChangePasswordRequest request) {
        return Result.ok(userService.changePassword(id, request.oldPassword(), request.newPassword()));
    }

    @PostMapping("/{id}/disable")
    public Result<UserDTO> disable(@PathVariable String id) {
        return Result.ok(userService.disable(id));
    }

    @PostMapping("/{id}/enable")
    public Result<UserDTO> enable(@PathVariable String id) {
        return Result.ok(userService.enable(id));
    }
}
