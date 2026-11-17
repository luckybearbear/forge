package io.github.luckybearbear.forge.demo;

import com.jayway.jsonpath.JsonPath;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 11 连库改造：内存 → Testcontainers 真实 PG（AbstractIntegrationTest）；
 * controller 迁入 controller/app 包 → 路径 /app-api/*（platform-web 双端前缀）。
 *
 * <p>租户载体：测试专用 X-Tenant-Id 头过滤器（2b 接入 forge-security-starter 的 TokenAuthFilter 后移除，
 * 由 token 填充租户）。双租户语义验收见 DemoTenantIsolationIntegrationTest（服务层显式上下文）。
 */
@SpringBootTest(classes = {ForgeDemoApplication.class, DemoFlowIntegrationTest.TenantHeaderFilterConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoFlowIntegrationTest extends AbstractIntegrationTest {

    /** 2b 前的过渡：HTTP 层以头模拟登录态租户（set/finally clear，与 TokenAuthFilter 同形）。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TenantHeaderFilterConfig {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> tenantHeaderFilter() {
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
                    String tenantId = request.getHeader("X-Tenant-Id");
                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContextHolder.setTenantId(tenantId);
                    }
                    try {
                        chain.doFilter(request, response);
                    } finally {
                        TenantContextHolder.clear();
                    }
                }
            });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    private static final String TENANT_HEADER = "X-Tenant-Id";

    // Boot 4 removed TestRestTemplate (brief was written against Boot 3 API): plain RestTemplate
    // + random port, with a pass-through error handler (TestRestTemplate never threw on 4xx/5xx
    // either) so error bodies stay assertable. Same pattern as starter StarterSmokeTest.
    @LocalServerPort
    int port;

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** GET 统一携带模拟租户头（与 POST 同一登录态，保证登记/查询/报修/维修单同租户可见）。 */
    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, "tenant-flow");
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TENANT_HEADER, "tenant-flow"); // 模拟登录态租户，贯穿登记→查询→报修→维修单
        return new HttpEntity<>(body, headers);
    }

    @Test
    void fullFlowRegisterFindRepairAndMaintenanceOrder() {
        // 1. 登记模具
        ResponseEntity<String> register = rest.postForEntity(url("/app-api/molds"),
                json("{\"name\":\"测试模具\",\"specification\":\"两腔\"}"), String.class);
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        String moldId = JsonPath.read(register.getBody(), "$.data.id");
        assertThat((String) JsonPath.read(register.getBody(), "$.data.status")).isEqualTo("IN_SERVICE");

        // 2. 查询（同租户可见）
        ResponseEntity<String> found = get("/app-api/molds/" + moldId);
        assertThat((String) JsonPath.read(found.getBody(), "$.code")).isEqualTo("OK");

        // 3. 不存在 → MOLD-0404 + HTTP 404
        ResponseEntity<String> missing = get("/app-api/molds/missing");
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat((String) JsonPath.read(missing.getBody(), "$.code")).isEqualTo("MOLD-0404");

        // 4. 校验失败 → FORGE-0400 + 400 + 字段 map
        ResponseEntity<String> invalid = rest.postForEntity(url("/app-api/molds"),
                json("{\"name\":\"\",\"specification\":\"x\"}"), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat((String) JsonPath.read(invalid.getBody(), "$.code")).isEqualTo("FORGE-0400");
        // (String) cast: uncast generic JsonPath.read makes assertThat overload resolution ambiguous
        assertThat((String) JsonPath.read(invalid.getBody(), "$.data.name")).isNotBlank();

        // 5. 报修 → 异步事件（@ApplicationModuleListener：事务提交后异步投递 + event_publication 登记）
        ResponseEntity<String> repair = rest.postForEntity(url("/app-api/molds/" + moldId + "/repairs"),
                json("{\"description\":\"导柱磨损\"}"), String.class);
        assertThat((String) JsonPath.read(repair.getBody(), "$.code")).isEqualTo("OK");

        // 6. 等待 maintenance 生成维修单（异步），并验证契约查询补全的模具名
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<String> orders = get("/app-api/maintenances?moldId=" + moldId);
            List<?> items = JsonPath.read(orders.getBody(), "$.data");
            assertThat(items).hasSize(1);
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].moldName"))
                    .isEqualTo("测试模具");
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].description"))
                    .isEqualTo("导柱磨损");
        });

        // 7. 维修单详情
        ResponseEntity<String> orderList = get("/app-api/maintenances?moldId=" + moldId);
        String orderId = JsonPath.read(orderList.getBody(), "$.data[0].id");
        ResponseEntity<String> detail = get("/app-api/maintenances/" + orderId);
        assertThat((String) JsonPath.read(detail.getBody(), "$.code")).isEqualTo("OK");
    }

    @Test
    void openApiAndHealthAreServed() {
        assertThat(rest.getForEntity(url("/v3/api-docs"), String.class).getStatusCode().value())
                .isEqualTo(200);
        assertThat(rest.getForEntity(url("/actuator/health"), String.class).getStatusCode().value())
                .isEqualTo(200);
        // /app-api 前缀已进 OpenAPI 文档（springdoc 随 handler mapping 自动暴露新路径）
        String docs = rest.getForEntity(url("/v3/api-docs"), String.class).getBody();
        assertThat(docs).contains("/app-api/molds");
        assertThat(docs).contains("/app-api/maintenances");
    }
}
