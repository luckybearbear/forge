package io.github.luckybearbear.forge.platform.web;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class JacksonLongToStringModuleTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JacksonLongToStringModule())
            .build();

    @Test
    void serializesLongAsString() {
        Map<String, Long> data = Map.of("id", 9007199254740993L); // 超 JS 安全整数
        String json = mapper.writeValueAsString(data);
        assertThat(json).contains("\"9007199254740993\"");
    }
}
