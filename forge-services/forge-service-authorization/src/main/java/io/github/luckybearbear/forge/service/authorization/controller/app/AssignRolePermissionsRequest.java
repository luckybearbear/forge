package io.github.luckybearbear.forge.service.authorization.controller.app;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** 全量替换语义：空集 = 回收该角色全部授权。 */
public record AssignRolePermissionsRequest(@NotNull Set<String> permissionIds) {}
