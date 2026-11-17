package io.github.luckybearbear.forge.demo;

import com.jayway.jsonpath.JsonPath;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 11 连库 + Task 8 认证接入后的全链路 HTTP 流：Testcontainers 真实 PG/Redis，
 * controller 路径 /app-api/*（platform-web 双端前缀），登录态由真实认证链承载
 * （@ActiveProfiles("test") → DemoDataSeeder 铺底 → /app-api/auth/login 取 token →
 * TokenAuthFilter 填充租户上下文）——2b 前的 X-Tenant-Id 测试过滤器已按其 javadoc 预告移除。
 *
 * <p>业务流全程 mfg-admin（t-mfg 租户，ADMIN 角色=全权限）：登记 → 查询 → 报修 → 异步维修单
 * （@ApplicationModuleListener 事务提交后异步投递）。租户隔离/越权/审计的专项验收见
 * {@link DemoAuthFlowIntegrationTest}（八场景）。
 */
@SpringBootTest(classes = ForgeDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DemoFlowIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    // Boot 4 removed TestRestTemplate (brief was written against Boot 3 API): plain RestTemplate
    // + random port, with a pass-through error handler (TestRestTemplate never threw on 4xx/5xx
    // either) so error bodies stay assertable. Same pattern as starter StarterSmokeTest.
    RestTemplate rest = noThrowRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login() {
        ResponseEntity<String> response = rest.postForEntity(url("/app-api/auth/login"),
                json("{\"tenantCode\":\"t-mfg\",\"username\":\"mfg-admin\",\"password\":\"Admin@123\"}", null),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.token");
    }

    /** GET 统一携带登录 token（与 POST 同一登录态，保证登记/查询/报修/维修单同租户可见）。 */
    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
    }

    private static HttpEntity<String> json(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void fullFlowRegisterFindRepairAndMaintenanceOrder() {
        String token = login();

        // 1. 登记模具（mfg-admin 持 mold:create）
        ResponseEntity<String> register = rest.postForEntity(url("/app-api/molds"),
                json("{\"name\":\"测试模具\",\"specification\":\"两腔\"}", token), String.class);
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        String moldId = JsonPath.read(register.getBody(), "$.data.id");
        assertThat((String) JsonPath.read(register.getBody(), "$.data.status")).isEqualTo("IN_SERVICE");

        // 2. 查询（同租户可见）
        ResponseEntity<String> found = get("/app-api/molds/" + moldId, token);
        assertThat((String) JsonPath.read(found.getBody(), "$.code")).isEqualTo("OK");

        // 3. 不存在 → MOLD-0404 + HTTP 404
        ResponseEntity<String> missing = get("/app-api/molds/missing", token);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat((String) JsonPath.read(missing.getBody(), "$.code")).isEqualTo("MOLD-0404");

        // 4. 校验失败 → FORGE-0400 + 400 + 字段 map
        ResponseEntity<String> invalid = rest.postForEntity(url("/app-api/molds"),
                json("{\"name\":\"\",\"specification\":\"x\"}", token), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat((String) JsonPath.read(invalid.getBody(), "$.code")).isEqualTo("FORGE-0400");
        // (String) cast: uncast generic JsonPath.read makes assertThat overload resolution ambiguous
        assertThat((String) JsonPath.read(invalid.getBody(), "$.data.name")).isNotBlank();

        // 5. 报修 → 异步事件（@ApplicationModuleListener：事务提交后异步投递 + event_publication 登记）
        ResponseEntity<String> repair = rest.postForEntity(url("/app-api/molds/" + moldId + "/repairs"),
                json("{\"description\":\"导柱磨损\"}", token), String.class);
        assertThat((String) JsonPath.read(repair.getBody(), "$.code")).isEqualTo("OK");

        // 6. 等待 maintenance 生成维修单（异步），并验证契约查询补全的模具名
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<String> orders = get("/app-api/maintenances?moldId=" + moldId, token);
            List<?> items = JsonPath.read(orders.getBody(), "$.data");
            assertThat(items).hasSize(1);
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].moldName"))
                    .isEqualTo("测试模具");
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].description"))
                    .isEqualTo("导柱磨损");
        });

        // 7. 维修单详情
        ResponseEntity<String> orderList = get("/app-api/maintenances?moldId=" + moldId, token);
        String orderId = JsonPath.read(orderList.getBody(), "$.data[0].id");
        ResponseEntity<String> detail = get("/app-api/maintenances/" + orderId, token);
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
}
