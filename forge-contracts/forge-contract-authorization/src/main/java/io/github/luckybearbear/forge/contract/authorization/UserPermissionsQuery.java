package io.github.luckybearbear.forge.contract.authorization;

import java.util.Set;

/** 跨模块同步查询契约：service-authorization 实现，identity 登录等侧消费——返回用户在指定租户内的权限点集合。 */
public interface UserPermissionsQuery {

    Set<String> authoritiesOf(String tenantId, String userId);
}
