package io.github.luckybearbear.forge.service.authorization.controller.admin;

import io.github.luckybearbear.forge.service.authorization.domain.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增权限点请求：code/name/type 必填；六列菜单元数据可空（type=DIR 时路由字段可不填——
 * service 不强校验，渲染层约定见模块 README；visible 缺省 TRUE）。
 */
public record CreatePermissionRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull PermissionType type,
        String parentId,
        String path,
        String component,
        String icon,
        Integer sort,
        Boolean visible) {}
