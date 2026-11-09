# forge-demo-app

可运行 Demo：mold + maintenance 双业务模块，演示契约同步查询（MoldQueryService）+ 领域事件
（MoldRepairRequested）+ Modulith 边界强制。内存存储、零 DB 依赖。

运行：`JAVA_HOME=<jdk25> ./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run`

体验流：POST /api/molds → GET /api/molds/{id} → POST /api/molds/{id}/repairs →
GET /api/maintenances?moldId={id}；API 文档 /swagger-ui/index.html；健康 /actuator/health。

## 模块边界

`ModulithBoundaryTest`（`ApplicationModules.of(ForgeDemoApplication.class).verify()`）守护：
maintenance 禁止 import `io.github.luckybearbear.forge.demo.mold.**` 内部类型，跨模块只走
forge-contract-mold 契约（DTO / 事件 / 查询接口 / 错误码）。

> 注：verify() 基于 ArchUnit 字节码分析，**未被使用的 import 不留字节码痕迹**，不会被检出；
> 只有真实的类型引用（字段/参数/调用）才会触发 Violation。手工负向验证时需构造真实引用。

## 已知偏差（Phase 1 → Phase 2）

`MoldRepairRequestedListener` 使用同步 `@EventListener` 而非 `@ApplicationModuleListener`：
后者的事件投递基础设施（spring-modulith-events-core 的 EventPublicationRegistry）需要持久化
存储（JPA/JDBC/Mongo），Phase 1 无 DB 导致上下文启动失败。Phase 2 随 PostgreSQL + 事件持久化
恢复异步监听（spec §10）；Modulith verify() 边界检查不受此偏差影响。
