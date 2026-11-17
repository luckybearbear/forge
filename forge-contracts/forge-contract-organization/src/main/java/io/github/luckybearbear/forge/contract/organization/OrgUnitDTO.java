package io.github.luckybearbear.forge.contract.organization;

/** 组织单元快照 DTO，跨模块同步查询用。 */
public record OrgUnitDTO(String id, String parentId, String type, String name, int sort) {}
