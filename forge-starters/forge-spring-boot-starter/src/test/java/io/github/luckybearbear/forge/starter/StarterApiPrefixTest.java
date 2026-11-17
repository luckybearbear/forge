package io.github.luckybearbear.forge.starter;

import io.github.luckybearbear.forge.starter.prefix.controller.admin.AdminProbeController;
import io.github.luckybearbear.forge.starter.prefix.controller.app.AppProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter 级双端前缀端到端（2b Step 3 装配改版后的回归守卫）：前缀注册不再经
 * {@code @Import(ForgeApiPrefixConfiguration)}，而由 ForgeWebAutoConfiguration 的
 * {@code @ConditionalOnMissingBean(WebMvcRegistrations.class)} 条件注册——缺省装配下
 * controller.app/admin.* → /app-api、/admin-api 依旧开箱即用。
 *
 * <p>扫描根收窄至 controller 子包（同 platform-web ApiPrefixDisabledMappingTest 模式），
 * 不拾取本模块其他测试的嵌套配置。
 */
@SpringBootTest(classes = StarterApiPrefixTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StarterApiPrefixTest {

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

    @SpringBootApplication(scanBasePackageClasses = {AppProbeController.class, AdminProbeController.class})
    static class TestApp {}

    @Test
    void starterAssemblyAppliesPackagePrefixes() {
        ResponseEntity<String> app = rest.getForEntity(url("/app-api/ping"), String.class);
        assertThat(app.getStatusCode().value()).isEqualTo(200);
        assertThat(app.getBody()).isEqualTo("app");

        ResponseEntity<String> admin = rest.getForEntity(url("/admin-api/ping"), String.class);
        assertThat(admin.getStatusCode().value()).isEqualTo(200);
        assertThat(admin.getBody()).isEqualTo("admin");

        // 前缀是替换而非叠加：裸 /ping 不再可达
        assertThat(rest.getForEntity(url("/ping"), String.class).getStatusCode().value()).isEqualTo(404);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
