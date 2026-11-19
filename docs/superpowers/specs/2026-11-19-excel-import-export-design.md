# Excel 引入引出引擎（platform-excel / storage / service-exchange + mold 首接入）设计规格

| 项 | 值 |
|---|---|
| 日期 | 2026-11-19 |
| 状态 | 已确认（会话内决策逐项定案；待评审合入） |
| 决策人 | luckybearbear |
| 阶段 | Phase 3 后端切片（通用引擎 + 首接入验证；前端组件下期） |
| 前置 | Phase 1/2/2b 全部交付（四服务/五 starter/租户判别式/审计/事件登记表）；storage 仅有 StorageClient 接口骨架 |

---

## 1. 背景与目标

基础资料/主数据场景（物料、客商、模具等）的标配能力是 Excel 引入引出（苍穹同名概念）。本 spec 交付：

1. **通用引擎**（纯技术）：列模型声明 + 流式解析/写出 + 校验框架；
2. **对象存储落地**：`forge-platform-storage` 的 `StorageClient` 从单接口骨架落成多实现 SPI（本地磁盘 / S3 兼容，配置切换）；
3. **任务中心**（业务通用服务）：异步任务表 + 状态机 + 进度 + 文件管理 + `/app-api` 路由 + 权限点 + 领域事件；
4. **首个接入**：demo mold 模块声明导入/导出模型，全链路实证。

## 2. 决策表（会话内定案）

| 决策 | 定案 | 理由与代价 |
|---|---|---|
| 本期范围 | 通用引擎 + mold 首接入；前端组件下期 | 引擎一次投资多模块摊薄；demo 接入即验收 |
| 执行模式 | **异步任务中心**（任务表/状态机/进度/轮询），不走同步 | 面向十万行级；代价：任务表+执行器+进度更新 |
| 导入语义 | **模型声明式**：模型声明业务键与写入模式（UPSERT / INSERT_ONLY） | 基础资料（upsert）与单据（仅新增）语义由接入方定，引擎提供两套回调 |
| 文件存储 | **元数据入库、文件实体走对象存储**；`forge.storage.type` 配置切换（`local` / `minio`），对齐苍穹 attachmentServer.type 思路 | DB 不放大文件；MinIO/S3 兼容为生产姿势，local 为开发/测试零依赖兜底 |
| Excel 库 | **FastExcel**（`cn.idev.excel`，EasyExcel 社区延续） | 注解列模型 + SAX 流式读 + 流式写；样式/图表丰富操作本引擎不需要（见 §9 裁定），POI 原生样板量 2~3 倍 |
| 模块落位 | 三层：`forge-platform-excel`（引擎）+ `forge-storage-starter`（存储实现装配）+ `forge-service-exchange`（任务中心）+ `forge-contract-exchange`（跨域事件/错误码） | 对齐分层规则：技术横切进 platform、业务通用进 services、apps 只接入；引擎可脱离任务中心复用 |
| Excel 能力面 | **轻样式、无公式求值、无宏、单表头、v1 单 sheet** | 详见 §9 评估表 |

## 3. 模块结构与依赖

```
forge-platform-excel        引擎：列模型注解/校验框架/模板生成/错误报告生成/流式读写封装（FastExcel）
forge-platform-storage      仅扩接口：+delete/+exists（实现不进本模块，保持接口模块零重依赖）
forge-storage-starter       StorageClient 实现 + 装配：LocalStorageClient（type=local）/ S3StorageClient（type=minio，AWS SDK v2，兼容 MinIO/云 S3）
forge-contract-exchange     EXCH- 错误码 + ExchangeTaskCompleted 事件（+ 预留查询契约）
forge-service-exchange      任务中心（自动配置库，照四服务样板）：exchange_task/exchange_file 表、
                            状态机、虚拟线程执行器、/app-api 控制器、权限校验、事件发布
forge-distributions/forge-demo-app   mold 模块声明导入/导出模型（首接入）+ demo seed 扩展
```

依赖方向（全部合法，dependency-rules.md 无违例）：

