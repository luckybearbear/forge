package io.github.luckybearbear.forge.service.organization.controller.app;

import io.github.luckybearbear.forge.contract.organization.OrgUnitDTO;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.service.organization.app.OrgUnitNode;
import io.github.luckybearbear.forge.service.organization.app.OrgUnitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 组织单元接口（双端前缀自动前插 /app-api）：/app-api/org-units CRUD + GET /tree +
 * PUT /{id}/move（环检测）+ GET /{id}/sub-tree。作用域为当前认证租户（判别式天然隔离）。
 * 控制器薄层：校验 + Result 包装，业务语义（错误码/事件/环检测）全在 {@link OrgUnitService}。
 */
@RestController
@RequestMapping("/org-units")
public class OrgUnitAppController {

    private final OrgUnitService orgUnitService;

    public OrgUnitAppController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @PostMapping
    public Result<OrgUnitDTO> create(@Valid @RequestBody CreateOrgUnitRequest request) {
        return Result.ok(orgUnitService.create(request.parentId(), request.type(), request.name(),
                request.sort() == null ? 0 : request.sort()));
    }

    @GetMapping("/{id}")
    public Result<OrgUnitDTO> get(@PathVariable String id) {
        return Result.ok(orgUnitService.findById(id));
    }

    /** 全林树形（嵌套节点，兄弟按 (sort, id) 稳定排序）。 */
    @GetMapping("/tree")
    public Result<List<OrgUnitNode>> tree() {
        return Result.ok(orgUnitService.tree());
    }

    /** 子树快照（根开头先序扁平）。 */
    @GetMapping("/{id}/sub-tree")
    public Result<List<OrgUnitDTO>> subTree(@PathVariable String id) {
        return Result.ok(orgUnitService.subTree(id));
    }

    @PutMapping("/{id}")
    public Result<OrgUnitDTO> update(@PathVariable String id,
            @Valid @RequestBody UpdateOrgUnitRequest request) {
        return Result.ok(orgUnitService.update(id, request.type(), request.name(), request.sort()));
    }

    /** 移动（newParentId 为 null 即提为根；环 → ORG-0409/409）。 */
    @PutMapping("/{id}/move")
    public Result<OrgUnitDTO> move(@PathVariable String id,
            @Valid @RequestBody MoveOrgUnitRequest request) {
        return Result.ok(orgUnitService.move(id, request.parentId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        orgUnitService.delete(id);
        return Result.ok();
    }
}
