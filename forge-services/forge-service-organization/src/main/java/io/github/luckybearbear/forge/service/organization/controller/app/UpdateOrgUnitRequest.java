package io.github.luckybearbear.forge.service.organization.controller.app;

import io.github.luckybearbear.forge.service.organization.domain.OrgUnitType;
import jakarta.validation.constraints.Size;

/** 组织单元更新请求：全部字段可选——null 即不改（部分更新语义；父变更走 move）。 */
public record UpdateOrgUnitRequest(
        OrgUnitType type,
        @Size(max = 128) String name,
        Integer sort) {}
