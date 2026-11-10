package io.github.luckybearbear.forge.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 平台视角（spec §6.1）：忽略租户上下文 → 解析为 PLATFORM_TENANT_ID，租户表查询恒空。非跨租户全量。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TenantIgnore {
}
