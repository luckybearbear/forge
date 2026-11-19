# IDE 与本地环境配置（Windows / Mac × IDEA / VS Code）

> 本页沉淀真实踩坑经验（2026-11）。凭据类值（数据库/Redis 密码）**只写在本机 `.vscode/`（已 gitignore）**，本文模板一律占位符。
> 前置阅读：[getting-started.md](getting-started.md)（JDK/Node/构建命令基础）。

## 0. 支持矩阵与总原则

| 组合 | 支持度 | 说明 |
|---|---|---|
| Windows + VS Code | ✅ 主力验证过的组合 | 便携 JDK/Node 或安装版皆可 |
| Windows + IDEA | ✅ 需 **2025.2+**（Java 25 语言级别硬门槛） | 2023.x/2024.x 一律 "JDK 0" 报错 |
| Mac + VS Code / IDEA | ✅ 按本页模板配置 | compose 起 PG/Redis 即可 |
| **WSL 里跑前端/后端** | ❌ 不支持 | node_modules 原生二进制按平台编译（rolldown binding 崩）；WSL2 的 localhost 也到不了 Windows 侧服务。**日常开发固定在宿主侧** |

共同前置：JDK 25 · Node ≥20.19（推荐 22）· pnpm 10 · Docker（Testcontainers/PG/Redis）。

---

## 1. Windows 侧

### 1.1 工具链

| 组件 | 推荐做法 | 备注 |
|---|---|---|
| JDK 25 | 便携解压（如 `D:\devloper\soft\jdk-25.0.5+8`），或任意安装版 | 不动系统 `JAVA_HOME` 也行——各工具显式指向即可 |
| Node 22 | 便携解压（如 `D:\devloper\soft\node-v22x`）+ `npm i -g pnpm@10 --prefix <该目录>` | 本仓库的 env 脚本：`.superpowers/sdd/2026-11-18-forge-web-foundation/node22-env.sh`（Git Bash 用） |
| PostgreSQL | 常驻容器（如 `docker run -d --name pg18 -p 5432:5432 -e POSTGRES_PASSWORD=<密码> -v <宿主目录>:/var/lib/postgresql/data -e PGDATA=/var/lib/postgresql/data postgres:18-alpine`） | **postgres:18 的 PGDATA 陷阱**：bind mount 到 `/var/lib/postgresql/data` 必须显式设 `PGDATA`，否则数据写进匿名卷、命名卷恒空 |
| Redis | 本机服务版（带密码）或容器 | 见 1.4 的 NOAUTH 坑 |
| 终端 | Git Bash（env 脚本格式按它写） | PowerShell 见 1.5 |

### 1.2 IDEA 要求与配置

**版本硬门槛：2025.2 及以上**（Java 25 语言级别自此版本支持）。低于此版本（如 2023.x）注册再多 SDK 都会报：

```
Cannot start javac ... it is configured to use JDK 0, but IDE supports
compilation using JDK 7 and newer only
```

这是 IDE 不认识 release=25 的表现，**唯一解法是升级 IDE**（Ultimate 订阅受限时 Community 2025.3 免费，且与旧版可共存，本项目功能完全够用）。

配置四步（2025.2+）：

1. `File → Project Structure → SDKs → +` → Add JDK 指向 JDK 25 目录（或 Download JDK：Eclipse Temurin / 25）
2. `Project Settings → Project` → SDK 选它，Language level = `SDK default`
3. `Settings → Build Tools → Maven → Runner` → JRE 选它（Importer 随意）
4. Maven 工具窗 → **Reload All Maven Projects**

若已注册 SDK 仍报错：`Modules` 全选 → `Dependencies → Module SDK` 改回 `Project SDK`（模块级残留覆盖）；顽固时关 IDE 删 `.idea/` 重导入。

### 1.3 VS Code 要求与配置

扩展：**Extension Pack for Java**（必装）；Spring Boot Extension Pack、Vue - Official（可选）。打开**仓库根目录**（自动识别多模块）。

`.vscode/` 不入库——本机自建两个文件，Windows 模板：

**`.vscode/settings.json`**

```jsonc
{
  "java.jdt.ls.java.home": "D:\\devloper\\soft\\jdk-25.0.5+8",
  "java.configuration.runtimes": [
    { "name": "JavaSE-25", "path": "D:\\devloper\\soft\\jdk-25.0.5+8", "default": true }
  ],
  "java.configuration.updateBuildConfiguration": "automatic",
  "maven.executable.preferMavenWrapper": true,
  "terminal.integrated.env.windows": { "JAVA_HOME": "D:\\devloper\\soft\\jdk-25.0.5+8" },
  "java.compile.nullAnalysis.mode": "disabled"
}
```

**`.vscode/launch.json`**

```jsonc
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Forge Demo App (demo :18080)",
      "request": "launch",
      "mainClass": "io.github.luckybearbear.forge.demo.ForgeDemoApplication",
      "projectName": "forge-demo-app",
      "cwd": "${workspaceFolder}/forge-distributions/forge-demo-app",
      "args": "--server.port=18080 --spring.profiles.active=demo",
      "env": {
        "FORGE_DB_URL": "jdbc:postgresql://localhost:5432/forge_demo",
        "FORGE_DB_USERNAME": "<你的PG用户>",
        "FORGE_DB_PASSWORD": "<你的PG密码>",
        "SPRING_DATA_REDIS_PASSWORD": "<本机Redis密码，服务版带密码时必填>"
      }
    },
    {
      "type": "chrome",
      "name": "forge-web admin (vite dev :5173)",
      "request": "launch",
      "url": "http://localhost:5173",
      "webRoot": "${workspaceFolder}/forge-web/apps/admin/src"
    }
  ]
}
```

