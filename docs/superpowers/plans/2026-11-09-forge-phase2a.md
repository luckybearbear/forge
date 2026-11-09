# Forge Phase 2a（企业基础能力·基建批）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 Phase 2 基建批：JPA+租户隔离数据层（Testcontainers 实证）、Token 认证骨架、双端路由/body 重复读/Long→String、Redis 缓存落地、Modulith 事件 JDBC 化、demo 连库升级——结束即有可用中间产物。

**Architecture:** 在 Phase 1 reactor 上：重写 platform-data 为 JPA 基线（`@TenantId` 自动租户过滤），platform-security 升级为 TTL 上下文 + Token 机制 + Security 集成，新增 forge-data-starter / forge-security-starter，demo-app 从内存切 PostgreSQL（作为基建验证载体）。

**Tech Stack:** Phase 1 栈 + Hibernate 7（Boot BOM）· Flyway 10+（含 flyway-database-postgresql）· spring-boot-starter-security · spring-data-redis · TransmittableThreadLocal 2.14.5 · Testcontainers（postgres:18-alpine / redis:8-alpine）

**Spec:** `docs/superpowers/specs/2026-11-09-forge-foundation-phase2-design.md`（2b 业务批计划待本批验收后另立）

## Global Constraints

- groupId `io.github.luckybearbear`；`${revision}`（1.0.0-SNAPSHOT）；版本管理只在 forge-bom（根 pom 只留 spring-boot.version 供插件，Phase 1 终审已定）
- Java release 25；构建一律 `./mvnw` + `JAVA_HOME="$FORGE_JDK"`（路径文件 `.superpowers/sdd/<plan>/jdk25-path.txt`，若不存在则 `cat` Phase 1 的 `.superpowers/sdd/2026-11-09-forge-foundation-phase1/jdk25-path.txt`）
- **不引入 Lombok**；UTF-8；提交 conventional + 中文
- 包根 `io.github.luckybearbear.forge.*`；平台模块只依赖 JDK/三方/platform 内部，禁止业务依赖；**starter 间禁止互依**
- 租户上下文：`TenantContextHolder`（TTL）；平台租户 ID 常量 `"0"`；ignore = 平台视角（租户表恒空），不做跨租户全量
- 事件租户传播：payload 显式携带 + 监听器显式 set/finally clear（不依赖 TTL 传播）
- 集成测试 `@Testcontainers(disabledWithoutDocker = true)`；`@ServiceConnection`（需 `spring-boot-testcontainers`）
- 数据库迁移：各模块 `db/migration`、`V{yyyyMMddHHmm}__snake_case.sql`、一次性变更、域前缀表名
- 每任务：TDD（可测处）→ 全绿 → 单条提交

## Spec 修订（随本计划生效）

spec §6.1 入口 1 原文"TenantContextFilter(-120，在 Security 之后)恢复租户"存在顺序矛盾（-120 先于 -100 运行却依赖 -100 的认证结果）。修订：**TokenAuthFilter(-100) 认证同时填充租户上下文并 finally 清理**；`FilterOrder.TENANT_FILTER=-120` 段保留给未来 header 直传租户场景。（计划入库时同步改 spec 一行。）

---

### Task 1: BOM 扩容 + Docker 基线验证

**Files:**
- Modify: `forge-bom/pom.xml`（+transmittable-thread-local 2.14.5；+新 starter/模块占位不在此任务）
- Modify: `docker-compose.yml`（无变更则跳过；仅验证）

**Interfaces:** Produces: TTL 依赖可用；Docker/PG/Redis 在场证明（后续 Testcontainers 任务依赖）

- [ ] **Step 1: forge-bom dependencyManagement 增加依赖项（版本属性 `transmittable-thread-local.version=2.14.5`）**

```xml
      <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>transmittable-thread-local</artifactId>
        <version>${transmittable-thread-local.version}</version>
      </dependency>
```

- [ ] **Step 2: 验证 Boot BOM 管理 testcontainers/flyway（决定后续是否补管）**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q dependency:tree -pl forge-distributions/forge-demo-app -Dincludes="org.testcontainers:*" 2>&1 | head -5
# 再查 org.flywaydb:*（demo 未引 flyway，改为临时 forge-bom 里 import 验证或查 spring-boot-dependencies 4.1.2 的 pom 元数据）
# 结论写入报告：Boot BOM 管了哪些（testcontainers / flyway-core / flyway-database-postgresql / postgresql / spring-data-redis / spring-boot-starter-data-jpa / spring-boot-starter-security / spring-boot-testcontainers）；
# 缺的由 forge-bom 补条目（本任务直接补，版本以 Maven Central 实查为准）
```

- [ ] **Step 3: Docker 基线验证（后续 Testcontainers 的前提）**

```bash
docker compose --profile infra up -d && sleep 10
docker compose --profile infra ps   # postgres 与 redis 均 healthy/running
docker compose --profile infra down # 释放端口（后续测试用容器自带）
```

- [ ] **Step 4: Commit**

```bash
git add forge-bom/pom.xml && git commit -m "build(bom): TTL 依赖入册 + Boot BOM 管辖范围核实"
```

---

### Task 2: 租户上下文（platform-core 接口 + platform-security TTL 实现）

**Files:**
- Create: `forge-platform/forge-platform-core/src/main/java/io/github/luckybearbear/forge/platform/core/CurrentTenantSupplier.java`
- Create: `forge-platform/forge-platform-security/src/main/java/io/github/luckybearbear/forge/platform/security/TenantContextHolder.java`
- Create: 同包 `TenantContextHolderSupplier.java`、`TenantIgnore.java`
- Modify: `forge-platform/forge-platform-security/src/main/java/io/github/luckybearbear/forge/platform/security/ThreadLocalUserProvider.java`、`ThreadLocalTenantProvider.java`（ThreadLocal → TransmittableThreadLocal）
- Modify: `forge-platform/forge-platform-security/pom.xml`（+ttl）
- Test: `.../security/TenantContextHolderTest.java`

**Interfaces:**
- Consumes: 无新
- Produces:
  - `interface CurrentTenantSupplier { Optional<String> currentTenantId(); boolean ignore(); }`（core，供 platform-data/cache 消费，避免 data→security 耦合）
  - `final class TenantContextHolder`：常量 `String PLATFORM_TENANT_ID = "0"`；`setTenantId(String)/getTenantId():Optional<String>/getRequiredTenantId()/setIgnore(boolean)/isIgnore()/clear()`
  - `@TenantIgnore`（方法/类级注解，RUNTIME）
  - `TenantContextHolderSupplier implements CurrentTenantSupplier`（spring 无关 POJO）
  - Phase 1 两个 Provider 的公开 API 不变（内部换 TTL；Phase 1 测试必须原样全绿）

- [ ] **Step 1: 写失败测试**

`TenantContextHolderTest.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

    private final TenantContextHolderSupplier supplier = new TenantContextHolderSupplier();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void setGetClearRoundTrip() {
        TenantContextHolder.setTenantId("t-1");
        assertThat(supplier.currentTenantId()).contains("t-1");
        assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo("t-1");
        TenantContextHolder.clear();
        assertThat(supplier.currentTenantId()).isEmpty();
        assertThatThrownBy(TenantContextHolder::getRequiredTenantId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoreFlagAndSupplier() {
        TenantContextHolder.setIgnore(true);
        assertThat(supplier.ignore()).isTrue();
        TenantContextHolder.setIgnore(false);
        assertThat(supplier.ignore()).isFalse();
    }

    @Test
    void isolatedAcrossThreads() throws InterruptedException {
        TenantContextHolder.setTenantId("t-1");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertThat(TenantContextHolder.getTenantId()).isEmpty();
            } catch (Throwable e) { // Throwable：AssertionError extends Error
                failure.set(e);
            }
        });
        other.start();
        other.join();
        assertThat(failure.get()).isNull();
    }
}
```

- [ ] **Step 2: 确认失败**：`JAVA_HOME="$FORGE_JDK" ./mvnw -q clean -pl forge-platform/forge-platform-core,forge-platform/forge-platform-security test` → COMPILATION ERROR

- [ ] **Step 3: 实现**

`CurrentTenantSupplier.java`（platform-core）：

```java
package io.github.luckybearbear.forge.platform.core;

