package io.github.luckybearbear.forge.platform.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestRestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void okPathIsUntouched() throws Exception {
        mvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void businessExceptionKeepsCarriedStatus() throws Exception {
        mvc.perform(get("/test/biz-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FORGE-0404"))
                .andExpect(jsonPath("$.message").value("请求的资源不存在: m-1"));
    }

    @Test
    void businessExceptionDefaultsTo200() throws Exception {
        mvc.perform(get("/test/biz-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FORGE-0423"));
    }

    @Test
    void validationFailureReturns400WithFieldMap() throws Exception {
        mvc.perform(post("/test/validate").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORGE-0400"))
                .andExpect(jsonPath("$.data.name").exists());
    }

    @Test
    void bodyNotReadableReturns400() throws Exception {
        mvc.perform(post("/test/validate").contentType(APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORGE-0400"));
    }

    @Test
    void unknownExceptionReturns500WithoutLeakingStack() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FORGE-0500"))
                .andExpect(jsonPath("$.message").value("系统异常"));
    }
}
