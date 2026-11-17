package io.github.luckybearbear.forge.service.organization.controller.app;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.organization.app.PositionDTO;
import io.github.luckybearbear.forge.service.organization.app.PositionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位接口（双端前缀自动前插 /app-api）：/app-api/positions CRUD——挂靠组织单元（存在性
 * ORG-0404）、删除保护（有挂岗 ORG-0409）。类级 {@code @PreAuthorize("hasAuthority('org:manage')")}
 * （字典 seed id …0003，未授权 403 FORGE-0403）。控制器薄层：校验 + Result 包装。
 */
@RestController
@RequestMapping("/positions")
@PreAuthorize("hasAuthority('org:manage')")
public class PositionAppController {

    private final PositionService positionService;

    public PositionAppController(PositionService positionService) {
        this.positionService = positionService;
    }

    @PostMapping
    public Result<PositionDTO> create(@Valid @RequestBody CreatePositionRequest request) {
        return Result.ok(positionService.create(request.orgUnitId(), request.name(),
                request.sort() == null ? 0 : request.sort()));
    }

    @GetMapping("/{id}")
    public Result<PositionDTO> get(@PathVariable String id) {
        return Result.ok(positionService.findById(id));
    }

    @GetMapping(params = "orgUnitId")
    public Result<List<PositionDTO>> listByOrgUnit(@RequestParam String orgUnitId) {
        return Result.ok(positionService.findByOrgUnitId(orgUnitId));
    }

    @PutMapping("/{id}")
    public Result<PositionDTO> update(@PathVariable String id,
            @Valid @RequestBody UpdatePositionRequest request) {
        return Result.ok(positionService.update(id, request.orgUnitId(), request.name(), request.sort()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        positionService.delete(id);
        return Result.ok();
    }
}