import java.util.Optional;

/** 当前租户供给接口：由 platform-security（TTL 实现）提供，platform-data/cache 消费，避免模块间耦合。 */
public interface CurrentTenantSupplier {

    Optional<String> currentTenantId();

    boolean ignore();
}
```

`TenantContextHolder.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import java.util.Optional;

/**
 * 租户上下文（TTL 承载）。Web 请求链由 TokenAuthFilter 填充并 finally 清理；
 * 异步/事件场景不依赖隐式传播——payload 显式携带租户，监听器显式 set/clear（spec §6.1）。
 * 平台视角：ignore=true 或无租户时解析为 PLATFORM_TENANT_ID（"0"，租户表恒空）。
 */
public final class TenantContextHolder {

    public static final String PLATFORM_TENANT_ID = "0";

    private static final ThreadLocal<String> TENANT = new TransmittableThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new TransmittableThreadLocal<>();

    private TenantContextHolder() {}

    public static void setTenantId(String tenantId) { TENANT.set(tenantId); }

    public static Optional<String> getTenantId() { return Optional.ofNullable(TENANT.get()); }

    public static String getRequiredTenantId() {
        return getTenantId().orElseThrow(() -> new IllegalStateException("no tenant in current context"));
    }

    public static void setIgnore(boolean ignore) { IGNORE.set(ignore); }

    public static boolean isIgnore() { return Boolean.TRUE.equals(IGNORE.get()); }

    public static void clear() {
        TENANT.remove();
        IGNORE.remove();
    }
}
```

`TenantContextHolderSupplier.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;

public class TenantContextHolderSupplier implements CurrentTenantSupplier {

    @Override
    public Optional<String> currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    @Override
    public boolean ignore() {
        return TenantContextHolder.isIgnore();
    }
}
```

`TenantIgnore.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 平台视角（spec §6.1）：忽略租户上下文 → 解析为 PLATFORM_TENANT_ID，租户表查询恒空。非跨租户全量。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TenantIgnore {
}
```

两个 Phase 1 Provider：`new ThreadLocal<>()` → `new TransmittableThreadLocal<>()`（import com.alibaba.ttl.TransmittableThreadLocal），其余不动。

`forge-platform-security/pom.xml` 依赖增：

```xml
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-core</artifactId>
    </dependency>
    <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>transmittable-thread-local</artifactId>
    </dependency>
```

- [ ] **Step 4: 全绿（含 Phase 1 原有测试）+ Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q clean -pl forge-platform/forge-platform-core,forge-platform/forge-platform-security test
git add forge-platform/forge-platform-core forge-platform/forge-platform-security && \
git commit -m "feat(platform-security): TTL 租户上下文 + @TenantIgnore + CurrentTenantSupplier 接口"
```

---

### Task 3: Token 机制（TokenStore SPI + 内存实现 + PasswordHasher）

**Files:**
- Create: `.../security/token/TokenGrant.java`、`StoredToken.java`、`TokenStore.java`、`InMemoryTokenStore.java`
- Create: `.../security/password/PasswordHasher.java`
- Test: `.../security/token/InMemoryTokenStoreTest.java`、`.../security/password/PasswordHasherTest.java`

**Interfaces:**
- Produces:
  - `record TokenGrant(String userId, String tenantId, String userType, Set<String> authorities)`
  - `record StoredToken(String token, TokenGrant grant, Instant expiresAt)`
  - `interface TokenStore { StoredToken create(TokenGrant grant, Duration ttl); Optional<StoredToken> resolve(String token); void revoke(String token); void refresh(String token, Duration ttl); }`
  - `class PasswordHasher { String hash(String raw); boolean matches(String raw, String encoded); }`（DelegatingPasswordEncoder，bcrypt 默认，前缀 `{bcrypt}`）

- [ ] **Step 1: 失败测试**

`InMemoryTokenStoreTest.java`：

```java
package io.github.luckybearbear.forge.platform.security.token;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenStoreTest {

    private final InMemoryTokenStore store = new InMemoryTokenStore();
    private final TokenGrant grant = new TokenGrant("u-1", "t-1", "MEMBER", Set.of("mold:create"));

    @Test
    void createResolveRoundTrip() {
        StoredToken created = store.create(grant, Duration.ofHours(1));
        assertThat(created.token()).isNotBlank();
        assertThat(store.resolve(created.token())).contains(created);
        assertThat(created.grant().authorities()).contains("mold:create");
    }

    @Test
    void revokedTokenUnresolvable() {
        StoredToken created = store.create(grant, Duration.ofHours(1));
        store.revoke(created.token());
        assertThat(store.resolve(created.token())).isEmpty();
    }

    @Test
    void expiredTokenUnresolvable() {
        StoredToken created = store.create(grant, Duration.ofMillis(150));
        waitQuietly(300);
        assertThat(store.resolve(created.token())).isEmpty();
    }

    @Test
    void refreshExtendsExpiry() {
        StoredToken created = store.create(grant, Duration.ofMillis(250));
        store.refresh(created.token(), Duration.ofHours(1));
        waitQuietly(300);
        assertThat(store.resolve(created.token())).isPresent();
    }

    private static void waitQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

`PasswordHasherTest.java`：

```java
package io.github.luckybearbear.forge.platform.security.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashAndMatch() {
        String encoded = hasher.hash("s3cret!");
        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(hasher.matches("s3cret!", encoded)).isTrue();
        assertThat(hasher.matches("wrong", encoded)).isFalse();
    }
}
```

- [ ] **Step 2: 确认失败** → `TokenStore` 等符号不存在
- [ ] **Step 3: 实现**

```java
package io.github.luckybearbear.forge.platform.security.token;

import java.util.Set;

/** 认证通过后的授权信息（进入 token 存储）。 */
public record TokenGrant(String userId, String tenantId, String userType, Set<String> authorities) {}
```

```java
package io.github.luckybearbear.forge.platform.security.token;

import java.time.Instant;

public record StoredToken(String token, TokenGrant grant, Instant expiresAt) {}
```

```java
package io.github.luckybearbear.forge.platform.security.token;

import java.time.Duration;
import java.util.Optional;

/** Token 存储 SPI：Redis 实现（2a starter 装配）/内存实现（测试与开发降级）。 */
public interface TokenStore {

    StoredToken create(TokenGrant grant, Duration ttl);

    Optional<StoredToken> resolve(String token);

    void revoke(String token);

    void refresh(String token, Duration ttl);
}
```

```java
package io.github.luckybearbear.forge.platform.security.token;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现：测试与无 Redis 开发降级用。生产必须 Redis。 */
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, StoredToken> store = new ConcurrentHashMap<>();
    private final IdGenerator idGenerator = new UuidV7Generator();

    @Override
    public StoredToken create(TokenGrant grant, Duration ttl) {
        String token = idGenerator.nextId();
        StoredToken stored = new StoredToken(token, grant, Instant.now().plus(ttl));
        store.put(token, stored);
        return stored;
    }

    @Override
    public Optional<StoredToken> resolve(String token) {
        return Optional.ofNullable(store.get(token)).filter(t -> t.expiresAt().isAfter(Instant.now()));
    }

    @Override
    public void revoke(String token) {
        store.remove(token);
    }

    @Override
    public void refresh(String token, Duration ttl) {
        store.computeIfPresent(token, (k, v) -> new StoredToken(k, v.grant(), Instant.now().plus(ttl)));
    }
}
```

```java
package io.github.luckybearbear.forge.platform.security.password;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 密码哈希（DelegatingPasswordEncoder，bcrypt 默认）。 */
public class PasswordHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String encoded) {
        return encoder.matches(raw, encoded);
    }
}
```

**注意**：`PasswordHasher` 引入 `org.springframework.security.crypto.*` —— platform-security 需加依赖 `org.springframework.security:spring-security-crypto`（Boot BOM 管版本，轻量无传递）。pom 增：

```xml
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-crypto</artifactId>
    </dependency>
