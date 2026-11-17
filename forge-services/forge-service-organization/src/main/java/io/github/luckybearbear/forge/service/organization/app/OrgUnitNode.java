package io.github.luckybearbear.forge.service.organization.app;

import io.github.luckybearbear.forge.contract.organization.OrgUnitDTO;
import java.util.List;

/** 树形节点（GET /org-units/tree 响应体）：单元快照 + 有序子节点（(sort, id) 稳定排序）。 */
public record OrgUnitNode(OrgUnitDTO unit, List<OrgUnitNode> children) {}
