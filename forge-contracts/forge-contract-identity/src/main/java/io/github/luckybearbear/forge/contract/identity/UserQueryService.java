package io.github.luckybearbear.forge.contract.identity;

import java.util.Optional;

/** 跨模块同步查询契约：service-identity 实现，其他模块消费。 */
public interface UserQueryService {

    Optional<UserDTO> findById(String userId);
}
