package io.github.luckybearbear.forge.service.organization.controller.app;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.organization.app.UserPositionDTO;
import io.github.luckybearbear.forge.service.organization.app.UserPositionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户挂岗接口（双端前缀自动前插 /app-api）：POST /app-api/user-positions 挂岗（用户经
 * UserQueryService 契约校验→IDENT-0404；岗位 ORG-0414）、DELETE ?userId=&positionId= 摘岗
 * （幂等）、GET ?userId= 按用户查岗。类级 {@code @PreAuthorize("hasAuthority('org:manage')")}
 * （字典 seed id …0003，未授权 403 FORGE-0403）。控制器薄层：校验 + Result 包装。
 */
@RestController
@RequestMapping("/user-positions")
@PreAuthorize("hasAuthority('org:manage')")
public class UserPositionAppController {

    private final UserPositionService userPositionService;

    public UserPositionAppController(UserPositionService userPositionService) {
        this.userPositionService = userPositionService;
    }

    @PostMapping
    public Result<UserPositionDTO> assign(@Valid @RequestBody AssignUserPositionRequest request) {
        return Result.ok(userPositionService.assign(request.userId(), request.positionId()));
    }

    @GetMapping(params = "userId")
    public Result<List<UserPositionDTO>> listByUser(@RequestParam String userId) {
        return Result.ok(userPositionService.findByUserId(userId));
    }

    /** 摘岗（幂等）：按 (userId, positionId) 组合定位——不暴露内部行 id。 */
    @DeleteMapping
    public Result<Void> unassign(@RequestParam String userId, @RequestParam String positionId) {
        userPositionService.unassign(userId, positionId);
        return Result.ok();
    }
}
