package io.github.luckybearbear.forge.contract.tenant;

import java.util.Optional;

/** 跨模块同步查询契约：service-tenant 实现，其他模块消费。 */
public interface TenantQueryService {

    Optional<TenantDTO> findById(String tenantId);

    Optional<TenantDTO> findByCode(String code);
}
