package io.github.luckybearbear.forge.contract.organization;

import java.util.List;

/** 跨模块同步查询契约：service-organization 实现，返回以给定单元为根的整棵子树快照。 */
public interface OrgTreeQueryService {

    List<OrgUnitDTO> findSubTree(String rootOrgUnitId);
}