日常用法：**F5** 调试后端（断点/单步同 IDEA）；测试类上方 CodeLens `Run|Debug`（容器测试需 Docker 在运行）；终端 `./mvnw verify`（settings 已注入 JAVA_HOME）。

### 1.4 Windows 侧高频坑速查（均为实录）

| 症状 | 根因 | 解法 |
|---|---|---|
| `NOAUTH Authentication required`（连接 localhost:6379） | 本机 Windows 服务版 Redis 带密码；demo 引了 security-starter，seeder 角色缓存 evict 首次触 Redis 即炸 | launch.json 加 `SPRING_DATA_REDIS_PASSWORD`；密码在本机 Redis 安装目录 `redis.windows-service.conf` 的 `requirepass` 行（服务名 `Redis`，`sc qc Redis` 可查路径）。或换容器端口：`SPRING_DATA_REDIS_PORT: 16379` + `docker run -d -p 16379:6379 redis:8-alpine` |
| `docker compose --profile infra` 的 redis 起不来 | 本机 6379 被服务版 Redis 占用 | 本机开发用服务版即可；compose 的 PG/Redis 留给 CI/容器环境 |
| PG 报库不存在 | forge_demo 库未建 | `docker exec <pg容器> psql -U postgres -c "CREATE DATABASE forge_demo"` |
| 前端 `rolldown ... Cannot find native binding` | 在 **WSL** 里跑了 Windows 侧安装的 node_modules（原生二进制平台不符） | 退出 WSL 回宿主侧跑（Git Bash `source <node22-env.sh>` 或 PowerShell `$env:Path="D:\devloper\soft\node-v22x;"+$env:Path`）。坚持 WSL 需独立克隆+在 WSL 内 install，勿混用 |
| Git Bash 跑 docker run 时容器路径被篡改（如 PGDATA 变 `C:/Program Files/Git/...`） | MSYS 路径转换 | 命令前缀 `MSYS_NO_PATHCONV=1` |
| 前端代理 502/ECONNREFUSED | vite proxy 指 8080，后端起在 18080 | 二者对齐（launch.json 改 8080，或改 vite.config 代理目标） |

### 1.5 PowerShell 等价命令

```powershell
$env:Path = "D:\devloper\soft\node-v22x;" + $env:Path   # Node
$env:JAVA_HOME = "D:\devloper\soft\jdk-25.0.5+8"          # JDK
cd forge-web ; pnpm dev
```

---

## 2. Mac 侧

### 2.1 工具链

```bash
brew install --cask temurin@25          # JDK: /Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
brew install node@22 && npm i -g pnpm@10 # Node: /opt/homebrew/opt/node@22（Apple Silicon）
```

`~/.zshrc`：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="/opt/homebrew/opt/node@22/bin:$PATH"
```

数据库/缓存：直接 `docker compose --profile infra up -d`（Mac 上 5432/6379 一般空闲，无 Windows 的端口冲突问题；compose 的凭据是 `forge/forge`、Redis 无密码）。

### 2.2 IDEA

同样 **2025.2+**；配置步骤与 Windows 完全一致（SDK 指向 temurin-25.jdk）。

### 2.3 VS Code

扩展同 Windows。模板差异只在路径与终端键名：

```jsonc
// settings.json（Mac）
{
  "java.jdt.ls.java.home": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home",
  "java.configuration.runtimes": [
    { "name": "JavaSE-25", "path": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home", "default": true }
  ],
  "java.configuration.updateBuildConfiguration": "automatic",
  "maven.executable.preferMavenWrapper": true,
  "terminal.integrated.env.osx": { "JAVA_HOME": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home" },
  "java.compile.nullAnalysis.mode": "disabled"
}
```

launch.json 同 Windows 模板，env 按 compose 场景取值：

```jsonc
"env": {
  "FORGE_DB_URL": "jdbc:postgresql://localhost:5432/forge",
  "FORGE_DB_USERNAME": "forge",
  "FORGE_DB_PASSWORD": "forge"
}
// Redis 用 compose（无密码）——无需 SPRING_DATA_REDIS_PASSWORD
```

（连接 compose 的 `forge` 库即连 demo 迁移后的完整库；如想独立演示库可自建 `forge_demo`。）

---

## 3. 双端快速启动清单

```text
① 起依赖        Windows: 本机 PG(容器)+Redis(服务)     Mac: docker compose --profile infra up -d
② 起后端（调试） VS Code F5 / IDEA Run（demo profile, 18080）
③ 起后端（CLI） ./mvnw -pl forge-distributions/forge-demo-app spring-boot:run
④ 起前端        Git Bash: source .superpowers/sdd/2026-11-18-forge-web-foundation/node22-env.sh
               cd forge-web && pnpm dev        # 5173，代理 /admin-api → 8080
⑤ 浏览器        http://localhost:5173 → admin/admin123（首登后立即 PUT /admin-api/auth/password 改密）
```

账号表（demo profile seeder 自动铺底）：平台管理员 `admin/admin123`；租户 `t-mfg` 管理员 `mfg-admin/Admin@123`；租户 `t-mold` 管理员 `mold-admin/Admin@123`、受限用户 `mold-viewer/Viewer@123`（仅 mold:read）。
