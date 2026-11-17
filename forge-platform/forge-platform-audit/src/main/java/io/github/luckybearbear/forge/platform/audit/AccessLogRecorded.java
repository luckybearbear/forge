package io.github.luckybearbear.forge.platform.audit;

import java.time.Instant;

/**
 * API 访问日志事件（spec §6.6）：{@code ApiAccessLogFilter} 在响应完成后发布，经 Modulith
 * 事件登记（event_publication）异步投递给 platform-audit 的 {@code AccessLogListener} 落
 * {@code api_access_log} 表。
 *
 * <p>发布点在事务之外（filter 的 finally 段）——监听器以
 * {@code @ApplicationModuleListener(fallbackExecution = true)} 消费（无事务发布不丢弃）。
 * tenantId/userId 在请求线程发布时刻快照（异步监听线程不继承 TTL 上下文，spec §6.1）：
 * 未认证/无租户请求为 {@code null}（审计表冗余列可空）。
 *
 * @param tenantId   发布时刻的租户上下文（可空——未认证请求）
 * @param userId     发布时刻的用户上下文（可空——未认证请求）
 * @param method     HTTP 方法
 * @param path       请求 URI
 * @param resultCode 响应状态码（filter 链后 response.getStatus()）
 * @param durationMs 请求耗时（毫秒，filter 计时）
 * @param bodyDigest 请求体摘要（ContentCachingRequestWrapper 缓存体截断至 max-body-size 的 UTF-8 串；
 *                   无缓存 wrapper 时为 {@code null}，有 wrapper 但未读体时为空串）
 * @param occurredAt 请求到达时刻（filter 入口快照，非落库时刻）
 */
public record AccessLogRecorded(String tenantId, String userId, String method, String path,
        int resultCode, long durationMs, String bodyDigest, Instant occurredAt) {}
