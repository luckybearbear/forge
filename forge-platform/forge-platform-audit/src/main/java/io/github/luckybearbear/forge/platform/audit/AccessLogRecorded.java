package io.github.luckybearbear.forge.platform.audit;

import java.time.Instant;

/**
 * API 访问日志事件（spec §6.6）：{@code ApiAccessLogFilter} 在响应完成后发布，经 Modulith
 * 事件登记（event_publication）异步投递给 platform-audit 的 {@code AccessLogListener} 落
 * {@code api_access_log} 表。
 *
 * <p><b>事务语义</b>：发布点在 filter 的 finally 段——请求线程此刻无自然事务，而消费方
 * {@code @ApplicationModuleListener} 为 AFTER_COMMIT 投递（<b>无事务发布会被 Modulith 直接
 * 丢弃</b>——service-authorization 集成测试实证）——发布方以 TransactionTemplate 包裹
 * publishEvent（event_publication 登记随该事务原子提交，提交后异步投递）。
 *
 * <p><b>列边界防御</b>：compact constructor 按 {@code api_access_log} 落库列长截断
 * tenantId/userId/method/path（36/36/8/512）——超长 URI 等原样传入会使监听器 INSERT 失败，
 * event_publication 卡 incomplete 进入失败重投循环。bodyDigest 为 TEXT 列不限长（filter 侧
 * 已按 max-body-size 截断并剥离控制字符）。
 *
 * <p>tenantId/userId 在请求线程发布时刻快照（异步监听线程不继承 TTL 上下文，spec §6.1）：
 * 未认证/无租户请求为 {@code null}（审计表冗余列可空）。
 *
 * @param tenantId   发布时刻的租户上下文（可空——未认证请求；截断 ≤36）
 * @param userId     发布时刻的用户上下文（可空——未认证请求；截断 ≤36）
 * @param method     HTTP 方法（截断 ≤8）
 * @param path       请求 URI（截断 ≤512）
 * @param resultCode 响应状态码（filter 链后 response.getStatus()）
 * @param durationMs 请求耗时（毫秒，filter 计时）
 * @param bodyDigest 请求体摘要（ContentCachingRequestWrapper 缓存体截断至 max-body-size 的 UTF-8 串，
 *                   控制字符已剥离；无缓存 wrapper 时为 {@code null}，有 wrapper 但未读体时为空串）
 * @param occurredAt 请求到达时刻（filter 入口快照，非落库时刻）
 */
public record AccessLogRecorded(String tenantId, String userId, String method, String path,
        int resultCode, long durationMs, String bodyDigest, Instant occurredAt) {

    public AccessLogRecorded {
        tenantId = AuditColumnLimits.truncate(tenantId, AuditColumnLimits.TENANT_ID);
        userId = AuditColumnLimits.truncate(userId, AuditColumnLimits.USER_ID);
        method = AuditColumnLimits.truncate(method, AuditColumnLimits.METHOD);
        path = AuditColumnLimits.truncate(path, AuditColumnLimits.PATH);
    }
}
