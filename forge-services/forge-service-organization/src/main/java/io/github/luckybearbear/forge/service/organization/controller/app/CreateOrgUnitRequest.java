package io.github.luckybearbear.forge.service.organization.controller.app;

import io.github.luckybearbear.forge.service.organization.domain.OrgUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 组织单元创建请求：parentId 可空（=根）；sort 可空（默认 0）。 */
public record CreateOrgUnitRequest(
        @Size(max = 36) String parentId,
        @NotNull OrgUnitType type,
        @NotBlank @Size(max = 128) String name,
        Integer sort) {}
