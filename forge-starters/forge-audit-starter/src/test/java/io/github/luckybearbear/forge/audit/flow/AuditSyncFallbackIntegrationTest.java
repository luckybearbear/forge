package io.github.luckybearbear.forge.audit.flow;

import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.starter.audit.AsyncAccessLogPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逃生口回归（forge.audit.async=false，P3 Task 2）：ApiAccessLogFilter 回退旧同步路径——
 * 请求线程内 TransactionTemplate 包裹 publishEvent，行照常落库；异步发布器 bean 不装配
 * （判别：容器内无 AsyncAccessLogPublisher）。与 AuditStarterIntegrationTest（缺省
 * async=true，异步判别=发布器 processed 计数）成对覆盖双路径。
 *
 * <p>自含 TestApp（同包扫描 controller.app 探针）；匿名 GET 无需登录态替身
 * （ContextSimulatingFilterConfig 仅在需要租户/用户上下文断言时使用）。
 */
@SpringBootTest(classes = AuditSyncFallbackIntegrationTest.TestApp.class,
        properties = "forge.audit.async=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditSyncFallbackIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectProvider<AsyncAccessLogPublisher> asyncPublisher;

    RestTemplate rest = new RestTemplate();

    @Test
    void syncFallbackPersistsAccessRowAndNoAsyncPublisherBean() {
        assertThat(asyncPublisher.getIfAvailable())
                .as("async=false 逃生口：异步发布器不得装配").isNull();

        String body = rest.getForEntity(
                "http://localhost:" + port + "/app-api/audit/ping", String.class).getBody();
        assertThat(body).contains("pong");

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM api_access_log WHERE path = ?", "/app-api/audit/ping");
            assertThat(rows).isNotEmpty();
            assertThat(rows.get(0).get("result_code")).isEqualTo(200);
        });
    }
}
