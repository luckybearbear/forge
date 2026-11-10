package io.github.luckybearbear.forge.platform.web;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class CacheRequestBodyFilterTest {

    private final CacheRequestBodyFilter filter =
            new CacheRequestBodyFilter(8 * 1024, Set.of("/upload"));

    @Test
    void bodyReadableAfterChainInsideFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app-api/x");
        request.setContent("{\"name\":\"模具\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        StringBuilder seen = new StringBuilder();
        filter.doFilter(request, response, (req, res) -> {
            var cached = (org.springframework.web.util.ContentCachingRequestWrapper) req;
            // 触发缓存：ContentCachingRequestWrapper 仅在流被消费时写入缓存，
            // 仅 getReader() 不读取（Spring 7 实证），故读空 reader
            cached.getReader().transferTo(java.io.Writer.nullWriter());
            seen.append(new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8));
        });
        assertThat(seen.toString()).contains("模具");
    }

    @Test
    void excludedUrlPassesThroughUnwrapped() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request); // 未包装
    }
}
