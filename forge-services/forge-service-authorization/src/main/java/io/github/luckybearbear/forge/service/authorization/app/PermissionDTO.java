package io.github.luckybearbear.forge.service.authorization.app;

/** 权限点对外视图（全局字典条目）。 */
public record PermissionDTO(String id, String code, String name, String type) {}
