package io.github.luckybearbear.forge.service.authorization.app;

/** 角色对外视图（dataScope 为数据权限语义字段，枚举名传输）。 */
public record RoleDTO(String id, String code, String name, String dataScope, String tenantId) {}
