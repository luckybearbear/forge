package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 401/403 统一 Result 输出（FORGE-0401/0403 启用）。 */
public final class ForgeSecurityResponses {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ForgeSecurityResponses() {}

    public static void write401(HttpServletResponse response) throws IOException {
        write(response, 401, Result.error(ErrorCodes.UNAUTHORIZED));
    }

    public static void write403(HttpServletResponse response) throws IOException {
        write(response, 403, Result.error(ErrorCodes.FORBIDDEN));
    }

    private static void write(HttpServletResponse response, int status, Result<Void> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
