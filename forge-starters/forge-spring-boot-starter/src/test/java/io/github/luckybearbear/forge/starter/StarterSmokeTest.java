package io.github.luckybearbear.forge.starter;

import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StarterSmokeTest.SmokeApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StarterSmokeTest {

    private static final Instant FIXED = Instant.parse("2026-11-09T00:00:00Z");

    // Boot 4 removed TestRestTemplate (brief was written against Boot 3 API): plain RestTemplate + random port.
    // TestRestTemplate never threw on 4xx/5xx (pass-through error handler); replicated here to assert error bodies.
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

    @SpringBootApplication
    @RestController
    static class SmokeApp {

        record EchoRequest(@NotBlank String name) {}

        @GetMapping("/smoke/time")
        Result<Instant> time() {
            return Result.ok(FIXED);
        }

        @GetMapping("/smoke/id")
        Result<Long> bigId() {
            return Result.ok(123456789012345L);
        }

        @PostMapping("/smoke/echo")
        Result<String> echo(@Valid @RequestBody EchoRequest request) {
            return Result.ok(request.name());
        }

        @PostMapping("/smoke/echo-cached")
        Result<String> echoCached(@Valid @RequestBody EchoRequest request, HttpServletRequest raw) {
            // 终审 Important #2 断言载体：starter 装配的 CacheRequestBodyFilter 生效时，
            // handler 收到的请求已被 ContentCachingRequestWrapper 包装，且 @RequestBody 消费后缓存可读
            String cached = raw instanceof ContentCachingRequestWrapper wrapper
                    ? new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)
                    : "<unwrapped>";
            return Result.ok(cached);
        }
    }

    @Test
    void instantSerializesAsIso8601() {
        ResponseEntity<String> response = rest.getForEntity(url("/smoke/time"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("2026-11-09T00:00:00Z").contains("\"OK\"");
    }

    @Test
    void validationFailureMappedByStarter() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url("/smoke/echo"),
                new HttpEntity<>("{\"name\":\"\"}", headers),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("FORGE-0400");
    }

    @Test
    void longFieldsSerializeAsString() {
        // 终审 Important #2：starter 注册的 JacksonLongToStringModule 对所有 Jackson 消费者生效，
        // Long 输出为 JSON 字符串（带引号）而非裸数字
        ResponseEntity<String> response = rest.getForEntity(url("/smoke/id"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"123456789012345\"");
    }

    @Test
    void requestBodyCachedForRepeatableRead() {
        // 终审 Important #2：CacheRequestBodyFilter 默认装配，handler 侧可读已消费的请求体缓存
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(url("/smoke/echo-cached"),
                new HttpEntity<>("{\"name\":\"模具\"}", headers),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // data 为缓存的原样请求体（JSON 字符串，序列化时引号被转义）
        assertThat(response.getBody()).contains("name").contains("模具");
    }

    @Test
    void actuatorHealthExposed() {
        ResponseEntity<String> response = rest.getForEntity(url("/actuator/health"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void openApiDocsAvailable() {
        ResponseEntity<String> response = rest.getForEntity(url("/v3/api-docs"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
