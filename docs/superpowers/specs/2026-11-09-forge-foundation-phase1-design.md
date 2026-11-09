# Forge Java 应用开发底座 — Phase 1（工程基础）设计规格

| 项 | 值 |
|---|---|
| 日期 | 2026-11-09 |
| 状态 | 已确认（会话内设计评审通过） |
| 决策人 | luckybearbear |
| 阶段 | Phase 1 / 共 5 阶段 |
| 仓库 | `D:\devloper\project\myselfProject\forge`（新建，git 初始化） |

---

## 1. 背景与目标

Forge 是一套面向 SaaS 产品（首个验证项目：模具生命周期管理 SaaS）的 **Java 应用开发基础平台**，采用**模块化单体优先、微服务按需演进**的架构原则。

四层结构（既定决策，不在本 spec 重新论证）：

| 层 | 职责 |
|---|---|
| `forge-platform` | 技术基础设施：日志、异常、审计、数据访问、缓存、安全、工具类。**不含业务概念** |
| `forge-contracts` | 模块契约：跨模块接口、DTO、Command/Query、领域事件 |
| `forge-services` | 业务共享能力：身份、组织、权限、租户、文件、工作流等（Phase 2 起） |
| `forge-apps` | 具体业务应用：模具、PLM、ERP 等（Phase 2+ 起） |

Phase 1 目标：**一个可独立运行、独立测试、独立发布的 Maven 多模块工程骨架**，含统一规范（响应/异常/错误码/日志/包命名）、BOM、Starter、可运行 Demo 应用与 Spring Modulith 边界验证。

既定技术路线（已锁定）：JDK 25 · Spring Boot 4.1.2 · Spring Modulith 2.1.2 · PostgreSQL（Phase 2 实装）· Maven · Docker/Podman · 不引入 Spring Cloud。

## 2. 范围

### 2.1 本次交付

1. 完整 Maven 多模块骨架（8 个顶层目录全部建立，模块按"真实现 / 半成品 / 占位"分级，见 §4）。
2. `forge-bom`：消费者唯一依赖入口。
3. 统一规范实现：`Result` / `PageResult` / `PageQuery` / `ErrorCode` / `BusinessException` / ID 生成。
4. `forge-platform-web`：全局异常处理、参数校验、Jackson 基线。
5. `forge-spring-boot-starter`：自动装配上述能力 + actuator。
6. 示例契约模块 `forge-contract-mold`。
7. 可运行 Demo：`forge-distributions/forge-demo-app`（双业务模块 + 契约 + 事件 + Modulith verify 测试）。
8. 构建环境：Maven Wrapper、JDK 25 本地安装、git 初始化、`docker-compose.yml`（可选启用）、`.gitlab-ci.yml` 骨架。
9. 文档四目录：architecture / development / database / standards。

### 2.2 出界清单（Phase 1 明确不做）

- 用户、组织、角色、认证、多租户的任何实现（Phase 2）
- 数据库实体、Flyway 脚本、Testcontainers 集成（Phase 2）
- 文件中心、字典、编码规则、通知、工作流（Phase 3）
- JaCoCo、Checkstyle/Spotless、Modulith 文档化插件、镜像发布流水线（Phase 4）
- 项目模板 `forge-template` 完整化（Phase 5，本次仅 README 占位）
- Spring Cloud / 微服务组件（未排期）
- 响应体强制自动包装（`ResponseBodyAdvice`）——控制器显式返回 `Result<T>`；自动包装留作后续可选配置

## 3. 版本与工具链决策

