package io.github.luckybearbear.forge.service.authorization.controller.app;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** 全量替换语义：空集 = 回收该用户全部角色。 */
public record AssignUserRolesRequest(@NotNull Set<String> roleIds) {}