- `service-exchange → platform-excel、platform-storage、platform-core、contract-exchange`（不依赖任何 service）
- `storage-starter → platform-storage`（+ AWS SDK v2，BOM 统一版本）
- `mold（demo-app 内）→ platform-excel`：实现 SPI 接口是 mold 包自身唯一的编译依赖；service-exchange 由 demo-app 组装层引用（apps/distributions 依赖 services 合法），mold 代码不触 service 内部包
- BOM 登记：4 个新模块坐标 + `fastexcel`、`awssdk-bom`（SDK 选型实施期可复评 MinIO Java SDK，接口不变）
- compose（profile infra）新增 `minio`（9000/9001）；S3 实现支持启动 `ensure-bucket`（免 mc 初始化容器）

## 4. 引擎：列模型与 SPI（forge-platform-excel）

**列模型 = 注解 POJO**，行绑定复用 FastExcel `@ExcelProperty`（列名即表头匹配），**校验复用 jakarta Bean Validation**（`@NotBlank/@Size/@Pattern` 引擎逐行跑 `Validator`）——不为校验再造注解体系。引擎自有注解仅承载 Excel 特有语义：

```java
@ExchangeSheet(key = "mold", name = "模具", authority = "mold:import",
               writeMode = UPSERT, businessKey = "moldNumber")  // 声明在模型注册类上
@ExchangeColumn  // 可选：dictRef（模板下拉字典源）、maxLength 超出 Bean Validation 时的模板生成提示
```

SPI（业务模块实现，service-exchange 运行时按 key 发现 bean）：

```java
public interface ImportModel<T> {
    ExchangeSheetMeta meta();                              // key/name/authority/writeMode/businessKey/rowType
    Map<String, T> loadExisting(Collection<String> keys);  // UPSERT 前置查询（INSERT_ONLY 不调用）
    void saveBatch(List<T> rows);                          // 分批写入（事务边界见 §6.4）
}
public interface ExportModel<T> {
    ExchangeSheetMeta meta();                              // authority 复用同一权限码语义（如 mold:export）
    List<T> fetchPage(long offset, int limit);             // 引擎分页拉取流式写出
    default long count() { return -1; }                    // 总行数（-1 未知：进度退化为行数语义）
}
```

引擎能力清单：模板生成（表头样式/冻结/列宽/必填标色/`dictRef` 下拉数据验证）、SAX 流式解析（值读取见 §9）、错误报告生成（原行回写 + 错误行标红 + 批注原因）、流式写出。**不含** Web/JPA 依赖（纯库，模块化单体之外亦可复用）。

## 5. 存储设计

### 5.1 StorageClient 接口扩展（platform-storage）

现有 `store/read` 之上新增 `delete(String key)`、`boolean exists(String key)`——返回 key 语义不变（实现自管寻址）。v1 不做预签名 URL（下载经任务中心 API 流式中转，鉴权与审计顺路）。

### 5.2 forge-storage-starter 装配

| 配置 | 说明 |
|---|---|
| `forge.storage.type` | `local`（默认，开发/测试零依赖）/ `minio`（S3 兼容：MinIO、云 S3、OSS S3 端点） |
| `forge.storage.local.base-dir` | local 实现根目录 |
| `forge.storage.minio.endpoint/access-key/secret-key/bucket` | 对齐苍穹 attachmentServer 配置面（url/accessKey/secretKey/bucket）；`ensure-bucket` 默认 true |

Swift 等其他对象存储为后续 SPI 扩展，本期不做。`ConditionalOnProperty` 装配，单实现生效，未配置回退 local。

### 5.3 文件元数据（service-exchange 域内表）

`exchange_file`（继承 `AbstractTenantEntity`，租户隔离）：`storage_key/filename/content_type/size`。文件实体在对象存储，元数据行管归属与审计。v1 不上移独立文件中心服务；后续如做附件能力再评估迁移。

## 6. 任务中心（forge-service-exchange）

### 6.1 表

`exchange_task`（继承 `AbstractTenantEntity`；Flyway：`V202611191000__create_exchange_tables.sql`，域前缀 `exchange_`，无跨域 FK）：

| 列 | 说明 |
|---|---|
| `type` | IMPORT / EXPORT |
| `model_key` | 模型 key（如 `mold`） |
| `status` | PENDING / RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED / CANCELED |
| `processed_rows / success_rows / error_rows` | 行计数（进度语义见 §6.3） |
| `source_file_id / result_file_id / error_report_file_id` | → exchange_file（逻辑引用，无物理 FK，同 OrgUnit 先例） |
| `error_message` | FAILED 原因摘要（单行） |
| `started_at / finished_at` | 执行窗口（审计基线自带 created/updated） |

