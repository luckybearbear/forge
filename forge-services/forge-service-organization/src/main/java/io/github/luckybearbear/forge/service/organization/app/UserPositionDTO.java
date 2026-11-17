package io.github.luckybearbear.forge.service.organization.app;

/** 用户挂岗 DTO（模块内应用视图：id + 逻辑引用对）。 */
public record UserPositionDTO(String id, String userId, String positionId) {}
