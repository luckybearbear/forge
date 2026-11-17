package io.github.luckybearbear.forge.contract.identity;

/** 用户快照 DTO，跨模块同步查询用。 */
public record UserDTO(String id, String tenantId, String username, String phone, String status) {}
