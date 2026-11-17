package io.github.luckybearbear.forge.demo.maintenance.controller.app;

import io.github.luckybearbear.forge.demo.maintenance.api.MaintenanceOrderDTO;
import io.github.luckybearbear.forge.demo.maintenance.app.MaintenanceService;
import io.github.luckybearbear.forge.platform.core.Result;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * app 端维修单接口：包名含 .controller.app. → 自动前插 /app-api（spec §6.5），
 * 实际路径 /app-api/maintenances、/app-api/maintenances/{id}。
 *
 * <p><b>类级 @PreAuthorize（Task 8）</b>：两查询端点同权限（字典 seed 的 maint:read），
 * 故按类声明；方法安全由 forge-security-starter 的 @EnableMethodSecurity 激活。
 */
@RestController
@RequestMapping("/maintenances")
@PreAuthorize("hasAuthority('maint:read')")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/{id}")
    public Result<MaintenanceOrderDTO> get(@PathVariable String id) {
        return Result.ok(maintenanceService.findById(id));
    }

    @GetMapping
    public Result<List<MaintenanceOrderDTO>> list(@RequestParam String moldId) {
        return Result.ok(maintenanceService.findByMoldId(moldId));
    }
}
