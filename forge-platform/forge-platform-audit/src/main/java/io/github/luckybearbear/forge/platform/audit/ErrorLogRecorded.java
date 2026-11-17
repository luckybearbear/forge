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
 * <p>发布点在事务之外（异常处理器内通常无活跃事务）——监听器以
 * {@code @ApplicationModuleListener(fallbackExecution = true)} 消费。tenantId/userId 在
 * 请求线程异常处理时刻快照（可空，语义同 {@link AccessLogRecorded}）。
 *
 * @param tenantId    发布时刻的租户上下文（可空——未认证请求）
 * @param userId      发布时刻的用户上下文（可空——未认证请求）
 * @param exception   异常类全限定名
 * @param message     异常消息（可空——异常可无消息）
 * @param stackDigest 堆栈摘要（printStackTrace 前 2000 字符截断）
 * @param occurredAt  异常处理时刻
 */
public record ErrorLogRecorded(String tenantId, String userId, String exception, String message,
        String stackDigest, Instant occurredAt) {}
