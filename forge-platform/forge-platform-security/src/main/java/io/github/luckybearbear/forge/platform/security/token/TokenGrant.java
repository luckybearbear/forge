package io.github.luckybearbear.forge.platform.security.token;

import java.util.Set;

/** 认证通过后的授权信息（进入 token 存储）。 */
public record TokenGrant(String userId, String tenantId, String userType, Set<String> authorities) {}
