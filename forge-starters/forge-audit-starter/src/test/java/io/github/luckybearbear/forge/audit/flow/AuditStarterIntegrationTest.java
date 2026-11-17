package io.github.luckybearbear.forge.audit.flow;

import io.github.luckybearbear.forge.platform.audit.AccessLogRecorded;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * forge-audit-starter 容器集成（真实 PG 容器，全 HTTP 链）：
 * 请求 → ApiAccessLogFilter（-80）发布 AccessLogRecorded → Modulith JDBC 登记表 →
 * AccessLogListener 异步落 api_access_log；未预期异常 → GlobalExceptionHandler 500 兜底发布
 * ErrorLogRecorded → api_error_log；业务/4xx 分支不入错误表（发布点取舍）；
 * event_publication 对应事件 COMPLETED（先登记后投递，spec §10）。
 *
 * <p><b>包位置在 starter 根包之外</b>（...forge.audit.flow，cache/data-starter 双范式）：
 * 测试应用经 @SpringBootApplication 从本包扫描 controller.app 探针（双端前缀 → /app-api），
 * 审计装配本体经 AutoConfiguration.imports 拾取。
 *
 * <p><b>登录态替身</b>：security-starter 不在类路径——ContextSimulatingFilterConfig 以
 * X-Tenant-Id/X-User-Id 头填充 TenantContextHolder/ThreadLocalUserProvider（与 TokenAuthFilter
 * 同形：链前 set、finally clear，order=HIGHEST_PRECEDENCE 保证其 finally 清理晚于访问日志
 * 过滤器的出口读取——上下文快照时机语义与生产一致）。
 *
 * <p>无头 GET（ping）用例验空摘要/空上下文落行；行断言按 path 过滤（append-only 表跨用例累计）。
 */