### 6.2 状态机

```
PENDING → RUNNING → SUCCESS（0 错误行）
                  → PARTIAL_SUCCESS（≥1 成功且 ≥1 错误行，挂错误报告文件）
                  → FAILED（解析失败/模板不符/系统异常——无部分入库语义上的"全对或全无"，见 6.4）
        → CANCELED（RUNNING 中协作式取消：批间检查中断位；终态后取消 → EXCH-0409）
```

### 6.3 进度语义

导入：SAX 无法廉价预知总行数——**进度 = processed_rows 单调递增**，total 于完成时定值；前端展示行数（或按 `min(processed/估计,99%)` 估算），不虚构百分比。导出：total 先经接入方 count 或首页拉取后可给真实百分比。进度落库节流：每批（默认 500 行）或每 2 秒更新一次，避免行级写放大。

### 6.4 执行管线与事务/租户/并发

- **执行器**：`Executors.newVirtualThreadPerTaskExecutor()`（JDK 25）+ 全局信号量限并发（`forge.exchange.max-concurrent-tasks`，默认 4）；队列即 PENDING 行，重启后 PENDING/RUNNING 任务由启动恢复器置 FAILED（`error_message=中断`）——v1 不做断点续跑。
- **租户上下文**：worker 线程不继承请求上下文（同事件消费铁律）：任务行自带 tenantId → 入口 `TenantContextHolder.setTenantId` → 委托 `REQUIRES_NEW` service 承载业务 → finally clear（照 `MoldRepairRequestedListener` 模式）。
- **导入事务**：**逐批提交（部分成功）**：批内单事务（saveBatch 原子），批间独立——一行错误只影响自身行，不回滚已提交批。ALL_OR_NOTHING 模式 v2 再加（模型声明位已预留）。
- **失败兜底**：系统异常（存储不可达等）→ 任务 FAILED + 已提交批不回滚（部分入库如实反映为 processed/success 计数）。

### 6.5 领域事件

`ExchangeTaskCompleted(tenantId, taskId, type, modelKey, status, successRows, errorRows)`——契约模块承载，事务提交后异步投递（Modulith 事件登记表），供业务模块做导入后缓存刷新等；v1 无消费方（照 `UserCreated` 预留先例）。

## 7. API 与权限

### 7.1 端点（`/app-api/exchange/**`，双端前缀自动；控制器薄层 + `Result` 包装，照 OrgUnitAppController 样板）

| 端点 | 说明 |
|---|---|
| `GET /exchange/models` | 当前租户可用模型列表（key/name/列说明） |
| `GET /exchange/models/{key}/template` | 下载空模板（样式+下拉），GET 流式 |
| `POST /exchange/imports` | multipart（file + modelKey）建任务；文件白名单/大小校验入口 |
| `POST /exchange/exports` | JSON（modelKey + 接入方自定义 filter 透传）建任务 |
| `GET /exchange/tasks?type=&status=&page=&size=` | 任务分页 |
| `GET /exchange/tasks/{id}` | 详情（状态/行数/文件引用） |
| `PUT /exchange/tasks/{id}/cancel` | 协作式取消 |
| `GET /exchange/tasks/{id}/files/{fileId}` | 下载（源文件/错误报告/导出产物；流式 + Content-Disposition） |

### 7.2 权限

- 平台级权限点：`exchange:import` / `exchange:export` / `exchange:task:view`（BUTTON/API 型），种子经新增迁移 `V202611191100__seed_exchange_permissions.sql` 入 auth_permission 集中字典（authorization 域管字典的现状不变，本特性只新增行）。
- **模型级权限**：模型 `authority` 声明接入域自己的码（mold → `mold:import`/`mold:export`，由 mold 域 demo 迁移自种）；**模板下载、建任务、取消、文件下载**时服务层校验 `当前 authorities ⊇ {平台级码, 模型级码}`（token 快照语义，与全工程口径一致）。

### 7.3 错误码（新前缀 `EXCH-`，登记进 docs/standards/error-codes.md，定义于 forge-contract-exchange）

| 码 | 语义 |
|---|---|
| EXCH-0400 | 模型未注册 / 参数无效（如 modelKey 不存在） |
| EXCH-0404 | 任务或文件不存在（含跨租户不可见） |
| EXCH-0409 | 状态冲突（取消非 RUNNING/PENDING 任务） |
| EXCH-0415 | 文件类型不支持（非 .xlsx/.xls）或超大小上限 |