```

- [ ] **Step 4: 全绿 + Commit** `feat(platform-security): TokenStore SPI（内存实现）+ PasswordHasher`

---

### Task 4: TokenAuthFilter + ForgeAuthentication + 401/403 统一输出

**Files:**
- Create: `.../security/auth/ForgeAuthentication.java`、`TokenAuthFilter.java`、`ForgeSecurityResponses.java`
- Test: `.../security/auth/TokenAuthFilterTest.java`

**Interfaces:**
- Consumes: Task 2 上下文、Task 3 TokenStore
- Produces:
  - `class ForgeAuthentication extends AbstractAuthenticationToken`：`getUserId()/getTenantId()/getUserType()`；静态工厂 `ForgeAuthentication.of(StoredToken, rawToken)`
  - `class TokenAuthFilter extends OncePerRequestFilter`：构造 `(TokenStore, Duration refreshTtl)`；无 Bearer 或无效 token → 直接放行（由链的 authenticated 规则拒绝）；有效 → SecurityContext + TenantContextHolder + ThreadLocalUserProvider 填充；`finally` 清理三处
  - `ForgeSecurityResponses.write401/403(HttpServletResponse, String message)`：JSON `Result`（FORGE-0401/0403，HTTP 401/403；FORGE-0403 处携带 HTTP 403）

- [ ] **Step 1: 失败测试**

`TokenAuthFilterTest.java`（standalone MockMvc + 手动 filter 语义测试）：

```java
package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.token.InMemoryTokenStore;
import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class TokenAuthFilterTest {

    private final TokenStore store = new InMemoryTokenStore();
    private final TokenAuthFilter filter = new TokenAuthFilter(store, Duration.ofHours(1));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        ThreadLocalUserProvider.clear();
    }

    @Test
    void validTokenFillsSecurityAndTenantContext() throws ServletException, IOException {
        StoredToken token = store.create(
                new TokenGrant("u-1", "t-1", "MEMBER", Set.of("mold:create")), Duration.ofHours(1));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-api/x");
        request.addHeader("Authorization", "Bearer " + token.token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain((req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(ForgeAuthentication.class);
            assertThat(((ForgeAuthentication) auth).getTenantId()).isEqualTo("t-1");
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo("t-1");
            assertThat(ThreadLocalUserProvider.currentUser()).contains(new CurrentUser("u-1", "u-1"));
        });

        filter.doFilter(request, response, chain);

        // finally 清理：链结束后三处均空
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isEmpty();
        assertThat(ThreadLocalUserProvider.currentUser()).isEmpty();
    }

    @Test
    void missingOrInvalidTokenPassesThroughUnauthenticated() throws ServletException, IOException {
        for (String header : new String[] {null, "Bearer invalid"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app-api/x");
            if (header != null) {
                request.addHeader("Authorization", header);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).as(header).isNull();
        }
    }

    @Test
    void validTokenIsRefreshed() throws ServletException, IOException {
        StoredToken token = store.create(
                new TokenGrant("u-1", "t-1", "MEMBER", Set.of()), Duration.ofMillis(200));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("Authorization", "Bearer " + token.token());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        waitQuietly(250); // 原过期点之后

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/x");
        second.addHeader("Authorization", "Bearer " + token.token());
        var chain = new MockFilterChain((req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull());
        filter.doFilter(second, new MockHttpServletResponse(), chain); // 续期后仍可认证
    }

    private static void waitQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

- [ ] **Step 2: 确认失败** → 符号不存在
- [ ] **Step 3: 实现**（注：`MockFilterChain(Servlet)` 单参构造接受一个 Servlet 作为链尾——用 lambda `MockFilterChain((req,res)->...)` 的写法若编译不过，改为定义一个实现 `javax.servlet`… 不，jakarta：`new MockFilterChain(new HttpServlet() { protected void service(...) {...} })` 匿名类内嵌断言；两种取其一，以能编译者为准并在报告注明）

`ForgeAuthentication.java`：

```java
package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.token.StoredToken;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ForgeAuthentication extends AbstractAuthenticationToken {

    private final String userId;
    private final String tenantId;
    private final String userType;
    private final Object credentials;

    private ForgeAuthentication(String userId, String tenantId, String userType,
                                Object credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.tenantId = tenantId;
        this.userType = userType;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    public static ForgeAuthentication of(StoredToken stored, String rawToken) {
        var authorities = stored.grant().authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
        return new ForgeAuthentication(stored.grant().userId(), stored.grant().tenantId(),
                stored.grant().userType(), rawToken, authorities);
    }

    @Override public Object getCredentials() { return credentials; }
    @Override public Object getPrincipal() { return userId; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getUserType() { return userType; }
}
```

`TokenAuthFilter.java`：

```java
package io.github.luckybearbear.forge.platform.security.auth;

import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 认证 + 租户上下文填充（spec §6.1 修订：合并原 TenantContextFilter 职责）。 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenStore tokenStore;
    private final Duration refreshTtl;

    public TokenAuthFilter(TokenStore tokenStore, Duration refreshTtl) {
        this.tokenStore = tokenStore;
        this.refreshTtl = refreshTtl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearer(request);
        if (token != null) {
            tokenStore.resolve(token).ifPresent(stored -> {
                tokenStore.refresh(token, refreshTtl); // 滑动续期
                SecurityContextHolder.getContext().setAuthentication(ForgeAuthentication.of(stored, token));
                ThreadLocalUserProvider.set(new CurrentUser(stored.grant().userId(), stored.grant().userId()));
                if (stored.grant().tenantId() != null) {
                    TenantContextHolder.setTenantId(stored.grant().tenantId());
                }
            });
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            ThreadLocalUserProvider.clear();
            TenantContextHolder.clear();
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
```

`ForgeSecurityResponses.java`（401/403 JSON；ObjectMapper 用 tools.jackson（Jackson 3），无 Spring 注入环境也能用）：

```java
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
```

（`tools.jackson.*` 类名以 classpath 实际为准——Boot 4 是 Jackson 3；若 `JsonMapper.builder()` API 名不同，用 `new JsonMapper()` 或 Boot 注入的 mapper，报告记录。）

- [ ] **Step 4: 全绿 + Commit** `feat(platform-security): TokenAuthFilter（认证+租户填充+滑动续期+finally 清理）`
  注：本任务 platform-security 需补依赖 `org.springframework:spring-web`（OncePerRequestFilter）与 `jakarta.servlet:jakarta.servlet-api`（provided）——以编译通过的最小集合为准，报告列出。

---

### Task 5: platform-data 重写（JPA 基线 + 租户解析器）

**Files:**
- Modify: `forge-platform/forge-platform-data/pom.xml`（spring-data-jdbc → spring-data-jpa + platform-core + platform-security 仅供…否：resolver 消费 CurrentTenantSupplier（core 接口）→ 依赖 platform-core 即可）
- Delete+Create: `.../data/AbstractAuditableEntity.java`（JPA 版重写）、新增 `AbstractTenantEntity.java`、`ForgeTenantIdentifierResolver.java`
- Modify: `AbstractAuditableEntityTest.java`（JPA 注解版）
- Modify: `forge-platform/forge-platform-data/README.md`

**Interfaces:**
- Produces:
  - `AbstractAuditableEntity`：`String id`(@Id/@PrePersist 自动 UUIDv7)、`Long version`(@Version)、审计四字段(@CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate + AuditingEntityListener)、`Boolean deleted`(@SQLRestriction("deleted = false") + @SQLDelete UPDATE 置 true)
  - `AbstractTenantEntity extends AbstractAuditableEntity`：`@TenantId String tenantId`
  - `ForgeTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String>`（构造注入 `CurrentTenantSupplier`；ignore 或空 → `"0"`；`validateExistingCurrentSessions()=false`）

- [ ] **Step 1: 失败测试**（重写 `AbstractAuditableEntityTest`；新增 resolver 测试）

```java
package io.github.luckybearbear.forge.platform.data;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractAuditableEntityTest {

    @Test
    void auditFieldsCarryJpaAndSpringDataAnnotations() throws Exception {
        assertThat(annotationOn("id", jakarta.persistence.Id.class)).isNotNull();
        assertThat(annotationOn("version", jakarta.persistence.Version.class)).isNotNull();
        assertThat(annotationOn("createdBy", CreatedBy.class)).isNotNull();
        assertThat(annotationOn("createdAt", CreatedDate.class)).isNotNull();
        assertThat(annotationOn("updatedBy", LastModifiedBy.class)).isNotNull();
        assertThat(annotationOn("updatedAt", LastModifiedDate.class)).isNotNull();
        assertThat(annotationOn("deleted", org.hibernate.annotations.SoftDelete.class).isPresent()
                || AbstractAuditableEntity.class.isAnnotationPresent(org.hibernate.annotations.SQLRestriction.class))
                .as("soft delete via @SQLRestriction on base class")
                .isTrue();
        assertThat(annotationOn("tenantId", org.hibernate.annotations.TenantId.class))
                .as("tenantId only on AbstractTenantEntity")
                .isNull(); // 本类无租户字段
        assertThat(annotationOnAbstractTenant("tenantId", org.hibernate.annotations.TenantId.class)).isNotNull();
    }

    @Test
    void prePersistAssignsUuidV7WhenIdMissing() {
        TestEntity entity = new TestEntity();
        entity.assignId();
        assertThat(entity.getId()).contains("-");
        assertThat(entity.getId().split("-")[2]).startsWith("7"); // UUID v7 version nibble
    }

    @Test
    void resolverMapsIgnoreAndEmptyToPlatformTenant() {
        var ignoring = supplierWith(Optional.empty(), true);
        var resolver = new ForgeTenantIdentifierResolver(ignoring);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("0");
        assertThat(resolver.validateExistingCurrentSessions()).isFalse();

        var tenanted = supplierWith(Optional.of("t-9"), false);
        assertThat(new ForgeTenantIdentifierResolver(tenanted).resolveCurrentTenantIdentifier()).isEqualTo("t-9");
    }

    private Object annotationOn(String field, Class<? extends java.lang.annotation.Annotation> type)
            throws NoSuchFieldException {
        return AbstractAuditableEntity.class.getDeclaredField(field).getAnnotation(type);
    }

    private Object annotationOnAbstractTenant(String field, Class<? extends java.lang.annotation.Annotation> type)
            throws NoSuchFieldException {
        return AbstractTenantEntity.class.getDeclaredField(field).getAnnotation(type);
    }

    private io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier supplierWith(
            Optional<String> tenantId, boolean ignore) {
        return new io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return tenantId; }
            @Override public boolean ignore() { return ignore; }
        };
    }

    static class TestEntity extends AbstractAuditableEntity {}
}
```

（注：`assignId` 为基类 `@PrePersist protected void assignId()`，测试直接调用；断言里取第 3 段 version nibble 的写法以 `UUID.fromString` 校验 version==7 为准更稳，实施时择优，语义不变。）

- [ ] **Step 2: 确认失败**（旧 SD-JDBC 基类删除后编译失败 → 顺势重写）
- [ ] **Step 3: 实现**

`forge-platform-data/pom.xml` 依赖：`spring-data-jpa`（替换 spring-data-jdbc）+ `forge-platform-core` + 测试栈不变。

`AbstractAuditableEntity.java`：

```java
package io.github.luckybearbear.forge.platform.data;

import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** JPA 审计基线（Phase 2 spec §6.3）：UUIDv7 主键自动赋值、乐观锁、审计、软删除。 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE {h-table} SET deleted = true WHERE id = ?")
public abstract class AbstractAuditableEntity {

    private static final IdGenerator ID_GENERATOR = new UuidV7Generator();

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private Long version;

    @CreatedBy
    @Column(name = "created_by", length = 36)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @PrePersist
    protected void assignId() {
        if (id == null) {
            id = ID_GENERATOR.nextId();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
```

（`{h-table}` 占位符在 Hibernate 7 的 @SQLDelete 中支持；若 Task 9 spike 实证不支持，fallback：去掉 `@SQLDelete`，软删除仅保留 `@SQLRestriction` 过滤 + service 层显式置位，README 记录差异——以 spike 结论为准。）

`AbstractTenantEntity.java`：

```java
package io.github.luckybearbear.forge.platform.data;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/** 租户实体基线：@TenantId 由 Hibernate 自动过滤/注入（spec §6.1 入口 2）。 */
@MappedSuperclass
public abstract class AbstractTenantEntity extends AbstractAuditableEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

`ForgeTenantIdentifierResolver.java`：

```java
package io.github.luckybearbear.forge.platform.data;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/** ignore/无租户 → 平台租户 "0"（租户表恒空 = 平台视角，spec §6.1）。 */
public class ForgeTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String PLATFORM_TENANT_ID = "0";

    private final CurrentTenantSupplier supplier;

    public ForgeTenantIdentifierResolver(CurrentTenantSupplier supplier) {
        this.supplier = supplier;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (supplier.ignore()) {
            return PLATFORM_TENANT_ID;
        }
        return supplier.currentTenantId().orElse(PLATFORM_TENANT_ID);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
```

README 重写（职责：JPA 基线/两基类/resolver/软删除/Flyway 约定；状态 ✅）。

- [ ] **Step 4: 全绿 + Commit** `feat(platform-data): 重写为 JPA 基线（@TenantId 实体 + 审计 + 软删除 + 租户解析器）`

---

### Task 6: platform-web 三件套（body 重复读 / Long→String / 乐观锁 409）

**Files:**
- Create: `.../web/CacheRequestBodyFilter.java`、`JacksonLongToStringModule.java`
- Modify: `.../web/GlobalExceptionHandler.java`（+ObjectOptimisticLockingFailureException 分支）
- Test: `CacheRequestBodyFilterTest.java`、`JacksonLongToStringModuleTest.java`、`GlobalExceptionHandlerTest.java`（追加用例）

**Interfaces:**
- Produces:
  - `CacheRequestBodyFilter`（包装 ContentCachingRequestWrapper；构造 `(long maxBodySize, Set<String> excludeUrls)`；无 Spring 依赖）
  - `JacksonLongToStringModule`（`com.fasterxml…` 不——Jackson 3：`tools.jackson.databind.module.SimpleModule` 子类，Long/long → String）
  - handler 分支：`ObjectOptimisticLockingFailureException`（org.springframework.orm.ObjectOptimisticLockingFailureException）→ HTTP 409 + `FORGE-0423`

- [ ] **Step 1: 失败测试**

`CacheRequestBodyFilterTest.java`：

```java
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
            cached.getReader(); // 触发缓存
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
```

`JacksonLongToStringModuleTest.java`：

```java
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
```

`GlobalExceptionHandlerTest` 追加：

```java
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
```

- [ ] **Step 2: 确认失败** → Step 3: 实现

`CacheRequestBodyFilter.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** 请求体可重复读（访问日志/审计前置）。排除 URL 不包装。 */
public class CacheRequestBodyFilter extends OncePerRequestFilter {

    private final long maxBodySize;
    private final Set<String> excludeUrls;

    public CacheRequestBodyFilter(long maxBodySize, Set<String> excludeUrls) {
        this.maxBodySize = maxBodySize;
        this.excludeUrls = excludeUrls;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludeUrls.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, (int) maxBodySize);
        filterChain.doFilter(wrapper, response);
    }
}
```

`JacksonLongToStringModule.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/** Long → String 序列化（防 JS 精度丢失，spec §6.5）。 */
public class JacksonLongToStringModule extends SimpleModule {

    public JacksonLongToStringModule() {
        super("forge-long-to-string");
        addSerializer(Long.class, new LongToStringSerializer(Long.class));
        addSerializer(Long.TYPE, new LongToStringSerializer(Long.TYPE));
    }

    static final class LongToStringSerializer extends StdSerializer<Long> {
        LongToStringSerializer(Class<?> type) { super(type, false); }
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(String.valueOf(value));
        }
    }
}
```

（Jackson 3 `StdSerializer` 构造与 `addSerializer` 签名以实际 API 为准——如构造参数/受检异常差异，等价调整并记录；语义固定：Long/long → JSON string。）

`GlobalExceptionHandler` 追加分支（import `org.springframework.orm.ObjectOptimisticLockingFailureException`）：

```java
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(409).body(Result.error(ErrorCodes.CONCURRENT_LOCKED));
    }
```

- [ ] **Step 4: 全绿 + Commit** `feat(platform-web): body 重复读 + Long→String + 乐观锁 409 映射`

---

### Task 7: platform-web 双端前缀路由

**Files:**
- Create: `.../web/ForgeApiPrefixConfiguration.java`（@Configuration，实现 WebMvcRegistrations）+ `ForgeApiPrefixProperties.java`（record + @ConfigurationProperties 或简单属性类——platform-web 无 Boot 配置绑定依赖时用手动 properties 传入；starter 侧绑定后传入构造）
- Test: `.../web/ApiPrefixMappingTest.java`（@SpringBootTest RANDOM_PORT 最小应用）

**Interfaces:**
- Produces:
  - `ForgeApiPrefixConfiguration implements WebMvcRegistrations`：`new ForgeApiPrefixRequestMappingHandlerMapping(appPrefix, adminPrefix, enabled)`；属性：`appPrefix="/app-api"`、`adminPrefix="/admin-api"`、`enabled=true`（由 starter 的 `forge.web.api-prefix.*` 绑定后传入；本模块提供类与映射逻辑）
  - `ForgeApiPrefixRequestMappingHandlerMapping extends RequestMappingHandlerMapping`：包名含 `.controller.admin.` → admin 前缀；`.controller.app.` → app 前缀；其余不加

- [ ] **Step 1: 失败测试**

```java
package io.github.luckybearbear.forge.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.RestTemplate? -- Boot 4 无 TestRestTemplate（Phase 1 结论），用 RestTemplate + @LocalServerPort + 透传 handler（Phase 1 StarterSmokeTest 模式）
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiPrefixMappingTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiPrefixMappingTest {

    @SpringBootApplication
    static class TestApp {
        @RestController
        static class AppSideController {          // 测试内包：io.github...web.appside? 需要包名含 .controller.app. —— 测试类放子包 controller.app / controller.admin
            @GetMapping("/ping") String ping() { return "app"; }
        }
        @RestController
        static class AdminSideController {
            @GetMapping("/ping") String ping() { return "admin"; }
        }
        @RestController
        static class PlainController {
            @GetMapping("/plain/ping") String ping() { return "plain"; }
        }
    }

    /* 集成断言：
       GET {port}/app-api/ping  → "app"
       GET {port}/admin-api/ping → "admin"
       GET {port}/plain/ping    → "plain"（不带前缀的包不受影响）
       404：GET {port}/api/ping */
    @Test
    void prefixesAppliedByPackage() { /* 上断言逐一实现 */ }
}
```

（内嵌类的包匹配需真实包名含 `.controller.app.` / `.controller.admin.`：测试源放 `.../web/controller/app/AppSideController.java` 与 `.../web/controller/admin/AdminSideController.java` 两个顶层测试类，TestApp 扫描同包。实施按此组织，断言用 RestTemplate+@LocalServerPort。）

- [ ] **Step 2: 确认失败** → Step 3: 实现

`ForgeApiPrefixConfiguration.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import org.springframework.boot.autoconfigure.web.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 双端前缀（spec §6.5）：controller.admin.* → /admin-api，controller.app.* → /app-api。 */
@Configuration
public class ForgeApiPrefixConfiguration {

    @Bean
    public WebMvcRegistrations forgeApiPrefixWebMvcRegistrations(ForgeApiPrefixProperties properties) {
        return () -> new ForgeApiPrefixRequestMappingHandlerMapping(properties);
    }

    public record ForgeApiPrefixProperties(boolean enabled, String appPrefix, String adminPrefix) {
        public static ForgeApiPrefixProperties defaults() {
            return new ForgeApiPrefixProperties(true, "/app-api", "/admin-api");
        }
    }
}
```

`ForgeApiPrefixRequestMappingHandlerMapping.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.lang.reflect.Method;

public class ForgeApiPrefixRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private static final String APP_PATTERN = ".controller.app.";
    private static final String ADMIN_PATTERN = ".controller.admin.";

    private final ForgeApiPrefixConfiguration.ForgeApiPrefixProperties properties;

    public ForgeApiPrefixRequestMappingHandlerMapping(
            ForgeApiPrefixConfiguration.ForgeApiPrefixProperties properties) {
        this.properties = properties;
    }

    @Override
    protected String[] getPathPrefixes() { // Spring 6+ 钩子：按类返回前缀
        return properties.enabled() ? new String[0] : new String[0]; // 占位不用——见 getPrefixFor
    }

    // Spring 6.1+ 提供正式钩子：protected String getPrefixFor(Class<?> handlerType)？若无此钩子，
    // 覆写 getMappingForMethod 后在 RequestMapping 上 prepend prefix。
    // 实施取 Spring Framework 7 实际可用的方式之一：
    //   (a) getPrefixFor(Class) —— 若存在则最简；
    //   (b) 覆写 getMappingForMethod：RequestMappingInfo.paths 前插前缀。
    // 以 (b) 为兜底标准写法，编译通过为准，报告记录所用钩子。
}
```

（**实施说明**：Spring Framework 6.1+ 的 `RequestMappingHandlerMapping` 提供 `configurePathPrefix/Predicate+Function` 注册方式；Boot 的 `WebMvcRegistrations` 场景下最稳的是覆写 `getMappingForMethod`：当 `handlerType.getPackageName().contains(".controller.app.")` 时给 `RequestMappingInfo` 加 `paths(appPrefix)` 前缀。以编译+测试双通过为准，两种钩子任选，报告记录。）

- [ ] **Step 4: 全绿 + Commit** `feat(platform-web): admin/app 双端包前缀路由`

---

### Task 8: platform-cache 落地（Redis 门面 + 租户 key）

**Files:**
- Modify: `forge-platform/forge-platform-cache/pom.xml`（+spring-data-redis + platform-core + platform-security(仅 CurrentTenantSupplier→core 即可，不需要 security) —— key 规则用 CurrentTenantSupplier（core）✓）
- Create: `.../cache/TenantCacheKeys.java`、`RedisCacheFacade.java`、`TenantRedisCacheManager.java`
- Test: `TenantCacheKeysTest.java`、`RedisCacheFacadeTest.java`（mock RedisTemplate）

**Interfaces:**
- Produces:
  - `TenantCacheKeys`：`String tenantKey(String key, CurrentTenantSupplier supplier)`（`t:{tenantId}:{key}`；ignore/无租户 → `p:{key}`）；`String cacheName(String name, CurrentTenantSupplier supplier)` 同规则
  - `RedisCacheFacade implements CacheFacade`（构造 `RedisTemplate<String,Object>` + supplier；put/get/evict）
  - `TenantRedisCacheManager extends RedisCacheManager`（覆写 `getCache(String)`/`createCache` 以 `cacheName()` 前缀——若内部 API 抵触，降级为仅提供 `TenantCacheKeyGenerator`（@Cacheable key 生成器）并在 README 记录降级，CacheManager 全量集成移 2b）

- [ ] **Step 1: 失败测试**（key 规则 + facade 委托）

`TenantCacheKeysTest.java`：

```java
package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TenantCacheKeysTest {

    @Test
    void tenantPrefixApplied() {
        assertThat(TenantCacheKeys.tenantKey("user:1", supplier("t-1", false))).isEqualTo("t:t-1:user:1");
        assertThat(TenantCacheKeys.cacheName("perms", supplier("t-1", false))).isEqualTo("t:t-1:perms");
    }

    @Test
    void platformViewWithoutPrefix() {
        assertThat(TenantCacheKeys.tenantKey("cfg", supplier(null, true))).isEqualTo("p:cfg");
        assertThat(TenantCacheKeys.tenantKey("cfg", supplier(null, false))).isEqualTo("p:cfg");
    }

    private CurrentTenantSupplier supplier(String tenantId, boolean ignore) {
        return new CurrentTenantSupplier() {
            @Override public Optional<String> currentTenantId() { return Optional.ofNullable(tenantId); }
            @Override public boolean ignore() { return ignore; }
        };
    }
}
```

`RedisCacheFacadeTest.java`：mockito mock `RedisTemplate<String,Object>`，验证 put→opsForValue().set(tenantKey, value, ttl)、get→opsForValue().get、evict→delete（key 断言用 TenantCacheKeys 结果）。

- [ ] **Step 2/3/4:** 实现 → 全绿 → Commit `feat(platform-cache): Redis 门面 + 租户 key 规则（CacheManager 视实现降级记录）`

`TenantCacheKeys.java`：

```java
package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;

