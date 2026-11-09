package io.github.luckybearbear.forge.starter;

import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

        @PostMapping("/smoke/echo")
        Result<String> echo(@Valid @RequestBody EchoRequest request) {
            return Result.ok(request.name());
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
