package io.github.luckybearbear.forge.platform.audit;

import java.time.Instant;

/**
 * API 错误日志事件（spec §6.6）：{@code GlobalExceptionHandler} 的 500 兜底分支
 * （{@code handleUnknown}）发布，经 Modulith 事件登记异步投递给 platform-audit 的
 * {@code ErrorLogListener} 落 {@code api_error_log} 表。
 *
 * <p><b>发布点取舍</b>：仅未预期的 500 兜底分支发布；4xx 家族与业务分支
 * （BusinessException）不发——业务异常是常态语义流量，记入错误表是噪音，其结果码可观测性
 * 已由 {@code api_access_log.result_code} 覆盖。
 *
 * <p><b>事务语义</b>：异常处理器内通常无活跃事务，而消费方 {@code @ApplicationModuleListener}
 * 为 AFTER_COMMIT 投递（<b>无事务发布会被 Modulith 直接丢弃</b>）——发布方以
 * TransactionTemplate 包裹 publishEvent（事务管理器可选注入，standalone 装配无管理器时
 * 直接发布——无 AFTER_COMMIT 监听器场景无害）。
 *
 * <p><b>列边界防御</b>：compact constructor 按 {@code api_error_log} 落库列长截断
 * tenantId/userId/exception（36/36/512）——超长异常类名原样传入会使监听器 INSERT 失败，
 * event_publication 卡 incomplete。message/stackDigest 为 TEXT 列不限长（stackDigest 发布侧
 * 已截前 2000 字符）。tenantId/userId 在请求线程异常处理时刻快照（可空，语义同
 * {@link AccessLogRecorded}）。
 *
 * @param tenantId    发布时刻的租户上下文（可空——未认证请求；截断 ≤36）
 * @param userId      发布时刻的用户上下文（可空——未认证请求；截断 ≤36）
 * @param exception   异常类全限定名（截断 ≤512）
 * @param message     异常消息（可空——异常可无消息；TEXT 列不限长）
 * @param stackDigest 堆栈摘要（printStackTrace 前 2000 字符截断；TEXT 列不限长）
 * @param occurredAt  异常处理时刻
 */
public record ErrorLogRecorded(String tenantId, String userId, String exception, String message,
        String stackDigest, Instant occurredAt) {

    public ErrorLogRecorded {
        tenantId = AuditColumnLimits.truncate(tenantId, AuditColumnLimits.TENANT_ID);
        userId = AuditColumnLimits.truncate(userId, AuditColumnLimits.USER_ID);
        exception = AuditColumnLimits.truncate(exception, AuditColumnLimits.EXCEPTION);
    }
}
