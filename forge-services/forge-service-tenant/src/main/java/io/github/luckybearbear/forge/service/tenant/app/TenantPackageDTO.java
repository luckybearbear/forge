package io.github.luckybearbear.forge.service.tenant.app;

/** 套餐快照 DTO（tenant 契约未定义套餐面——纯内部形态，控制层/服务层共用）。 */
public record TenantPackageDTO(String id, String code, String name, Integer maxUsers, String remark) {}
