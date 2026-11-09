package io.github.luckybearbear.forge.demo.maintenance.api;

import io.github.luckybearbear.forge.demo.maintenance.app.MaintenanceService;
import io.github.luckybearbear.forge.platform.core.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenances")
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
