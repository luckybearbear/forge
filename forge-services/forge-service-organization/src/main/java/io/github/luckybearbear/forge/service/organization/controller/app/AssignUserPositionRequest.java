package io.github.luckybearbear.forge.service.organization.controller.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 挂岗请求：userId 经 contract-identity UserQueryService 校验（缺失 IDENT-0404/404）。 */
public record AssignUserPositionRequest(
        @NotBlank @Size(max = 36) String userId,
        @NotBlank @Size(max = 36) String positionId) {}