| 项 | 决策 | 说明 |
|---|---|---|
| groupId | `io.github.luckybearbear` | |
| 项目版本 | `${revision}`（默认 `1.0.0-SNAPSHOT`）+ flatten-maven-plugin | CI 友好的多模块统一版本（借鉴 yudao-dependencies 做法）；正式发布时设 revision=1.0.0 |
| Java | release **25** | 本机安装 Temurin 25（zip 解压至 `D:\devloper\soft\`），构建时显式指向，**不修改系统 `JAVA_HOME`**。Enforcer 强制 `[25,)` |
| Spring Boot | **4.1.2** | 当前最新稳定 GA；Jackson 3 / Framework 7 / Servlet 6.1 时代，编码注意新包名（见 §10） |
| Spring Modulith | **2.1.2** | 配套 Boot 4.1 |
| 构建 | 项目内置 Maven Wrapper（发行版 Maven 3.9.9） | 不依赖、不修复本机已损坏的 `mvn`（损坏原因：`boot/` 下混装 classworlds 2.7.0/2.8.0；是否顺手修复在实施时确认） |
| 依赖仓库 | 优先直连 Maven Central | 若网速不可接受，使用 **gitignored** 的 `.mvn/local-settings.xml`（`-s` 显式传入）指向阿里云镜像；**不修改全局/用户级 settings.xml** |
| git | `git init -b main` + `.gitignore` + 分阶段提交 | |

## 4. 工程结构（Phase 1 定稿目录树）

```
forge/
├── pom.xml                       # forge-parent：聚合 + 父 POM（dependencyManagement 导入 Boot/Modulith BOM）
├── .mvn/wrapper/ + mvnw + mvnw.cmd
├── .gitignore
├── .gitlab-ci.yml                # CI 骨架：verify 阶段
├── docker-compose.yml            # postgres:18-alpine + redis:8-alpine，profile=infra，可选启用
├── README.md                     # 项目简介 + 快速开始
│
├── forge-bom/                    # ✅ 完整：消费者唯一入口（导入 Boot/Modulith BOM + 管理 Forge 全部模块版本）
│
├── forge-platform/               # 聚合 POM
│   ├── forge-platform-core/      # ✅ 完整：Result/PageResult/PageQuery/ErrorCode/BusinessException/
│   │                             #          ErrorCodes 平台错误码/IdGenerator(UUIDv7 默认实现)
│   ├── forge-platform-web/       # ✅ 完整：全局异常处理器、校验失败结构化响应、Jackson 时区/序列化基线
│   ├── forge-platform-observability/  # ✅ 基础：MDC 常量、日志规范约定
│   ├── forge-platform-test/      # ✅ 基础：单测/切片测试基类（Testcontainers 基类留给 Phase 2）
│   ├── forge-platform-data/      # ⚙️ 半成品（有意）：Spring Data JDBC 依赖 + 审计字段/乐观锁基类 + Flyway 命名规范；无实体、Demo 不连库、不进 starter
│   ├── forge-platform-security/  # ⚙️ 仅上下文抽象：CurrentUser/CurrentTenant 读取接口 + ThreadLocal 实现；无认证实现
│   ├── forge-platform-cache/     # 📦 SPI 接口 + README 占位
│   ├── forge-platform-messaging/ # 📦 SPI 接口 + README 占位
│   ├── forge-platform-audit/     # 📦 SPI 接口 + README 占位
│   └── forge-platform-storage/   # 📦 SPI 接口 + README 占位
│
├── forge-contracts/              # 聚合 POM
│   └── forge-contract-mold/      # ✅ 示例契约：MoldDTO、MoldStatus、MoldRepairRequested 事件、MoldQueryService
│
├── forge-services/               # 📦 聚合 POM + README（Phase 2 起）
├── forge-apps/                   # 📦 聚合 POM + README（Phase 2+ 起）
│
├── forge-starters/               # 聚合 POM
│   └── forge-spring-boot-starter/  # ✅ 完整：webmvc + actuator + platform-core/web/observability + 自动配置
│
├── forge-distributions/          # 聚合 POM
│   └── forge-demo-app/           # ✅ 可运行 Demo（§7）
│
├── forge-template/               # 📦 README 占位（Phase 5）
└── docs/
    ├── superpowers/specs/        # 设计规格（本文件）
    ├── architecture/             # 分层与依赖规则说明
    ├── development/              # 环境搭建（JDK 25 / wrapper / 构建命令 / IDE）
    ├── database/                 # Flyway 规范（Phase 1 仅规范文档）
    └── standards/                # 错误码分段 / 命名 / 包结构规范
