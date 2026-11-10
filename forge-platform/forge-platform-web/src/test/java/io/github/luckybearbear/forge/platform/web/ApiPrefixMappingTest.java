package io.github.luckybearbear.forge.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec §6.5 双端前缀路由：controller.app.* → /app-api，controller.admin.* → /admin-api，
 * 其余包不受影响。AppSideController / AdminSideController 是同测试源码集的真实子包顶层类。
 */
@SpringBootTest(classes = ApiPrefixMappingTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiPrefixMappingTest {

    // Boot 4 删除 TestRestTemplate（Phase 1 结论）：RestTemplate + @LocalServerPort + 透传 error handler。
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

    // 扫描根 = io.github...platform.web：拾取 controller.app/controller.admin 测试控制器与
    // main 侧 ForgeApiPrefixConfiguration。
    @SpringBootApplication
    static class TestApp {

        @RestController
        static class PlainController {

            @GetMapping("/plain/ping")
            String ping() {
                return "plain";
            }
        }
    }

    @Test
    void prefixesAppliedByPackage() {
        ResponseEntity<String> app = rest.getForEntity(url("/app-api/ping"), String.class);
        assertThat(app.getStatusCode().value()).isEqualTo(200);
        assertThat(app.getBody()).isEqualTo("app");

        ResponseEntity<String> admin = rest.getForEntity(url("/admin-api/ping"), String.class);
        assertThat(admin.getStatusCode().value()).isEqualTo(200);
        assertThat(admin.getBody()).isEqualTo("admin");

        ResponseEntity<String> plain = rest.getForEntity(url("/plain/ping"), String.class);
        assertThat(plain.getStatusCode().value()).isEqualTo(200);
        assertThat(plain.getBody()).isEqualTo("plain");

        ResponseEntity<String> wrongPrefix = rest.getForEntity(url("/api/ping"), String.class);
        assertThat(wrongPrefix.getStatusCode().value()).isEqualTo(404);

        // 前缀是替换而非叠加：裸 /ping 不再可达。
        ResponseEntity<String> bare = rest.getForEntity(url("/ping"), String.class);
        assertThat(bare.getStatusCode().value()).isEqualTo(404);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