## 8. 首个接入：mold（demo-app）

- mold 域新增 `MoldImportModel`（行 POJO：moldNumber/name/spec/location…，`@ExcelProperty` + Bean Validation；UPSERT by `moldNumber`）与 `MoldExportModel`（Repository 分页 fetchPage）；mold 域迁移自种 `mold:import`/`mold:export` 权限行。
- demo-app README 增 curl 演示：模板下载 → 造一个含错误行的 xlsx → 导入 → 轮询任务 → PARTIAL_SUCCESS → 下载错误报告。
- `SPRING_PROFILES_ACTIVE=demo` 下 storage 默认 local（demo 不强依赖 MinIO）。

## 9. Excel 能力面裁定（评估定案）

| 特性 | 裁定 |
|---|---|
| 公式（读） | 只取**缓存计算值**；服务端**不求值**；无缓存值按错误行提示"请用 Excel 保存后重试" |
| 公式（写） | 不做 |
| 导入样式 | 忽略；**业务语义只认列值**（"红行=作废"类颜色编码明确不支持） |
| 模板/报告样式 | 轻量：表头加粗冻结/列宽/必填标色/错误行标红+批注原因/`dictRef` 下拉数据验证 |
| 合并单元格 | 模板统一单行表头；数据区出现合并单元格 → 该行错误"请使用标准模板" |
| 图表/图片 | 不解析不报错（SAX 值路径天然不触及） |
| 宏 | **拒绝 .xlsm/.xltm**；白名单 `.xlsx`（`.xls` 接受）；上传按 magic number 校验，不只看扩展名 |
| 多 sheet | v1 单 sheet；`ExchangeSheetMeta` 预留 sheet 属性不实现 |

**安全规格**：POI `ZipSecureFile` 压缩比阈值保持默认（防解压炸弹）；引擎级上限配置 `forge.exchange.max-file-size`（默认 20MB）/ `max-rows`（默认 100000），超限 EXCH-0415。

## 10. 测试策略

| 层 | 覆盖 |
|---|---|
| platform-excel 单测 | 模板生成（样式/下拉）、流式解析（类型/公式缓存值/合并单元格报错/白名单外的 sheet 结构）、Bean Validation 逐行校验、错误报告生成、流式写出 |
| storage-starter 单测 | local 实现 CRUD；条件装配（type 切换） |
| storage-starter 集成 | Testcontainers MinIO 跑 S3 实现（无 Docker 自动 skip，惯例不变） |
| service-exchange | Modulith 边界测试（照 ModulithBoundaryTest）；Testcontainers PG/Redis 全生命周期集成（storage 用 local，不依赖 MinIO 容器）：建任务→执行→SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED、租户隔离（跨租户 EXCH-0404）、权限拒绝 403、取消冲突 EXCH-0409、事件登记 |
| demo 冒烟 | mold 导入全链路 + 错误报告下载；`SPRING_PROFILES_ACTIVE=demo` 可重启幂等 |

## 11. 非目标（明确排除）

公式求值；宏/图表/图片；多 sheet 与主子单导入；ALL_OR_NOTHING 导入；导出公式与自定义样式主题；跨租户/平台视角批量导入；Swift 存储；预签名 URL 直连下载；断点续跑；前端组件（下期独立 spec）。

## 12. 风险与开放问题

| 项 | 处置 |
|---|---|
| FastExcel 社区延续风险（EasyExcel 停更后接棒） | 引擎层已隔离 FastExcel 类型（SPI/注解不含其公开类型），必要时可换 POI 原生实现，接入方零改动 |
| AWS SDK v2 vs MinIO Java SDK | 实施期复评（依赖体积/传递冲突）；接口不变，仅 starter 内部实现差异 |
| 进度无真实百分比（导入） | §6.3 已裁定：行数语义，不虚构；如需百分比 v2 两遍扫描再议 |
| compose/CI 影响 | minio 仅 profile infra 可选；CI 集成测试沿用"无 Docker 自动 skip"，不新增硬依赖；service-exchange 集成用 local 存储免 MinIO |
| auth_permission 跨域种子 | 字典归 authorization 管的现状延续，本特性新增行不动旧脚本（Flyway 一次性变更规则） |
