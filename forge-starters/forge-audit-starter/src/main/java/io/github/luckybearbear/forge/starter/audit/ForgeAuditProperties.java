package io.github.luckybearbear.forge.starter.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * forge.audit.* 属性（spec §6.6，P3 Task 2 审计发布异步化）：访问日志事件发布通道形态。
 *
 * <p>缺省异步：请求线程仅入队（去每请求事务税），消费侧（虚拟线程）以 TransactionTemplate
 * 包裹 publishEvent（Modulith AFTER_COMMIT 投递要求事务内发布——语义与旧同步路径等价，
 * 仅挪出请求线程）。async=false 为逃生口——回退请求线程内同步发布（旧路径，排障/对照用）。
 *
 * @param async        访问日志发布是否异步（默认 true；false=请求线程同步发布）
 * @param queueCapacity 异步有界队列容量（默认 1024；满则丢弃计数——审计不反噬请求）
 */
@ConfigurationProperties(prefix = "forge.audit")
public record ForgeAuditProperties(
        @DefaultValue("true") boolean async,
        @DefaultValue("1024") int queueCapacity) {
}
