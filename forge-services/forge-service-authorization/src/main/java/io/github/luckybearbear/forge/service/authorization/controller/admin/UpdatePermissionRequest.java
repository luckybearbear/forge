package io.github.luckybearbear.forge.service.authorization.controller.admin;

import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;

/** 部分更新：null 字段不改（code 不可改——字典锚点）。 */
public record UpdatePermissionRequest(String name, PermissionType type) {}