/** 租户 key 规则（spec §6.1 入口 3）。 */
public final class TenantCacheKeys {

    private TenantCacheKeys() {}

    public static String tenantKey(String key, CurrentTenantSupplier supplier) {
        return supplier.ignore()
                ? "p:" + key
                : supplier.currentTenantId().map(t -> "t:" + t + ":" + key).orElse("p:" + key);
    }

    public static String cacheName(String name, CurrentTenantSupplier supplier) {
        return tenantKey(name, supplier);
    }
}
```

`RedisCacheFacade.java`：

```java
package io.github.luckybearbear.forge.platform.cache;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;

/** CacheFacade 的 Redis 实现（Phase 1 SPI 兑现）。 */
public class RedisCacheFacade implements CacheFacade {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CurrentTenantSupplier tenantSupplier;

    public RedisCacheFacade(RedisTemplate<String, Object> redisTemplate, CurrentTenantSupplier tenantSupplier) {
        this.redisTemplate = redisTemplate;
        this.tenantSupplier = tenantSupplier;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(TenantCacheKeys.tenantKey(key, tenantSupplier), value, ttl);
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(TenantCacheKeys.tenantKey(key, tenantSupplier)));
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(TenantCacheKeys.tenantKey(key, tenantSupplier));
    }
}
```

`TenantRedisCacheManager.java`：extends RedisCacheManager，构造透传，覆写 `getCache(String name)` → `super.getCache(TenantCacheKeys.cacheName(name, supplier))`、`getCache(name, factory)` 同理；与 RedisCacheManager 内部 cache map 兼容性以编译+starter 集成测试为准，若覆写点不可达（private final），降级方案照 Interfaces 节说明执行并记录。

---

### Task 9: forge-data-starter（含全计划最关键 spike：Testcontainers 双租户隔离）

**Files:**
- Create: `forge-starters/forge-data-starter/pom.xml`、`forge-starters/forge-data-starter/src/main/java/io/github/luckybearbear/forge/starter/data/ForgeDataAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `src/test/java/io/github/luckybearbear/forge/starter/data/DataStarterIntegrationTest.java`、`src/test/java/.../TestTenantEntity.java`、`TestTenantRepository.java`、`src/test/resources/db/migration/V202611120000__create_test_tenant_entity.sql`
- Modify: `forge-starters/pom.xml`（挂模块）、`forge-bom/pom.xml`（starter 入册）
- Modify: `forge-platform/forge-platform-test/`：新增 `AbstractIntegrationTest.java` + `TenantTestSupport.java`（本任务一并交付，pom + spring-boot-testcontainers + testcontainers junit-jupiter，test scope）

