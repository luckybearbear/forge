package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.audit.ErrorLogRecorded;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
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

    @AfterEach
    void cleanContext() {
        TenantContextHolder.clear();
        ThreadLocalUserProvider.clear();
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

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        // TestRestController 增加 /test/optimistic-lock 端点：
        // @GetMapping("/test/optimistic-lock") Result<Void> optimisticLock() {
        //     throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, 1);
        // }
        mvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FORGE-0423"));
    }

    // ===== 错误侧审计发布（spec §6.6，Task 7）：仅 500 兜底分支发布 ErrorLogRecorded =====

    private final List<Object> published = new CopyOnWriteArrayList<>();

    private MockMvc mvcWithPublisher(PlatformTransactionManager transactionManager) {
        ApplicationEventPublisher recorder = published::add;
        return MockMvcBuilders.standaloneSetup(new TestRestController())
                .setControllerAdvice(new GlobalExceptionHandler(recorder, transactionManager))
                .build();
    }

    /** 直通桩（无真实连接）：仅证"发布经事务包裹后仍送达"的模板路径，不验隔离性。 */
    private static PlatformTransactionManager passthroughTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(
                    org.springframework.transaction.TransactionDefinition definition) {
                return new org.springframework.transaction.support.SimpleTransactionStatus();
            }

            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {}

            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {}
        };
    }

    @Test
    void unknownExceptionPublishesErrorLogRecordedWithContextSnapshot() throws Exception {
        TenantContextHolder.setTenantId("t-audit-unit");
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));

        mvcWithPublisher(null).perform(get("/test/boom"))
                .andExpect(status().isInternalServerError());

        assertThat(published).hasSize(1);
        ErrorLogRecorded event = (ErrorLogRecorded) published.get(0);
        assertThat(event.tenantId()).isEqualTo("t-audit-unit");
        assertThat(event.userId()).isEqualTo("u-1");
        assertThat(event.exception()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.message()).isEqualTo("boom");
        assertThat(event.stackDigest()).contains("IllegalStateException: boom");
        assertThat(event.stackDigest().length()).isLessThanOrEqualTo(2000);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void unknownExceptionPublishesWithinTransactionWhenManagerPresent() throws Exception {
        // starter 装配路径：事务管理器在场 → publishEvent 经 TransactionTemplate 包裹（AFTER_COMMIT
        // 投递要求事务内发布），发布仍送达一次
        mvcWithPublisher(passthroughTransactionManager()).perform(get("/test/boom"))
                .andExpect(status().isInternalServerError());

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOf(ErrorLogRecorded.class);
    }

    @Test
    void businessAnd4xxBranchesDoNotPublishErrorLog() throws Exception {
        MockMvc audited = mvcWithPublisher(null);
        audited.perform(get("/test/biz-404")).andExpect(status().isNotFound());
        audited.perform(get("/test/biz-default")).andExpect(status().isOk());
        audited.perform(post("/test/validate").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 发布点取舍（类 javadoc）：业务/4xx 是常态流量不算错误，仅 500 兜底入错误日志
        assertThat(published).isEmpty();
    }

    @Test
    void missingPublisherDoesNotBreakHandling() throws Exception {
        // standalone 装配（无参构造）：兜底分支跳过发布，响应语义不变（上方全组用例同构造，
        // 此处显式钉住 boom 路径的"不炸"契约）
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FORGE-0500"));
    }
}