```

图例：✅ 完整实现 · ⚙️ 有意半成品（接口与规范就位，实现在后续阶段）· 📦 占位（POM + README 说明职责与排期）。

**每个模块（含占位）必须有 `README.md`**：职责、依赖、Phase 1 状态、后续排期。这是验收项。

## 5. 依赖规则与执行机制

既定规则矩阵：

| 调用方 | 允许依赖 | 禁止 |
|---|---|---|
| platform | JDK、第三方基础组件 | 任何业务模块 |
| contracts | 必要基础类型（platform-core 可选） | services / apps 的实现 |
| services | platform、contracts | apps 内部实现 |
| apps | platform、services、contracts | 其他 apps 内部实现 |

Phase 1 执行机制（三层）：

1. **Maven 结构性强制**：模块只能依赖 reactor 中声明的构件；apps 间互依在 POM 层面不可达。
2. **Spring Modulith `verify()`**：应用内（Demo）模块边界破坏直接导致测试失败。
3. **文档 + 评审规则**：`docs/architecture/` 记载矩阵，作为 Code Review 依据（ArchUnit 等留给 Phase 4 评估）。

## 6. 关键技术设计

### 6.1 统一响应（platform-core）

```java
public record Result<T>(String code, String message, T data) {
    public static <T> Result<T> ok(T data);          // code = "OK"
    public static Result<Void> ok();
    public static <T> Result<T> error(ErrorCode ec, String message);
}

public record PageResult<T>(List<T> items, long total, int page, int size);

public record PageQuery(int page, int size) {        // page 1 起始；size 经紧凑构造器钳制在 [1, 200]，默认 20（超限静默归一，非 400 拒绝）
    public int offset();
}
```

- 成功码为字符串常量 `"OK"`；不使用数字码。
- `traceId` 字段**暂不加入**响应体（观察性关联走 MDC/日志，Phase 4 随链路追踪再评估）。
- **不引入 Lombok**：核心类型用 record，日志用显式 `LoggerFactory`——规避注解处理器排序与 Boot 4 兼容负担（如团队强烈偏好 `@Slf4j`，后续可加回并同步维护 `lombok.config`）。

### 6.2 错误码与异常体系（platform-core）

```java
public interface ErrorCode {
    String code();                 // 形如 "FORGE-0404"，正则 ^[A-Z][A-Z0-9]{1,9}-\d{4}$
    String defaultMessage();
}