**Interfaces:**
- Consumes: Task 2/3/4/5/8 产物
- Produces:
  - `ForgeDataAutoConfiguration`（@AutoConfiguration）：`CurrentTenantSupplier` 缺省 Bean（无则提供 `PlatformTenantSupplier`（恒 empty/ignore=false））；`ForgeTenantIdentifierResolver` Bean（@ConditionalOnMissingBean）；`HibernatePropertiesCustomizer` 注册 `hibernate.tenant_identifier_resolver`（常量 `org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER`，若常量名不存在用字符串字面量）；`AuditorAware<String>`（读 `CurrentUserProvider`（@ConditionalOnMissingBean CurrentUserProvider 时提供 empty 实现）→ orElse("system")）+ `@EnableJpaAuditing`；`IdGenerator` Bean（@ConditionalOnMissingBean，UuidV7Generator——Phase 1 终审遗留项兑现）；EPP 默认值 `spring.modulith.events.jdbc.schema-initialization.enabled=true`
  - `AbstractIntegrationTest`（platform-test）：`@Testcontainers(disabledWithoutDocker = true)` + 静态 `PostgreSQLContainer<>("postgres:18-alpine")` + `GenericContainer<>("redis:8-alpine").withExposedPorts(6379)`，均 `@ServiceConnection`
  - `TenantTestSupport.runAs(String tenantId, Runnable)`（set/finally clear）

