package io.github.luckybearbear.forge.demo;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ForgeDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoFlowIntegrationTest {

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

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void fullFlowRegisterFindRepairAndMaintenanceOrder() {
        // 1. 登记模具
        ResponseEntity<String> register = rest.postForEntity(url("/api/molds"),
                json("{\"name\":\"测试模具\",\"specification\":\"两腔\"}"), String.class);
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        String moldId = JsonPath.read(register.getBody(), "$.data.id");
        assertThat((String) JsonPath.read(register.getBody(), "$.data.status")).isEqualTo("IN_SERVICE");

        // 2. 查询
        ResponseEntity<String> found = rest.getForEntity(url("/api/molds/" + moldId), String.class);
        assertThat((String) JsonPath.read(found.getBody(), "$.code")).isEqualTo("OK");

        // 3. 不存在 → MOLD-0404 + HTTP 404
        ResponseEntity<String> missing = rest.getForEntity(url("/api/molds/missing"), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat((String) JsonPath.read(missing.getBody(), "$.code")).isEqualTo("MOLD-0404");

        // 4. 校验失败 → FORGE-0400 + 400 + 字段 map
        ResponseEntity<String> invalid = rest.postForEntity(url("/api/molds"),
                json("{\"name\":\"\",\"specification\":\"x\"}"), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat((String) JsonPath.read(invalid.getBody(), "$.code")).isEqualTo("FORGE-0400");
        // (String) cast: uncast generic JsonPath.read makes assertThat overload resolution ambiguous
        assertThat((String) JsonPath.read(invalid.getBody(), "$.data.name")).isNotBlank();

        // 5. 报修 → 异步事件
        ResponseEntity<String> repair = rest.postForEntity(url("/api/molds/" + moldId + "/repairs"),
                json("{\"description\":\"导柱磨损\"}"), String.class);
        assertThat((String) JsonPath.read(repair.getBody(), "$.code")).isEqualTo("OK");

        // 6. 等待 maintenance 生成维修单（异步），并验证契约查询补全的模具名
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<String> orders = rest.getForEntity(
                    url("/api/maintenances?moldId=" + moldId), String.class);
            java.util.List<?> items = JsonPath.read(orders.getBody(), "$.data");
            assertThat(items).hasSize(1);
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].moldName"))
                    .isEqualTo("测试模具");
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].description"))
                    .isEqualTo("导柱磨损");
        });

        // 7. 维修单详情
        ResponseEntity<String> orderList = rest.getForEntity(
                url("/api/maintenances?moldId=" + moldId), String.class);
        String orderId = JsonPath.read(orderList.getBody(), "$.data[0].id");
        ResponseEntity<String> detail = rest.getForEntity(url("/api/maintenances/" + orderId), String.class);
        assertThat((String) JsonPath.read(detail.getBody(), "$.code")).isEqualTo("OK");
    }

    @Test
    void openApiAndHealthAreServed() {
        assertThat(rest.getForEntity(url("/v3/api-docs"), String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity(url("/actuator/health"), String.class).getStatusCode().value()).isEqualTo(200);
    }
}