public class BusinessException extends RuntimeException {
    // (ErrorCode, message 覆盖, 可选 HttpStatus 传输提示，默认 200)
}
```

- **业务错误码是字符串分段码**，与 HTTP 状态码解耦；HTTP 状态仅作传输层提示（异常可携带，默认 200）。
- 分段规范 v0：`FORGE-` 为平台保留段；业务域前缀由底座统一分配（示例：`MOLD-`、`MAINT-`）；4 位数字建议语义：`0xxx` 参数/通用、`4xxx` 资源不存在/冲突、`9xxx` 系统级。
- 平台预置错误码（`ErrorCodes` 常量类，覆盖面借鉴 yudao `GlobalErrorCodeConstants`）：`FORGE-0400` 校验失败、`FORGE-0401` 未认证（预留）、`FORGE-0403` 无权限（预留）、`FORGE-0404` 资源不存在、`FORGE-0405` 方法不支持、`FORGE-0415` 媒体类型不支持、`FORGE-0423` 并发冲突（预留）、`FORGE-0429` 请求过频（限流预留）、`FORGE-0500` 系统内部错误、`FORGE-0501` 功能未实现/未开启。
- `BusinessException` 消息支持 `{}` 占位格式化（借鉴 `ServiceExceptionUtil`），参数错位降级为告警不抛错。
- 业务域错误码常量类**随契约模块发布**（如 `MoldErrorCodes` 放 `forge-contract-mold`，借鉴 yudao 将 `ErrorCodeConstants` 放 `-api` 模块的做法）；前缀集中登记于 `docs/standards/error-codes.md`（与附录 A 同步维护）。

### 6.3 上下文抽象（platform-security）

- `CurrentUser` / `CurrentTenant` 读取接口 + ThreadLocal 持有器实现。
- **归属 platform-security 而非 platform-core**（设计讨论中曾考虑 core，最终定 security：Phase 2 将由 Spring Security 认证结果填充同一接口）。
- 无 Spring Security 依赖、无认证实现；Demo 不使用本模块。

### 6.4 platform-data（半成品设计）

- 依赖 `spring-data-jdbc`（版本由 Boot BOM 管理）；提供审计字段基类（`id`/`createdBy`/`createdAt`/`updatedBy`/`updatedAt`/`version`，Spring Data JDBC 注解）与命名规范。
- Flyway 规范（本次只写规范、不放脚本）：目录 `src/main/resources/db/migration`，命名 `V{yyyyMMddHHmm}__{snake_case_description}.sql`。
- **不进入 forge-spring-boot-starter**；连库模块（Phase 2 services、Demo 后续版本）自行引入，保证 Phase 1 应用零 DB 依赖。

### 6.5 platform-web

- `GlobalExceptionHandler`（`@RestControllerAdvice` + `@Order(0)` 抢占三方 advice；异常覆盖面借鉴 yudao，适配 Boot 4/jakarta 实际类型）：
  - `BusinessException` → 携带的 HTTP 状态（默认 200）+ `Result(code, message)`；防御分支：业务码为成功码时改判 `FORGE-0500`（借鉴 yudao Issue #1196）；业务异常日志 warn 且不打堆栈
  - `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` / `ValidationException` → 400 + `FORGE-0400`，`data` 为 `Map<String,String>`（字段 → 错误信息）
  - `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` / `HttpMediaTypeNotSupportedException` / `MaxUploadSizeExceededException` → 400 + 对应 `FORGE-04xx`
  - 资源不存在（`NoHandlerFoundException` / Framework 7 对应类型）→ 404 + `FORGE-0404`
  - `HttpRequestMethodNotSupportedException` → 405 + `FORGE-0405`
  - 兜底 `Exception` → 500 + `FORGE-0500`（日志 error，不向客户端泄漏堆栈；Phase 2 增加错误日志落库，采用 Modulith 事件而非 yudao 的直接依赖 infra API）
  - 与 yudao 的关键差异：**HTTP 状态保留语义**（yudao 一律 200；本底座按异常类别返回 4xx/5xx，业务语义在 `code`）
- Jackson 基线（Boot 4 = Jackson 3，`tools.jackson` 包名，实施时以实际依赖树为准）：时间统一 `Instant`，ISO-8601 输出，日期不写时间戳。
- 所有 advice/配置 bean 提供 `@ConditionalOnMissingBean` 覆盖点。

### 6.6 forge-spring-boot-starter

- 传递依赖：`spring-boot-starter-webmvc`（Boot 4 的 MVC starter artifactId 以 Maven Central 实查为准）+ `spring-boot-starter-actuator`（默认仅暴露 health/info）+ platform-core/web/observability。
- 自动配置：`ForgeWebAutoConfiguration` 装配全局异常处理与 Jackson 基线，注册文件 `META-INF/spring/…AutoConfiguration.imports`。
- 集成 springdoc-openapi（取 Boot 4 兼容版本线，实施时实查）：基础 `OpenAPI` Bean（标题/版本），api-docs 默认生产禁用；分组、公共 header 参数注入等增强留待 Phase 2（借鉴 yudao Swagger 配置模式）。
- 后续阶段新增 starter 命名规范：`forge-<feature>-starter`。

### 6.7 ID 生成（platform-core）

- `IdGenerator` SPI + 默认 **UUID v7** 实现（48-bit 毫秒时间戳 + 随机位，`SecureRandom`；时间有序利于索引）。单测校验版本位/格式。
- Demo 实体主键为 `String`（UUIDv7）。

### 6.8 可观测性基线（platform-observability + starter）

- `logback-spring.xml` 基线放在 starter 资源内：控制台 pattern 含 MDC traceId 占位；`prod` profile 切结构化日志（Boot 4 structured logging，实施时验证配置键）。
- platform-observability 模块：MDC key 常量 + 日志规范文档引用。Micrometer/Prometheus/OTel 仅预留接入说明（Phase 4）。

### 6.9 Filter 顺序常量（platform-web）

- `FilterOrder` 常量类统一预留全底座 Filter 顺序段（借鉴 yudao `WebFilterOrderEnum`）：CORS 最前 → 请求体包装 → 租户（Phase 2）→ 安全（Phase 2）→ 访问日志（Phase 2）→ 业务自定义段。
- Phase 1 实际 Filter 很少，但顺序段先占位，避免后续各 starter 各自为政。

## 7. Demo 应用设计（forge-distributions/forge-demo-app）

目的：验证底座 + 示范"契约 + 事件 + 模块边界"标准打法，供未来业务模块照抄。

```
io.github.luckybearbear.forge.demo          # @SpringBootApplication（Modulith 根包）
├── mold/          @ApplicationModule       # 模具登记、报修
│   ├── api/       MoldController           # POST /api/molds、GET /api/molds/{id}、POST /api/molds/{id}/repairs
│   ├── app/       MoldService              # 业务编排，发布 MoldRepairRequested（契约类型）
│   ├── domain/    Mold（内存注册表）
│   └── MoldQueryServiceImpl                # 实现契约接口（跨模块同步查询暴露点）
└── maintenance/   @ApplicationModule       # 维修单
    ├── api/       MaintenanceController    # GET /api/maintenances/{id}
    ├── app/       MaintenanceService
    └── domain/    MaintenanceOrder（内存注册表）
