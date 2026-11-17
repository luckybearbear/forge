package io.github.luckybearbear.forge.starter.web;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 请求体可重复读（访问日志/审计前置）配置（spec §6.5；终审 Important #2 补齐 starter 装配）。
 *
 * <p>{@code forge.web.access-log.*} 绑定至本 record：{@code max-body-size} 为缓存上限
 * （超出部分不缓存，不影响请求体读取），{@code exclude-urls} 为不包装的<b>精确路径</b>集合
 * （上传类端点）。访问日志落库消费方于 2b 接入（终审裁定降范围）。
 *
 * @param enabled     是否注册 body 缓存过滤器（默认 true）
 * @param maxBodySize 缓存上限，缺省 8KB
 * @param excludeUrls 不包装的精确路径集合，缺省空集
 */
@ConfigurationProperties(prefix = "forge.web.access-log")
public record ForgeWebAccessLogProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("8KB") DataSize maxBodySize,
        @DefaultValue Set<String> excludeUrls) {
}
