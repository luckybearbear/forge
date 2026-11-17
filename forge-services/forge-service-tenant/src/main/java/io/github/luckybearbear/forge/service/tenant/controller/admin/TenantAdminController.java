package io.github.luckybearbear.forge.service.tenant.controller.admin;

import io.github.luckybearbear.forge.contract.tenant.TenantDTO;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.tenant.app.TenantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户管理端接口：包名含 {@code .controller.admin.} → platform-web 双端前缀映射自动前插
 * {@code /admin-api}（spec §6.5）——实际路径 /admin-api/tenants、/admin-api/tenants/{id}、
 * /admin-api/tenants?code=、/admin-api/tenants/{id}/disable|enable。
 *
 * <p>控制器保持薄层：参数校验 + Result 包装，业务语义（含错误码/事件）全在 {@link TenantService}。
 */
@RestController
@RequestMapping("/tenants")
public class TenantAdminController {

    private final TenantService tenantService;

    public TenantAdminController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public Result<TenantDTO> create(@Valid @RequestBody CreateTenantRequest request) {
        return Result.ok(tenantService.create(request.code(), request.name(),
                request.packageId(), request.expiredAt()));
    }

    @GetMapping("/{id}")
    public Result<TenantDTO> get(@PathVariable String id) {
        return Result.ok(tenantService.findById(id));
    }

    @GetMapping(params = "code")
    public Result<TenantDTO> getByCode(@RequestParam String code) {
        return Result.ok(tenantService.findByCode(code));
    }

    @PutMapping("/{id}")
    public Result<TenantDTO> update(@PathVariable String id, @Valid @RequestBody UpdateTenantRequest request) {
        return Result.ok(tenantService.update(id, request.name(), request.packageId(), request.expiredAt()));
    }

    @PostMapping("/{id}/disable")
    public Result<TenantDTO> disable(@PathVariable String id) {
        return Result.ok(tenantService.disable(id));
    }

    @PostMapping("/{id}/enable")
    public Result<TenantDTO> enable(@PathVariable String id) {
        return Result.ok(tenantService.enable(id));
    }
}
