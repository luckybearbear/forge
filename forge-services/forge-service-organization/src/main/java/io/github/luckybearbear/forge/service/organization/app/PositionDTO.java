package io.github.luckybearbear.forge.service.organization.app;

/** 岗位 DTO（模块内应用视图；跨模块契约只有组织单元面，岗位无对外快照需求）。 */
public record PositionDTO(String id, String orgUnitId, String name, int sort) {}
