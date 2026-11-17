package io.github.luckybearbear.forge.demo;

import com.jayway.jsonpath.JsonPath;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8 认证全链路验收（真实 HTTP 链 + Testcontainers PG/Redis）：四服务（tenant/identity/
 * organization/authorization）+ security（TokenAuthFilter/方法安全）+ cache（权限快照）+ audit
 * （访问/错误日志）首次同台组装，种子数据经服务 API 铺底（@ActiveProfiles("test") →
 * {@link DemoDataSeeder}）。
 *
 * <p><b>九场景</b>（brief 全列 + 最终评审 Important-1 补充）：① 无 token 401 FORGE-0401；
 * ② mfg-admin 登录建模 200；③ 双租户管理员同端点互不可见（判别式隔离经真实认证链）；
 * ④ mold-viewer（仅 mold:read）POST → 403 FORGE-0403；⑤ 角色授权变更 → <b>新登录</b>即生效
 * （权限快照缓存被显式 evict，但旧 token 的 TokenGrant 快照冻结——见
 * {@link #permissionGrantTakesEffectOnReLoginOnly()}）；⑥ logout 后旧 token 401；⑦ 上述流量入
 * api_access_log、未预期异常入 api_error_log；⑧ Modulith 边界保持绿（ModulithBoundaryTest，
 * 本类不重复）；⑨ org 面类级 org:manage 门禁（mold-viewer 调 /app-api/org-units/tree 403，
 * mold-admin 200 对照——org 三控制器门禁的 HTTP 级实证）。
 *
 * <p><b>场景 5 的快照语义（本测试组要演示的边界）</b>：权限在<b>登录时</b>进 TokenGrant
 * 快照——改角色授权后旧 token 不含新权限（继续 403）；重新登录经 UserPermissionsQuery 重新
 * 装配（快照缓存已被 RoleService.assignPermissions 显式 evict，不会返回陈旧集）才携带
 * mold:create。token 内权限刷新机制属 Phase 3，本阶段不实现。
 *
 * <p>BoomProbeController：测试专用未预期异常端点（500 兜底 → api_error_log），栖身本测试类
 * （@Bean 注册，包无 .controller.app. → 不加 /app-api 前缀，路径 /test/boom——仍受
 * anyRequest().authenticated() 门禁，须带 token 才能触达）。
 */
@SpringBootTest(classes = {ForgeDemoApplication.class, DemoAuthFlowIntegrationTest.ProbeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DemoAuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Duration AWAIT = Duration.ofSeconds(10);

    /**
     * 权限字典 seed 固定 id（service-authorization V202611181300）。mold:read 与 seeder 共享同一
     * 常量（{@link DemoDataSeeder#MOLD_READ_PERMISSION_ID}，评审 Minor-6：单一来源防双份硬编码漂移）；
     * mold:create 仅本测试使用，就地声明。
     */
    private static final String MOLD_CREATE_PERMISSION_ID = "0194b480-0000-7000-8000-000000000004";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Boot 4 无 TestRestTemplate：直连 RestTemplate + 直通错误处理器（demo 既有范式），4xx/5xx 体可断言
    RestTemplate rest = noThrowRestTemplate();

    // ---- 场景 1：无 token → 401 FORGE-0401（安全链 entryPoint 统一体）----

    @Test
    void anonymousRequestIsRejectedWithForge401() {
        ResponseEntity<String> response = rest.getForEntity(url("/app-api/molds"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("FORGE-0401");
    }

    // ---- 场景 2：mfg-admin 登录 → token → POST /app-api/molds 200（@PreAuthorize mold:create）----

    @Test
    void mfgAdminLoginCreatesMold() {
        String token = login("t-mfg", "mfg-admin", "Admin@123");
        ResponseEntity<String> response = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                json("{\"name\":\"认证链模具\",\"specification\":\"两腔\"}", token), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("OK");
        assertThat((String) JsonPath.read(response.getBody(), "$.data.id")).isNotBlank();
    }

    // ---- 场景 3：双租户隔离经真实认证链——mfg token 可见自有模具，mold token 同 id 404 ----

    @Test
    void authenticatedTokensStayTenantIsolated() {
        String mfgToken = login("t-mfg", "mfg-admin", "Admin@123");
        String moldId = createMold(mfgToken, "隔离验证模具");

        ResponseEntity<String> mfgView = rest.exchange(url("/app-api/molds/" + moldId), HttpMethod.GET,
                bearer(mfgToken), String.class);
        assertThat(mfgView.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(mfgView.getBody(), "$.data.name")).isEqualTo("隔离验证模具");

        // 同一 id、同端点、另一租户的合法 token → 判别式过滤命不中 → MOLD-0404（跨租户不可见）
        String moldToken = login("t-mold", "mold-admin", "Admin@123");
        ResponseEntity<String> moldView = rest.exchange(url("/app-api/molds/" + moldId), HttpMethod.GET,
                bearer(moldToken), String.class);
        assertThat(moldView.getStatusCode().value()).isEqualTo(404);
        assertThat((String) JsonPath.read(moldView.getBody(), "$.code")).isEqualTo("MOLD-0404");
    }

    // ---- 场景 4：mold-viewer（VIEWER 角色，仅 mold:read）POST → 403 FORGE-0403 ----

    @Test
    void viewerWithoutCreateAuthorityGetsForge403() {
        String viewerToken = login("t-mold", "mold-viewer", "Viewer@123");
        ResponseEntity<String> response = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                json("{\"name\":\"越权尝试\",\"specification\":\"一腔\"}", viewerToken), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("FORGE-0403");
    }

    // ---- 场景 9：org 面类级 org:manage 门禁（最终评审 Important-1 的 HTTP 级实证）----

    /**
     * org 三控制器（org-units/positions/user-positions）类级
     * {@code @PreAuthorize("hasAuthority('org:manage')")}：mold-viewer（仅 mold:read）调
     * GET /app-api/org-units/tree → 403 FORGE-0403；对照 mold-admin（ADMIN 角色，字典全量含
     * org:manage）→ 200——证明 403 源自权限门禁而非端点/装配问题。注解存在性的防回归钉子
     * 在 organization 模块（OrgControllersPreAuthorizeAnnotationTest）；本场景补 HTTP 链实证。
     */
    @Test
    void orgFaceRequiresOrgManageAuthority() {
        String viewerToken = login("t-mold", "mold-viewer", "Viewer@123");
        ResponseEntity<String> denied = rest.exchange(url("/app-api/org-units/tree"), HttpMethod.GET,
                bearer(viewerToken), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat((String) JsonPath.read(denied.getBody(), "$.code")).isEqualTo("FORGE-0403");

        String adminToken = login("t-mold", "mold-admin", "Admin@123");
        ResponseEntity<String> allowed = rest.exchange(url("/app-api/org-units/tree"), HttpMethod.GET,
                bearer(adminToken), String.class);
        assertThat(allowed.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(allowed.getBody(), "$.code")).isEqualTo("OK");
    }

    // ---- 场景 5：角色授权变更 → 新登录生效；旧 token 快照冻结（Phase 3 才做 token 内刷新）----

    /**
     * 快照语义实证：mold-admin 给 VIEWER 角色加授 mold:create（PUT /app-api/roles/{id}/permissions，
     * 全量替换）后——
     * <ul>
     *   <li><b>旧 token 仍 403</b>：权限集在登录时冻结进 TokenGrant，授权变更不回写既有 token；</li>
     *   <li><b>新登录即 200</b>：RoleService.assignPermissions 已显式 evict 权限快照缓存
     *       （auth:perm:{userId}），重登经 UserPermissionsQuery 重新装配到含 mold:create 的集合
     *       ——缓存即时失效经新登录验证（未 evict 则重登仍命中陈旧快照 → 403）。</li>
     * </ul>
     *
     * <p><b>基线恢复（评审 Important-1）</b>：本场景对 VIEWER 的授权变更是污染性写——场景 4 与
     * 本场景开头的「viewer POST → 403」前置依赖 VIEWER 缺 mold:create 的种子基线。finally 段
     * PUT 回 [mold:read] 恢复基线（方法序无关化：任何执行序下其他场景都不应看到本场景的残留）；
     * 恢复失败仅在主体断言已通过时才报错（不掩盖主体失败——finally 抛异常会替换原始异常）。
     */
    @Test
    void permissionGrantTakesEffectOnReLoginOnly() {
        String staleToken = login("t-mold", "mold-viewer", "Viewer@123");
        ResponseEntity<String> before = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                json("{\"name\":\"授权前\",\"specification\":\"一腔\"}", staleToken), String.class);
        assertThat(before.getStatusCode().value()).isEqualTo(403);

        String adminToken = login("t-mold", "mold-admin", "Admin@123");
        String viewerRoleId = roleIdByCode(adminToken, "VIEWER");
        boolean bodyCompleted = false;
        try {
            ResponseEntity<String> grant = rest.exchange(url("/app-api/roles/" + viewerRoleId + "/permissions"),
                    HttpMethod.PUT, json("""
                            {"permissionIds":["%s","%s"]}"""
                            .formatted(DemoDataSeeder.MOLD_READ_PERMISSION_ID, MOLD_CREATE_PERMISSION_ID),
                            adminToken),
                    String.class);
            assertThat(grant.getStatusCode().value()).isEqualTo(200);

            // 旧 token：TokenGrant 快照冻结 → 依旧 403（要演示的语义，非缺陷）
            ResponseEntity<String> staleRetry = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                    json("{\"name\":\"旧token重试\",\"specification\":\"一腔\"}", staleToken), String.class);
            assertThat(staleRetry.getStatusCode().value()).isEqualTo(403);

            // 重新登录：快照缓存已 evict → 新 token 携带 mold:create → 200
            String refreshedToken = login("t-mold", "mold-viewer", "Viewer@123");
            ResponseEntity<String> after = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                    json("{\"name\":\"授权后\",\"specification\":\"一腔\"}", refreshedToken), String.class);
            assertThat(after.getStatusCode().value()).isEqualTo(200);
            bodyCompleted = true;
        } finally {
            ResponseEntity<String> restore = rest.exchange(url("/app-api/roles/" + viewerRoleId + "/permissions"),
                    HttpMethod.PUT, json("""
                            {"permissionIds":["%s"]}"""
                            .formatted(DemoDataSeeder.MOLD_READ_PERMISSION_ID), adminToken),
                    String.class);
            if (bodyCompleted && restore.getStatusCode().value() != 200) {
                throw new AssertionError("VIEWER baseline restore failed: " + restore.getBody());
            }
        }
    }

    // ---- 场景 6：logout 吊销当前 token → 旧 token 再用 401 ----

    @Test
    void logoutRevokesOldToken() {
        String token = login("t-mfg", "mfg-admin", "Admin@123");
        ResponseEntity<String> logout = rest.exchange(url("/app-api/auth/logout"), HttpMethod.POST,
                bearer(token), String.class);
        assertThat(logout.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> afterLogout = rest.exchange(url("/app-api/molds/any"), HttpMethod.GET,
                bearer(token), String.class);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
        assertThat((String) JsonPath.read(afterLogout.getBody(), "$.code")).isEqualTo("FORGE-0401");
    }

    // ---- 场景 7：上述流量入 api_access_log；未预期异常入 api_error_log ----

    /**
     * 审计落表断言以<b>唯一标记行</b>关联（评审 Minor-5：不做计数断言——计数无法证明是"本场景
     * 这次请求"落的行）：nanoTime 标记同时进入 POST 请求体（→ body_digest 列）与 GET 路径段
     * （→ path 列），断言携带该标记的行存在即唯一关联。401 不入访问表（日志过滤器在安全链后）。
     */
    @Test
    void authenticatedTrafficIsAudited() {
        String adminToken = login("t-mold", "mold-admin", "Admin@123");
        String marker = "authflow-audit-" + System.nanoTime();

        // 带体 POST（200，标记进 body_digest）+ 唯一路径 GET（业务 404，标记进 path）
        ResponseEntity<String> created = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                json("{\"name\":\"" + marker + "\",\"specification\":\"两腔\"}", adminToken), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        rest.exchange(url("/app-api/molds/" + marker), HttpMethod.GET, bearer(adminToken), String.class);

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> postRows = jdbcTemplate.queryForList(
                    "SELECT * FROM api_access_log WHERE path = '/app-api/molds' AND body_digest LIKE ?",
                    "%" + marker + "%");
            assertThat(postRows).as("本次带体 POST 的访问行（body_digest 含唯一标记）").isNotEmpty();
            assertThat(postRows.get(0).get("result_code")).isEqualTo(200);
            assertThat(postRows.get(0).get("tenant_id")).isNotNull(); // TokenAuthFilter 填充的上下文快照

            List<Map<String, Object>> getRows = jdbcTemplate.queryForList(
                    "SELECT * FROM api_access_log WHERE path = ?", "/app-api/molds/" + marker);
            assertThat(getRows).as("本次唯一路径 GET 的访问行").isNotEmpty();
            assertThat(getRows.get(0).get("result_code")).isEqualTo(404); // 业务 404 分支同样入表
        });

        // 未预期异常：测试专用端点抛 IllegalStateException → 500 兜底 → api_error_log 行
        ResponseEntity<String> boom = rest.exchange(url("/test/boom"), HttpMethod.GET,
                bearer(adminToken), String.class);
        assertThat(boom.getStatusCode().value()).isEqualTo(500);
        assertThat(boom.getBody()).contains("FORGE-0500");

        Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM api_error_log WHERE exception = ? AND message = ?",
                    IllegalStateException.class.getName(), "demo-authflow-boom");
            assertThat(rows).isNotEmpty();
            assertThat(rows.get(0).get("tenant_id")).isNotNull(); // TokenAuthFilter 填充的上下文快照
            assertThat(rows.get(0).get("stack_digest")).asString().contains("demo-authflow-boom");
        });
    }

    // ---- helpers ----

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** app 登录 → token（Result&lt;LoginResponse&gt; 包装，$.data.token）。 */
    private String login(String tenantCode, String username, String password) {
        return JsonPath.read(loginBody(tenantCode, username, password), "$.data.token");
    }

    private String loginBody(String tenantCode, String username, String password) {
        ResponseEntity<String> response = rest.postForEntity(url("/app-api/auth/login"),
                json("""
                        {"tenantCode":"%s","username":"%s","password":"%s"}"""
                        .formatted(tenantCode, username, password), null),
                String.class);
        assertThat(response.getStatusCode().value()).as("login %s/%s: %s", tenantCode, username,
                response.getBody()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.code"))
                .as("login %s/%s: %s", tenantCode, username, response.getBody()).isEqualTo("OK");
        return response.getBody();
    }

    private String createMold(String token, String name) {
        ResponseEntity<String> response = rest.exchange(url("/app-api/molds"), HttpMethod.POST,
                json("{\"name\":\"" + name + "\",\"specification\":\"两腔\"}", token), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JsonPath.read(response.getBody(), "$.data.id");
    }

    /** 当前租户角色列表按 code 找 id（GET /app-api/roles，需 role:manage——管理员 token）。 */
    private String roleIdByCode(String adminToken, String code) {
        ResponseEntity<String> response = rest.exchange(url("/app-api/roles"), HttpMethod.GET,
                bearer(adminToken), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> roles = JsonPath.read(response.getBody(), "$.data");
        return roles.stream()
                .filter(role -> code.equals(role.get("code")))
                .map(role -> (String) role.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("role not found: " + code));
    }

    private static HttpEntity<String> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<String> json(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return new HttpEntity<>(body, headers);
    }

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

    /** 测试专用探针：500 兜底分支（场景 7 的 api_error_log 载体），不进生产上下文。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        BoomController boomController() {
            return new BoomController();
        }
    }

    @RestController
    static class BoomController {

        @GetMapping("/test/boom")
        Result<Void> boom() {
            throw new IllegalStateException("demo-authflow-boom");
        }
    }
}