- [ ] **Step 1: pom**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-starters</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-data-starter</artifactId>
  <name>Forge :: Starter :: Data</name>
  <dependencies>
    <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-data</artifactId></dependency>
    <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-starter-jdbc</artifactId></dependency>
    <!-- tests -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

（flyway 两件与 modulith-jdbc 版本均由 Boot/Modulith BOM 管；Task 1 核实若有缺，forge-bom 补。）

`forge-platform-test/pom.xml` 追加（test scope 供继承使用——注意 platform-test 的 junit/assertj 为 compile，这两个也 compile，供下游 test 引用传递）：

```xml
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId></dependency>
```

- [ ] **Step 2: 写失败集成测试（THE SPIKE）**

```java
package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import io.github.luckybearbear.forge.platform.test.TenantTestSupport;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DataStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {}

    @Autowired TestTenantRepository repository;

    @Test
    void tenantIsolationAutoApplied() {
        TestTenantEntity a = TenantTestSupport.runAsResult("t-a",
                () -> repository.save(new TestTenantEntity("A-data")));
        TenantTestSupport.runAs("t-b", () -> repository.save(new TestTenantEntity("B-data")));

        List<TestTenantEntity> seenByA = TenantTestSupport.runAsResult("t-a", () -> repository.findAll());
        List<TestTenantEntity> seenByB = TenantTestSupport.runAsResult("t-b", () -> repository.findAll());
        List<TestTenantEntity> platformView = TenantTestSupport.runAsPlatform(() -> repository.findAll());

        assertThat(seenByA).extracting(TestTenantEntity::getName).containsExactly("A-data");
        assertThat(seenByB).extracting(TestTenantEntity::getName).containsExactly("B-data");
        assertThat(platformView).isEmpty(); // 平台视角恒空（spec §6.1）
        assertThat(a.getCreatedAt()).isNotNull();      // 审计自动填充
        assertThat(a.getCreatedBy()).isEqualTo("system"); // 无认证上下文 → system
        assertThat(a.getTenantId()).isEqualTo("t-a");  // @TenantId 注入
    }

    @Test
    void softDeleteFiltersQueries() {
        TestTenantEntity saved = TenantTestSupport.runAsResult("t-a",
                () -> repository.save(new TestTenantEntity("to-delete")));
        TenantTestSupport.runAs("t-a", () -> repository.deleteById(saved.getId()));
        List<TestTenantEntity> rest = TenantTestSupport.runAsResult("t-a", () -> repository.findAll());
        assertThat(rest).isEmpty(); // @SQLRestriction 过滤（DB 里行仍在，deleted=true）
    }
}

@Entity
@Table(name = "data_starter_test_entity")
class TestTenantEntity extends io.github.luckybearbear.forge.platform.data.AbstractTenantEntity {
    @jakarta.persistence.Column(name = "name") private String name;
    @jakarta.persistence.Column(name = "recorded_at") private Instant recordedAt = Instant.now();
    TestTenantEntity(String name) { this.name = name; }
    protected TestTenantEntity() {} // JPA
    public String getName() { return name; }
}

interface TestTenantRepository extends JpaRepository<TestTenantEntity, String> {}
```

