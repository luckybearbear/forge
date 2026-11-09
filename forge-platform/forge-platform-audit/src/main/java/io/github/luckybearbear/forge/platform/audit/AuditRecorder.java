package io.github.luckybearbear.forge.platform.audit;

/** 审计记录 SPI。Phase 2 接操作日志落库（经 Modulith 事件异步化）。 */
public interface AuditRecorder {

    void record(AuditEntry entry);
}
