package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.web.controller.app.AppSideController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec §6.5：{@code enabled=false} 时所有包均不加前缀（回归守卫）。只扫描 app 端测试控制器：
 * 关闭前缀后 app/admin 两端都会落在裸 /ping，同扫会 Ambiguous mapping；app 端即可证明开关语义。
 */
@SpringBootTest(classes = {ApiPrefixDisabledMappingTest.TestApp.class,
        ForgeApiPrefixConfiguration.class,
        ApiPrefixDisabledMappingTest.DisabledPrefix.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiPrefixDisabledMappingTest {

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

    // 扫描根仅 controller.app 子包；ForgeApiPrefixConfiguration 与 DisabledPrefix 经 classes 显式注册。
    @SpringBootApplication(scanBasePackageClasses = AppSideController.class)
    static class TestApp {
    }

    @Configuration
    static class DisabledPrefix {

        @Bean
        ForgeApiPrefixProperties forgeApiPrefixProperties() {
            return new ForgeApiPrefixProperties(false, "/app-api", "/admin-api");
        }
    }

    @Test
    void noPrefixAppliedWhenDisabled() {
        ResponseEntity<String> bareApp = rest.getForEntity(url("/ping"), String.class);
        assertThat(bareApp.getStatusCode().value()).isEqualTo(200);
        assertThat(bareApp.getBody()).isEqualTo("app");

        ResponseEntity<String> prefixedApp = rest.getForEntity(url("/app-api/ping"), String.class);
        assertThat(prefixedApp.getStatusCode().value()).isEqualTo(404);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