`db/migration/V202611120000__create_data_starter_test_entity.sql`：

```sql
CREATE TABLE data_starter_test_entity (
    id           VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    version      BIGINT,
    created_by   VARCHAR(36),
    created_at   TIMESTAMPTZ,
    updated_by   VARCHAR(36),
    updated_at   TIMESTAMPTZ,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dstest_tenant ON data_starter_test_entity (tenant_id);
```

`TenantTestSupport.java`（platform-test）：

```java
package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import java.util.function.Supplier;

public final class TenantTestSupport {

    public static void runAs(String tenantId, Runnable action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    public static <T> T runAsResult(String tenantId, Supplier<T> action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    public static <T> T runAsPlatform(Supplier<T> action) {
        TenantContextHolder.setIgnore(true);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantTestSupport() {}
}
```

`AbstractIntegrationTest.java`：

```java
package io.github.luckybearbear.forge.platform.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 集成测试基类：PG + Redis 真实容器；无 Docker 环境 skip（disabledWithoutDocker）。 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
}
```

（Redis 容器在 data-starter 测试其实未用——保留统一基座供 security-starter/demo 复用；`@ServiceConnection` 对 GenericContainer+redis 镜像生成 RedisConnectionDetails，Boot 支持。）

- [ ] **Step 3: 确认失败**（starter 未实现 → 上下文起不来/符号缺失）
- [ ] **Step 4: 实现 ForgeDataAutoConfiguration**

```java
package io.github.luckybearbear.forge.starter.data;

import io.github.luckybearbear.forge.platform.core.CurrentTenantSupplier;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import io.github.luckybearbear.forge.platform.data.ForgeTenantIdentifierResolver;
import io.github.luckybearbear.forge.platform.security.CurrentUserProvider;
import io.github.luckybearbear.forge.platform.security.TenantContextHolderSupplier;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.hibernate5.SpringBeanContainer? -- 不需要。删除该行（Hibernate 7 无此用法）。

@AutoConfiguration
@EnableJpaAuditing(dateTimeProviderRef = "forgeDateTimeProvider")
public class ForgeDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentTenantSupplier.class)
    public CurrentTenantSupplier forgeTenantSupplier() {
        return new TenantContextHolderSupplier(); // TTL 上下文恒可用
    }

    @Bean
    @ConditionalOnMissingBean
    public ForgeTenantIdentifierResolver forgeTenantIdentifierResolver(CurrentTenantSupplier supplier) {
        return new ForgeTenantIdentifierResolver(supplier);
    }

    @Bean
    public org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
            forgeTenantHibernateCustomizer(ForgeTenantIdentifierResolver resolver) {
        return properties -> properties.put(
                org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> forgeAuditorAware(Optional<CurrentUserProvider> userProvider) {
        return () -> userProvider.map(CurrentUserProvider::currentUser)
                .orElse(Optional.empty())
                .map(io.github.luckybearbear.forge.platform.security.CurrentUser::userId)
                .map(java.util.Optional::of)
                .orElseGet(() -> Optional.of("system"));
    }

    @Bean
    public DateTimeProvider forgeDateTimeProvider() {
        return () -> Optional.of(java.time.Instant.now());
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator forgeIdGenerator() {
        return new UuidV7Generator();
    }
}
```

（`AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER` 常量名/Hibernate 7 中 resolver 注册方式若与上面不符——以 `dependency:tree` 定位 hibernate-core 后 `jar tf`+`javap` 实证可用注册点，等价实现并记录；`@EnableJpaAuditing` 放 starter 的效果（应用未引 data-starter 时无 JPA——正确）✓。）

imports 文件：

```
io.github.luckybearbear.forge.starter.data.ForgeDataAutoConfiguration
```

