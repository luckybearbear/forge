package io.github.luckybearbear.forge.audit.flow;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import io.github.luckybearbear.forge.platform.audit.ErrorLogRecorded;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件 record 列边界截断单测（Important #2）：compact constructor 按落库列长截断
 * tenantId/userId/method/path/exception（36/36/8/512/512）——超长值原样传入会使监听器
 * INSERT 失败、event_publication 卡 incomplete 重投；TEXT 列字段（bodyDigest/message/
 * stackDigest）不限长不截。容器链路实证见 AuditStarterIntegrationTest（超长 URI 用例）。
 */
class AuditEventColumnBoundaryTest {

    @Test
    void accessLogRecordedTruncatesLimitedColumnsAndKeepsTextColumn() {
        String bodyDigest = "d".repeat(10_000); // TEXT 列不限长（filter 侧已按 max-body-size 截断）
        AccessLogRecorded event = new AccessLogRecorded(
                "t".repeat(40), "u".repeat(40), "M".repeat(20), "P".repeat(600),
                200, 5L, bodyDigest, Instant.now());

        assertThat(event.tenantId()).hasSize(36);
        assertThat(event.userId()).hasSize(36);
        assertThat(event.method()).hasSize(8);
        assertThat(event.path()).hasSize(512);
        assertThat(event.bodyDigest()).hasSize(10_000); // 落 TEXT 列：构造侧不重复截断
    }

    @Test
    void errorLogRecordedTruncatesExceptionButKeepsTextColumns() {
        String message = "m".repeat(5_000);
        String stackDigest = "s".repeat(3_000); // 发布侧已截 2000，构造侧对 TEXT 列不再截
        ErrorLogRecorded event = new ErrorLogRecorded(
                "t".repeat(40), null, "E".repeat(600), message, stackDigest, Instant.now());

        assertThat(event.tenantId()).hasSize(36);
        assertThat(event.userId()).isNull(); // null 不动（可空列）
        assertThat(event.exception()).hasSize(512);
        assertThat(event.message()).hasSize(5_000);
        assertThat(event.stackDigest()).hasSize(3_000);
    }
}
