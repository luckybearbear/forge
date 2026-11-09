package io.github.luckybearbear.forge.contract.mold;

/** 模具快照 DTO，跨模块同步查询用。 */
public record MoldDTO(String id, String name, String specification, MoldStatus status) {}
