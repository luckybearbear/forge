package io.github.luckybearbear.forge.service.organization.controller.app;

import jakarta.validation.constraints.Size;

/** 组织单元移动请求：parentId 为 null 即提为根（非 null 须存在，环 → ORG-0409/409）。 */
public record MoveOrgUnitRequest(@Size(max = 36) String parentId) {}