- [ ] **Step 5: 跑 spike（Docker 必须）**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q clean -pl forge-starters/forge-data-starter -am test
# 预期：容器测试 2 个全绿。若 @TenantId 未生效（B 能看到 A 数据）：
#   1) 检查 resolver 是否真正注册（日志 hibernate.tenant_identifier_resolver）
#   2) Hibernate 7 discriminator 需要的额外配置（如 AvailableSettings.MULTI_TENANCY=DISCRIMINATOR？以实证为准）
#   3) @SQLDelete 占位符 {h-table} 若报错 → 按 Task 5 fallback 去注解、保留 @SQLRestriction，测试断言改为验证过滤（deleteById 走硬删或 @Modifying——记录差异）
# 任一 fallback 都必须在报告与 README 记录最终机制
```

- [ ] **Step 6: BOM/聚合挂载 + README + Commit** `feat(data-starter): JPA+租户装配（Testcontainers 双租户隔离实证）+ platform-test 容器基类`

---

### Task 10: forge-security-starter

**Files:**
- Create: `forge-starters/forge-security-starter/pom.xml`、`src/main/java/io/github/luckybearbear/forge/starter/security/ForgeSecurityAutoConfiguration.java`、`ForgeSecurityProperties.java`、`RedisTokenStore.java`、`TenantIgnoreAspect.java`
- Create: `META-INF/spring/...AutoConfiguration.imports`
- Test: `SecurityStarterIntegrationTest.java`（extends AbstractIntegrationTest，真实 Redis）

**Interfaces:**
- Produces（应用引本 starter 即得）:
  - `TokenStore` Bean：Redis 在类路径且未自定义 → `RedisTokenStore`（`auth:token:{token}` hash，TTL）；否则 `InMemoryTokenStore` + 启动 warn
  - `TokenAuthFilter` 注入 Security 链（`addFilterBefore(AuthorizationFilter.class)`），不重复注册为全局 Filter
  - 默认 `SecurityFilterChain`：stateless/CSRF off；permitAll：`forge.security.permit-all`（默认 `/app-api/auth/login,/admin-api/auth/login,/actuator/health,/v3/api-docs/**,/swagger-ui/**`）+ 其余 authenticated；401/403 → `ForgeSecurityResponses`
  - `@TenantIgnore` AOP：进入标注方法 `setIgnore(true)`、`finally` 还原
  - 属性：`forge.security.token-ttl=8h`、`forge.security.permit-all`

- [ ] **Step 1: pom**（依赖：spring-boot-starter-security、spring-boot-starter-aop、platform-security、spring-data-redis、platform-core；test：starter-test + platform-test + starter-web?—— 测试应用需要 web 端点：test 依赖 forge-spring-boot-starter）

- [ ] **Step 2: 失败测试**

```java
package io.github.luckybearbear.forge.starter.security;

import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.token.TokenGrant;
import io.github.luckybearbear.forge.platform.security.token.TokenStore;
import io.github.luckybearbear.forge.platform.test.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SecurityStarterIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityStarterIntegrationTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        @RestController
        static class ProbeController {
            @GetMapping("/probe/secure")
            String secure() {
                return "tenant=" + TenantContextHolder.getTenantId().orElse("none");
            }

            @GetMapping("/probe/open")
            @io.github.luckybearbear.forge.platform.security.TenantIgnore
            String open() {
                return "ignore=" + TenantContextHolder.isIgnore();
            }
        }
    }

    @Autowired TokenStore tokenStore;
    @LocalServerPort int port;
    /* RestTemplate + 透传 handler（Phase 1 模式），baseUrl=http://localhost:+port */

    @Test
    void unauthenticatedGets401Result() { /* GET /probe/secure → 401 + $.code=FORGE-0401 */ }

    @Test
    void validTokenAuthenticatesAndFillsTenant() {
        /* tokenStore.create(grant("u-1","t-7",Set.of("x")), 8h) → GET /probe/secure 带 Bearer
           → 200 + body contains tenant=t-7 */
    }

    @Test
    void revokedTokenRejected() { /* revoke 后同 token → 401 */ }

    @Test
    void tenantIgnoreAnnotationActivatesPlatformView() { /* GET /probe/open（放行路径加入 permit-all 或以 token 访问）→ ignore=true */ }
}
```

- [ ] **Step 3: 确认失败** → 实现（RedisTokenStore：`RedisTemplate<String,Object>` hash 结构存 TokenGrant 字段 + expiresAt，TTL=ttl；resolve 校验未过期；refresh 重设 TTL——hash 无整体 TTL 便捷法则改 String JSON 值 + TTL，取实现最简者，语义对齐接口即可，报告记录结构）
- [ ] **Step 4: 全绿（需 Docker）+ BOM/聚合挂载 + README + Commit** `feat(security-starter): SecurityFilterChain + RedisTokenStore + @TenantIgnore AOP`

---

### Task 11: demo-app 连库升级（双租户 + 事件恢复）

**Files:**
- Modify: `forge-distributions/forge-demo-app/pom.xml`（+forge-data-starter；契约依赖不变）
- Delete: `.../demo/mold/domain/MoldEntity.java`（record 版）、`MoldRegistry.java`；maintenance 同
- Create: `.../mold/domain/MoldJpaEntity.java`（extends AbstractTenantEntity）、`MoldRepository.java`；maintenance 对应两文件
- Create: `src/main/resources/db/migration/V202611130000__create_demo_tables.sql`
- Modify: `MoldService/MaintenanceService`（注册表→repository；请求/响应模型不变）、`MoldController/MaintenanceController`（迁入 `api/controller.app` 包结构——即 `mold/controller/app/`、`maintenance/controller/app/`，路径变 `/app-api/molds` 等）、`MoldRepairRequestedListener`（@EventListener → @ApplicationModuleListener）
- Modify: `application.yaml`（PG/Redis/Flyway/JPA validate/modulith schema-init）
- Modify: 全部既有 demo 测试（内存→容器集成；MoldServiceTest 改用 mock repository 或并入集成测试，以实施顺为准）
- Test: `DemoTenantIsolationIntegrationTest.java`

**Interfaces:**
- Consumes: data-starter 全部；契约事件 payload 补 `tenantId` 字段（**契约变更**：`MoldRepairRequested` 增 `String tenantId` 组件——breaking，Phase 1 契约仅 demo 消费，同步改）

- [ ] **Step 1: 迁移脚本**

```sql
CREATE TABLE mold (
    id            VARCHAR(36) PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    specification VARCHAR(256),
    status        VARCHAR(32) NOT NULL,
    created_at    TIMESTAMPTZ,
    created_by    VARCHAR(36),
    updated_at    TIMESTAMPTZ,
    updated_by    VARCHAR(36),
    version       BIGINT,
    deleted       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_mold_tenant ON mold (tenant_id);

CREATE TABLE maintenance_order (
    id          VARCHAR(36) PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL,
    mold_id     VARCHAR(36) NOT NULL,
    mold_name   VARCHAR(128),
    description VARCHAR(512) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ,
    created_by  VARCHAR(36),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(36),
    version     BIGINT,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_maint_tenant ON maintenance_order (tenant_id);
```

- [ ] **Step 2: 失败测试**（`DemoTenantIsolationIntegrationTest extends AbstractIntegrationTest`：A 登录态注册模具→A 可见、B（TenantTestSupport 或第二个 token 场景）不可见；报修→异步维修单（Awaitility）落库且 tenant 隔离；平台视角空；`event_publication` 表非空断言（JdbcTemplate count>0，状态 COMPLETED））

- [ ] **Step 3: 实现**（实体/仓库/服务改造；controller 包迁移 `mold/controller/app/MoldController.class`——包名 `io.github.luckybearbear.forge.demo.mold.controller.app` 含 `.controller.app.` 触发前缀；listener 恢复 `@ApplicationModuleListener` 并在入口 `TenantContextHolder.setTenantId(event.tenantId())` / finally clear；旧 `ModulithBoundaryTest` 模块名断言可能需调整（新增 controller 子包仍在模块内，verify 应保持绿——若 verify 因包结构报边界违规，按 Modulith 提示调整 named interface 或允许依赖，报告记录）

- [ ] **Step 4: 全量 demo 测试绿 + README 更新（连库说明、`--profile infra` compose、认证接入预告 2b）**
- [ ] **Step 5: Commit** `feat(demo): 连库升级（JPA 租户实体 + 双租户隔离 + @ApplicationModuleListener 恢复）`

---

### Task 12: 文档与登记收尾

**Files:**
- Modify: `README.md`（starter 表 +2 行、快速开始补 compose/容器测试说明、模块表状态更新）
- Modify: `docs/development/getting-started.md`（Docker/Testcontainers 前置说明、无 Docker skip 行为、JDK 路径沿用）
- Modify: `docs/architecture/dependency-rules.md`（多租户四入口小节、`@TenantIgnore` 平台视角语义、starter 拆分与依赖边界）
- Modify: `docs/standards/error-codes.md`（FORGE-0401/0403/0423 启用说明）
- Modify: `docs/database/flyway-conventions.md`（多模块 classpath 合并执行说明）
- Modify: spec §6.1 入口 1 行（按本计划"Spec 修订"节措辞）

- [ ] **Step 1: 逐文件更新（内容如上，与实际行为一致）**
- [ ] **Step 2: Commit + Push** `docs: Phase 2a 文档与规范更新（租户四入口/starter 拆分/错误码启用）`

---

### Task 13: 2a 终验

- [ ] **Step 1: Docker 在场全量**：`JAVA_HOME="$FORGE_JDK" ./mvnw --batch-mode clean verify` → BUILD SUCCESS，记录测试总数（预计 60+，含容器集成）
- [ ] **Step 2: Docker 缺席路径**（可选，若本机可临时停 Docker）：同命令 → 容器测试 skipped、其余全绿、surefire skipped 计数可见；恢复 Docker
- [ ] **Step 3: 验收对照 spec §11-2a 七条逐项核验并记录**
- [ ] **Step 4: Push + 交付报告**（提交列表/测试统计/验收结论/2b 前置输入：spike 中 Hibernate 注册机制的实际做法、契约 tenantId 字段变更、任何 fallback 决策）

---

## Self-Review 记录

- **Spec 覆盖**（§2.1-2a 十项）：1↔T5/T9、2↔T9/T10、3↔T2/T4、4↔T3/T4/T10、5↔T8、6↔T6/T7、7↔T9/T11、8↔T9(platform-test)、9↔T11、10↔T5/T9/T12；§11-2a 七条验收 ↔ T13。无缺口。
- **占位符**：无 TBD；四处"以实际 API/实证为准"（Jackson3 序列化器签名、WebMvc 前缀钩子、Hibernate resolver 注册、@SQLDelete 占位符）均附确定性验证命令与 fallback 路径。
- **类型一致性**：CurrentTenantSupplier/TenantContextHolder/TokenStore 三族签名在 T2-T10 间一致；契约 MoldRepairRequested 增 tenantId 在 T11 声明为显式契约变更并全量同步。
