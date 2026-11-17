# 开发环境上手

## JDK 25

本仓库要求 JDK 25（构建由 Maven Enforcer 强制 [25,)）。推荐 Temurin：
https://adoptium.net/temurin/releases/?version=25

构建时显式指定（不动系统 JAVA_HOME）：

```bash
export JAVA_HOME="D:/devloper/soft/<jdk-25 目录>"
```

## 构建

```bash
./mvnw verify                 # 全量构建 + 测试
./mvnw -pl <module> test      # 单模块测试
./mvnw -pl forge-distributions/forge-demo-app -am spring-boot:run   # 跑 Demo（连 PG，见下）
```

依赖优先直连 Maven Central；网速不佳时可用（不入库）`.mvn/local-settings.xml` 指向阿里云镜像，
构建命令加 `-s .mvn/local-settings.xml`。

## Docker / Testcontainers

- 集成测试坚持真实容器（不用 H2/jedis-mock）：platform-test 的 `AbstractIntegrationTest`
  经 Testcontainers 起 `postgres:18-alpine` + `redis:8-alpine` 两个容器，`@ServiceConnection`
  自动注入连接（测试应用含对应技术栈即直连）。
- 基类标注 `@Testcontainers(disabledWithoutDocker = true)`：**本机无 Docker（或 daemon 未启动）
  时容器集成测试自动 skip，其余测试照常执行**——构建不因缺 Docker 失败，但容器集成覆盖面归零，
  提交前尽量保证 Docker 在场跑一次 `./mvnw verify`。
- 镜像预拉建议：首次运行会现场拉取镜像，网络受限环境建议先手动预拉，避免测试超时：
  `docker pull postgres:18-alpine && docker pull redis:8-alpine`。

### Git Bash 注意：MSYS 路径转换

Git Bash（MSYS）会把以 `/` 开头的参数当作本机路径篡改。docker 命令中出现**容器内路径**时须加
`MSYS_NO_PATHCONV=1` 前缀，否则路径被改写为 `C:/Program Files/Git/...`（Phase 2a 实证：
`docker run ... -e PGDATA=/var/lib/postgresql/data ...` 的 PGDATA 被篡改，容器初始化异常）：

```bash
MSYS_NO_PATHCONV=1 docker run -d --name forge-pg -e PGDATA=/var/lib/postgresql/data postgres:18-alpine
```

仓库内 `docker-compose.yml` 的路径不经 shell 展开，`docker compose` 调用不受影响。

## 本地 PostgreSQL / Redis（Demo 连库）

Demo（forge-demo-app）连 PostgreSQL 运行，本地基础设施二选一（详细步骤见
`forge-distributions/forge-demo-app/README.md`）：

1. **compose**：仓库根 `docker compose --profile infra up -d`（PG 5432 / Redis 6379；
   库 forge / 用户 forge / 密码 forge），再补建 demo 库后启动（Demo 凭据默认即 forge/forge）：
   `docker exec -it $(docker ps -qf name=postgres) psql -U forge -d forge -c "CREATE DATABASE forge_demo"`
   然后启动 Demo（无需额外环境变量）。
2. **常驻实例/容器**：本机已有 PG 占用 5432 时，在该实例建独立库 `forge_demo`，经环境变量
   `FORGE_DB_URL` / `FORGE_DB_USERNAME` / `FORGE_DB_PASSWORD` 指向（真实凭据不进版本库）。

**宿主端口冲突**：compose 的 PG 固定映射宿主 5432，与本地已有 PG（如 pg15/pg18 常驻容器）冲突时
二选一：停掉其一，或改 compose 端口映射（如 `"15432:5432"`）并经 `FORGE_DB_URL` 指向新端口。

## Demo 认证体验（demo profile 种子与初始口令）

以 `SPRING_PROFILES_ACTIVE=demo` 启动 Demo 即由 `DemoDataSeeder` 铺底认证全链路的初始数据
（经服务 API 创建、幂等可重启；集成测试上下文用 `test` 同一份；**Redis 必备**——token 存储
与权限快照缓存）。两租户三账号：

| 租户 | 编码 | 用户 | 口令 | 角色 |
|------|------|------|------|------|
| 精密制造 | t-mfg | mfg-admin | Admin@123 | ADMIN（全权限） |
| 模具车间 | t-mold | mold-admin | Admin@123 | ADMIN（全权限） |
| 模具车间 | t-mold | mold-viewer | Viewer@123 | VIEWER（仅 mold:read，演示 403） |

登录 `POST /app-api/auth/login`（体含 tenantCode/username/password）→ `data.token` → swagger
右上 **Authorize** 填 `Bearer <token>`（含前缀）。管理面 `POST /admin-api/auth/login` 用平台
管理员 **admin / admin123**（service-identity Flyway 种子行；**初始口令仅供首登体验，登录后应尽快
经改密通道更换**）。权限快照冻结（授权变更需重登）、审计落表等完整体验流见
`forge-distributions/forge-demo-app/README.md`。

## OSIV：连库应用建议关闭

连库应用建议显式 `spring.jpa.open-in-view=false`（demo 已关）：判别式租户在 Hibernate 会话
开启时解析，OSIV 的请求级会话先于租户上下文填充开启 → 判别式恒解析为平台租户 `"0"`（租户表
恒空；Phase 2b Task 8 实证：`REQUIRES_NEW` 另开会话亦不足以恢复，appLogin 恒 IDENT-0401）。
详见 `docs/architecture/dependency-rules.md` 的 OSIV 小节。

## IDE

将仓库作为 Maven 项目导入；Annotation Processing 无需配置（本项目不使用 Lombok）。
Enforcer 要求 IDE 内置 Maven/JDK 同样满足 25。
