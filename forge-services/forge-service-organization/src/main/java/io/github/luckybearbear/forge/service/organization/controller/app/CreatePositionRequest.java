package io.github.luckybearbear.forge.service.organization.controller.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 岗位创建请求：挂靠组织单元（须存在于本租户 → ORG-0404）；sort 可空（默认 0）。 */
public record CreatePositionRequest(
        @NotBlank @Size(max = 36) String orgUnitId,
        @NotBlank @Size(max = 128) String name,
        Integer sort) {}
