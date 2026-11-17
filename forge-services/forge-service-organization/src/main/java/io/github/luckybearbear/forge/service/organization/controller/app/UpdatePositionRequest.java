package io.github.luckybearbear.forge.service.organization.controller.app;

import jakarta.validation.constraints.Size;

/** 岗位更新请求：全部字段可选——null 即不改（orgUnitId 非 null 为换挂靠，须存在 → ORG-0404）。 */
public record UpdatePositionRequest(
        @Size(max = 36) String orgUnitId,
        @Size(max = 128) String name,
        Integer sort) {}
