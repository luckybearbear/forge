package io.github.luckybearbear.forge.service.tenant.controller.admin;

import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.tenant.app.TenantPackageDTO;
import io.github.luckybearbear.forge.service.tenant.app.TenantPackageService;
import jakarta.validation.Valid;
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
 * 套餐管理端接口（双端前缀自动前插 /admin-api）：/admin-api/tenant-packages CRUD。
 * 删除为软删（编码随之可复用）；控制器薄层约定同 {@link TenantAdminController}。
 */
@RestController
@RequestMapping("/tenant-packages")
public class TenantPackageAdminController {

    private final TenantPackageService tenantPackageService;

    public TenantPackageAdminController(TenantPackageService tenantPackageService) {
        this.tenantPackageService = tenantPackageService;
    }

    @PostMapping
    public Result<TenantPackageDTO> create(@Valid @RequestBody CreateTenantPackageRequest request) {
        return Result.ok(tenantPackageService.create(request.code(), request.name(),
                request.maxUsers(), request.remark()));
    }

    @GetMapping("/{id}")
    public Result<TenantPackageDTO> get(@PathVariable String id) {
        return Result.ok(tenantPackageService.findById(id));
    }

    @GetMapping(params = "code")
    public Result<TenantPackageDTO> getByCode(@RequestParam String code) {
        return Result.ok(tenantPackageService.findByCodeRequired(code));
    }

    @PutMapping("/{id}")
    public Result<TenantPackageDTO> update(@PathVariable String id,
            @Valid @RequestBody UpdateTenantPackageRequest request) {
        return Result.ok(tenantPackageService.update(id, request.name(), request.maxUsers(), request.remark()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        tenantPackageService.delete(id);
        return Result.ok();
    }
}