```

- 依赖：`forge-spring-boot-starter` + `forge-contract-mold` + `spring-modulith-starter-core`；测试：`spring-boot-starter-test` + `spring-modulith-starter-test` + Awaitility。
- 行为流：登记模具 → 报修发布 `MoldRepairRequested` → maintenance `@ApplicationModuleListener`（异步）生成维修单 → 查询维修单。
- **maintenance 只依赖契约 jar，禁止 import `demo.mold` 内部包**——`ApplicationModules.of(ForgeDemoApplication.class).verify()` 单测强制。
- 错误演示：查询不存在模具 → `MOLD-0404`（HTTP 404 + Result），错误码常量定义于契约模块 `MoldErrorCodes`（见 §6.2）。
- 内存存储（`ConcurrentHashMap`），零 DB 依赖。

## 8. 构建与本地环境

- **JDK 25 安装**：Adoptium API `latest/25/ga/windows/x64/jdk/hotspot` zip → 解压 `D:\devloper\soft\`（保留发行版目录名）。会话内构建以 `JAVA_HOME=<该目录> ./mvnw …` 方式显式指向。`docs/development/` 记录安装与 IDE 配置说明。
- **Maven Wrapper**：`mvnw`（3.9.9 发行版）为唯一构建入口；`./mvnw verify` 为标准命令。
- **CI 骨架**（`.gitlab-ci.yml`）：`verify` 阶段跑 `./mvnw verify`；镜像 `maven:3.9-eclipse-temurin-25`（tag 不存在则回退 `eclipse-temurin:25` + wrapper）；缓存 `.m2`；触发 MR + main。离线/内部制品库能力标注 Phase 4。
- **docker-compose.yml**：`postgres:18-alpine`（库 forge）+ `redis:8-alpine`，挂 `profiles: [infra]`，`docker compose --profile infra up` 才启动；Phase 1 Demo 不依赖。

## 9. 测试策略与验收标准

测试（按模块）：

| 模块 | 测试 |
|---|---|
| platform-core | 纯单测：Result 工厂、PageQuery 校验、UUIDv7 版本位/格式、BusinessException |
| platform-web | 切片测试：校验失败 → 400 + 字段错误 map；BusinessException 映射 |
| starter | 最小 `@SpringBootTest`（测试源内建小应用）验证自动装配生效 |
| demo-app | MockMvc 全链路集成测试：建模具 → 查询 → 报修 → Awaitility 等待异步维修单 → 404 用例；Modulith `verify()` 测试 |

- platform-test 吸收 yudao 的「`@SpringBootTest(NONE)` + `@Import` 精简配置」轻量切片模式；DB/Redis 集成测试坚持 Testcontainers 真实 PostgreSQL（**不采纳** yudao 的 H2 / jedis-mock 方案，避免与生产行为漂移）。

**验收标准（Phase 1 Done）**：

1. JDK 25 环境下 `./mvnw verify` 根仓库全绿（含 Modulith 边界测试）。
2. Demo 应用可通过 `./mvnw spring-boot:run` 启动且集成测试覆盖全部端点。
3. 每个模块含 README（职责/依赖/状态/排期）。
4. `docs/` 四目录内容就位：依赖规则、构建手册、Flyway 规范、错误码与命名规范。
5. `forge-bom` 可作为独立构件被外部项目导入（编译期验证）。
6. `.gitlab-ci.yml`、`docker-compose.yml`、`.gitignore`、`README.md` 就位；git 历史按实施阶段分批提交。

## 10. 风险与实施时验证项

| 风险/不确定 | 应对 |
|---|---|
| Boot 4 与既往差异：Jackson 3 包名（`tools.jackson`）、MVC starter artifactId、`@MockitoBean` 取代 `@MockBean`、JUnit 版本管理 | 实施时以实际解析的依赖树为准，先跑最小 spike 再铺开 |
| Modulith 2.1 事件发布记录（Event Publication Registry）持久化要求 | Phase 1 事件内存完成即可；registry 持久化（JDBC）留 Phase 2 连库时启用 |
| 阿里云镜像对 Boot 4.1.2 同步延迟 | 默认直连 Central；`.mvn/local-settings.xml`（gitignored）备用 |
| GitLab CI 镜像 tag `maven:3.9-eclipse-temurin-25` 可用性 | 回退方案已定义（§8） |
| 本机 Maven 损坏（classworlds 混装） | 不依赖本机 mvn（用 wrapper）；是否顺手删除旧 jar 修复，实施时与用户确认 |
| Temurin 25 下载体积（约 200MB） | 网络已验证可达 Adoptium API |
| 借鉴源差异：本地 yudao-cloud 为 **jdk8 分支**（Boot 2.7 / javax / Jackson 2） | 只借鉴模式不搬代码；涉及点（异常处理器清单、Jackson 定制、autoconfig 注册）均需换算到 Boot 4 / jakarta / Jackson 3 语义 |

## 11. 后续阶段路线（非本次范围，仅存档）

- **Phase 2 企业基础能力**：identity/organization/authorization/tenant 服务、Spring Security 认证、Flyway 实装、PostgreSQL + Testcontainers、数据审计。多租户备忘（借鉴 yudao biz-tenant）：上下文用 TransmittableThreadLocal 或 JDK 25 ScopedValue 评估；Web filter 解析 header、SQL 改写、Redis key 前缀、MQ 消息头四入口统一隔离；`@TenantIgnore` + ignore 配置作逃生口。另评估：Long→String Jackson 序列化（防 JS 精度丢失）、请求体可重复读 Filter、admin-api/app-api 包前缀路由（WebMvcRegistrations 模式）、幂等/限流/分布式锁（yudao protection 模式）、访问/错误日志落库（Modulith 事件实现）。
- **Phase 3 通用业务能力**：文件中心、字典、编码规则、消息通知、简单审批。
- **Phase 4 工程化**：Modulith 文档化、JaCoCo、格式化门禁、CI/CD 完善（含离线/内部制品库）、镜像发布。
- **Phase 5 模板化**：forge-template、按需装配、示例业务模块、完整开发规范。

---

## 附录 A：错误码分段分配表 v0

| 前缀 | 归属 | 状态 |
|---|---|---|
| `FORGE-` | 平台（platform 各模块） | 启用，预置码见 §6.2 |
| `MOLD-` | 示例业务：模具 | 启用（Demo） |
| `MAINT-` | 示例业务：维修 | 启用（Demo） |
| （其他） | 新业务域在 standards 文档登记后启用 | 流程待 Phase 2 建立 |
