package io.github.luckybearbear.forge.service.authorization.controller.app;

import io.github.luckybearbear.forge.service.authorization.domain.DataScope;

/** 部分更新：null 字段不改。 */
public record UpdateRoleRequest(String name, DataScope dataScope) {}