@SpringBootTest(classes = {AuditStarterIntegrationTest.TestApp.class,
        AuditStarterIntegrationTest.ContextSimulatingFilterConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditStarterIntegrationTest extends AbstractIntegrationTest {

    private static final Duration AWAIT = Duration.ofSeconds(10);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";

    @SpringBootApplication
    static class TestApp {
    }

    /** TokenAuthFilter 替身（见类 javadoc）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class ContextSimulatingFilterConfig {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> contextSimulatingFilter() {
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(
                    new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain chain) throws ServletException, IOException {
                            String tenantId = request.getHeader(TENANT_HEADER);
                            String userId = request.getHeader(USER_HEADER);
                            if (tenantId != null && !tenantId.isBlank()) {
                                TenantContextHolder.setTenantId(tenantId);
                            }
                            if (userId != null && !userId.isBlank()) {
                                ThreadLocalUserProvider.set(new CurrentUser(userId, userId));
                            }
                            try {
                                chain.doFilter(request, response);
                            } finally {
                                TenantContextHolder.clear();
                                ThreadLocalUserProvider.clear();
                            }
                        }
                    });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Boot 4 无 TestRestTemplate：直连 RestTemplate + 直通错误处理器（demo 范式），4xx/5xx 体可断言
    RestTemplate rest = noThrowRestTemplate();

    private static RestTemplate noThrowRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String tenantId,
            String userId, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set(TENANT_HEADER, tenantId);
        }
        if (userId != null) {
            headers.set(USER_HEADER, userId);
        }
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url(path), method, new HttpEntity<>(jsonBody, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private List<Map<String, Object>> accessRows(String path) {
        return jdbcTemplate.queryForList("SELECT * FROM api_access_log WHERE path = ?", path);
    }

    private int completedPublications(Class<?> eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type = ? AND completion_date IS NOT NULL",
                Integer.class, eventType.getName());
    }

    /** 带体 POST：method/path/resultCode/duration/租户/用户/body 摘要齐落一行 + 登记表 COMPLETED。 */
    @Test
    void accessRequestFlowsIntoCompletedAccessLogRow() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/app-api/audit/echo",
                "t-audit-1", "u-audit-1", "{\"name\":\"hello-audit\"}");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("hello-audit");

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = accessRows("/app-api/audit/echo");
            assertThat(rows).isNotEmpty();
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("method")).isEqualTo("POST");
            assertThat(row.get("result_code")).isEqualTo(200);
            assertThat(((Number) row.get("duration_ms")).longValue()).isGreaterThanOrEqualTo(0);
            assertThat(row.get("tenant_id")).isEqualTo("t-audit-1");
            assertThat(row.get("user_id")).isEqualTo("u-audit-1");
            assertThat((String) row.get("body_digest")).contains("hello-audit");
            assertThat(row.get("occurred_at")).isNotNull();
            // 审计基类列（AbstractAuditableEntity）随行落账
            assertThat(row.get("created_at")).isNotNull();
            assertThat(row.get("deleted")).isEqualTo(false);
        });

        // 事件经 registry 异步完成（先登记后投递，spec §10）
        Awaitility.await().atMost(AWAIT).untilAsserted(() ->
                assertThat(completedPublications(AccessLogRecorded.class)).isGreaterThanOrEqualTo(1));
    }

    /** 未预期异常：500 响应 + api_error_log 行（exception/message/stack/租户/用户）+ 访问表同记 500。 */
    @Test
    void unhandledExceptionFlowsIntoErrorLogRow() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/app-api/audit/boom",
                "t-audit-1", "u-audit-2", null);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("FORGE-0500");

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM api_error_log WHERE exception = ?",
                    IllegalStateException.class.getName());
            assertThat(rows).isNotEmpty();
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("message")).isEqualTo("audit-boom");
            assertThat(row.get("tenant_id")).isEqualTo("t-audit-1");
            assertThat(row.get("user_id")).isEqualTo("u-audit-2");
            assertThat((String) row.get("stack_digest")).contains("audit-boom");
            assertThat(row.get("occurred_at")).isNotNull();
        });

        // 错误响应同样入访问表（result_code=500）；ErrorLogRecorded 登记表 COMPLETED
        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = accessRows("/app-api/audit/boom");
            assertThat(rows).isNotEmpty();
            assertThat(rows.get(0).get("result_code")).isEqualTo(500);
        });
        Awaitility.await().atMost(AWAIT).untilAsserted(() ->
                assertThat(completedPublications(
                        io.github.luckybearbear.forge.platform.audit.ErrorLogRecorded.class))
                        .isGreaterThanOrEqualTo(1));
    }

    /** 无头 GET（未认证）：tenant/user 空列、body 摘要空串（wrapper 有但体未读）。 */
    @Test
    void anonymousRequestLogsNullableContextAndEmptyBodyDigest() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/app-api/audit/ping",
                null, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = accessRows("/app-api/audit/ping");
            assertThat(rows).isNotEmpty();
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("method")).isEqualTo("GET");
            assertThat(row.get("result_code")).isEqualTo(200);
            assertThat(row.get("tenant_id")).isNull();
            assertThat(row.get("user_id")).isNull();
            assertThat(row.get("body_digest")).isEqualTo("");
        });
    }

    /** 业务分支（404 语义）：入访问表（result_code=404）但永不入错误表——发布点取舍的实证。 */
    @Test
    void businessExceptionRecordsAccessRowButNeverErrorLog() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/app-api/audit/biz",
                "t-audit-1", "u-audit-3", null);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("FORGE-0404");

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = accessRows("/app-api/audit/biz");
            assertThat(rows).isNotEmpty();
            assertThat(rows.get(0).get("result_code")).isEqualTo(404);
        });

        Integer businessErrors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_error_log WHERE exception = ?",
                Integer.class, BusinessException.class.getName());
        assertThat(businessErrors).isZero(); // 常态业务流量不算错误（类 javadoc 取舍）
    }
}
