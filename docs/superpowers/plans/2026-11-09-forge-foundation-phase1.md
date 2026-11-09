# Forge Phase 1（工程基础）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Forge 模块化单体底座的 Phase 1 工程骨架：可独立运行、独立测试、独立发布的 Maven 多模块工程，含统一规范、BOM、Starter、示例契约与可运行 Demo（双业务模块 + Modulith 边界验证）。

**Architecture:** Maven 多模块 reactor，四层结构（platform / contracts / services / apps）+ starters / distributions / bom。跨模块协作只走 contracts + 领域事件；Spring Modulith `verify()` 在 Demo 应用内强制模块边界。

**Tech Stack:** JDK 25 (Temurin) · Spring Boot 4.1.2 · Spring Modulith 2.1.2 · Spring Data JDBC（仅基类） · springdoc 3.2.0 · Maven 3.9.9 (wrapper) · 无 Lombok · 无 DB（Demo 内存存储）

**Spec:** `docs/superpowers/specs/2026-11-09-forge-foundation-phase1-design.md`

## Global Constraints

- groupId `io.github.luckybearbear`；版本 `${revision}`（默认 `1.0.0-SNAPSHOT`）+ flatten-maven-plugin 1.7.1
- Java `release=25`；Maven Enforcer：`requireJavaVersion [25,)`、`requireMavenVersion [3.9,)`
- Spring Boot **4.1.2**（import `spring-boot-dependencies`）；MVC starter 用 **`spring-boot-starter-webmvc`**（Boot 4 命名）；测试用 `spring-boot-starter-test`
- Spring Modulith **2.1.2**（import `spring-modulith-bom`；Demo 用 `spring-modulith-starter-core` / test 用 `spring-modulith-starter-test`）
- springdoc `springdoc-openapi-starter-webmvc-ui` **3.2.0**（由 forge-bom 管理版本）
- **不引入 Lombok**：record + 显式 `LoggerFactory`；编码 UTF-8
- 包根：`io.github.luckybearbear.forge`；platform 模块 `…forge.platform.<x>`；契约 `…forge.contract.mold`；Demo `…forge.demo`（Modulith 根）
- 业务错误码正则 `^[A-Z][A-Z0-9]{1,9}-\d{4}$`；成功码字符串 `"OK"`；HTTP 状态保留语义（BusinessException 可携带，默认 200）
- platform 模块禁止依赖业务模块；Demo 的 maintenance 模块禁止 import `io.github.luckybearbear.forge.demo.mold.**`
- 所有构建命令在仓库根目录 `D:\devloper\project\myselfProject\forge`（Git Bash）执行；JDK 25 通过 `JAVA_HOME="$FORGE_JDK"` 显式指定，**不修改系统环境变量**
- Maven 一律用 `./mvnw`；本机损坏的 `mvn` 仅在 Task 2 用一次（生成 wrapper）
- 每个任务以 commit 结束；提交信息用 conventional 前缀 + 中文摘要

---

### Task 1: 工具链准备（JDK 25 安装 + 本机 mvn 修复）

**Files:**
- Create: `D:\devloper\soft\<jdk-25.x.y+z>\`（解压产物，不入库）
- Move（备份性移动，可逆）: `D:\devloper\soft\apache-maven-3.9.9\boot\plexus-classworlds-2.7.0.jar` → `D:\devloper\soft\plexus-classworlds-2.7.0.jar.bak`

**Interfaces:**
- Produces: 环境变量 `FORGE_JDK`（Git Bash 路径，指向 JDK 25 根目录），后续所有任务的构建命令依赖它

- [ ] **Step 1: 下载 Temurin JDK 25**

```bash
curl -L -o /tmp/jdk25.zip "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"
ls -lh /tmp/jdk25.zip   # 预期约 200MB
```

- [ ] **Step 2: 解压到 D:\devloper\soft\ 并验证**

```bash
unzip -q /tmp/jdk25.zip -d "/d/devloper/soft/"
ls "/d/devloper/soft/" | grep '^jdk-25'    # 记下实际目录名，形如 jdk-25.0.5+11
export FORGE_JDK="/d/devloper/soft/<实际目录名>"
"$FORGE_JDK/bin/java" -version            # 预期输出包含 'version "25'
```
（若无 unzip：`powershell -Command "Expand-Archive -Path C:\Users\1\AppData\Local\Temp\jdk25.zip -DestinationPath D:\devloper\soft"`，再定位目录）

- [ ] **Step 3: 修复本机 mvn（移除混装的旧 classworlds jar，可逆）**

```bash
mv "/d/devloper/soft/apache-maven-3.9.9/boot/plexus-classworlds-2.7.0.jar" "/d/devloper/soft/plexus-classworlds-2.7.0.jar.bak"
mvn -version    # 预期 Apache Maven 3.9.9 输出正常（用系统 JDK 17 跑这一次没关系）
```

- [ ] **Step 4: Commit（无仓库文件变更，跳过；此任务无 git 提交）**

---

### Task 2: Reactor 骨架（根 POM + BOM + 全部聚合/占位模块 + Maven Wrapper）

**Files:**
- Create: `pom.xml`（根，forge-parent）
- Create: `forge-bom/pom.xml`
- Create: `forge-platform/pom.xml` + 10 个子模块目录及 `pom.xml`（core/web/security/data/cache/messaging/observability/audit/storage/test）
- Create: `forge-contracts/pom.xml`
- Create: `forge-services/pom.xml`、`forge-apps/pom.xml`（空聚合 + README）
- Create: `forge-starters/pom.xml`、`forge-distributions/pom.xml`
- Create: `forge-template/README.md`
- Create: 各占位模块 `README.md`；cache/messaging/audit/storage 四个占位模块的 SPI 接口（`CacheFacade`、`MessagePublisher`、`AuditEntry` + `AuditRecorder`、`StorageClient`，spec §4 要求）
- Create: `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`（由 mvn wrapper:wrapper 生成）
- Modify: `.gitignore`（追加 `.flattened-pom.xml`）

**Interfaces:**
- Produces: 全 reactor `./mvnw validate` 可解析的模块树；后续任务只往既有模块里加代码

- [ ] **Step 1: 先建最小根 pom（供 wrapper 插件运行）**

`pom.xml`（临时最小版）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.luckybearbear</groupId>
  <artifactId>forge-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
</project>
```

- [ ] **Step 2: 生成 Maven Wrapper（only-script，不带 jar）**

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.9 -Dtype=only-script
JAVA_HOME="$FORGE_JDK" ./mvnw -version    # 预期下载 apache-maven-3.9.9 后输出 Maven 3.9.9 / Java version 25
```

- [ ] **Step 3: 写正式根 pom（替换最小版）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.github.luckybearbear</groupId>
  <artifactId>forge-parent</artifactId>
  <version>${revision}</version>
  <packaging>pom</packaging>
  <name>Forge :: Parent</name>
  <description>Forge — A Modular Java Application Foundation for Building SaaS Products.</description>

  <modules>
    <module>forge-bom</module>
    <module>forge-platform</module>
    <module>forge-contracts</module>
    <module>forge-services</module>
    <module>forge-apps</module>
    <module>forge-starters</module>
    <module>forge-distributions</module>
  </modules>

  <properties>
    <revision>1.0.0-SNAPSHOT</revision>
    <java.version>25</java.version>
    <maven.compiler.release>25</maven.compiler.release>
    <maven.compiler.parameters>true</maven.compiler.parameters>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>4.1.2</spring-boot.version>
    <spring-modulith.version>2.1.2</spring-modulith.version>
    <springdoc.version>3.2.0</springdoc.version>
    <flatten-maven-plugin.version>1.7.1</flatten-maven-plugin.version>
    <maven-compiler-plugin.version>3.14.0</maven-compiler-plugin.version>
    <maven-surefire-plugin.version>3.5.3</maven-surefire-plugin.version>
    <maven-enforcer-plugin.version>3.5.0</maven-enforcer-plugin.version>
  </properties>

  <!-- 依赖方向铁律：platform 不依赖业务；contracts 只依赖 platform-core；services 依赖 platform+contracts；
       apps 依赖 platform+services+contracts；apps 之间禁止互依。跨模块调用只走 contracts + 事件。 -->
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.github.luckybearbear</groupId>
        <artifactId>forge-bom</artifactId>
        <version>${revision}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>${maven-compiler-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>${maven-surefire-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-enforcer-plugin</artifactId>
          <version>${maven-enforcer-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>flatten-maven-plugin</artifactId>
          <version>${flatten-maven-plugin.version}</version>
          <configuration>
            <flattenMode>oss</flattenMode>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <version>${spring-boot.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <executions>
          <execution>
            <id>enforce-toolchain</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
              <rules>
                <requireJavaVersion><version>[25,)</version></requireJavaVersion>
                <requireMavenVersion><version>[3.9,)</version></requireMavenVersion>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>flatten-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>flatten</id>
            <phase>process-resources</phase>
            <goals><goal>flatten</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: 写 forge-bom**

`forge-bom/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-bom</artifactId>
  <packaging>pom</packaging>
  <name>Forge :: BOM</name>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-bom</artifactId>
        <version>${spring-modulith.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
      </dependency>

      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-core</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-web</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-security</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-data</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-cache</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-messaging</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-observability</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-audit</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-storage</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-platform-test</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-contract-mold</artifactId><version>${revision}</version></dependency>
      <dependency><groupId>io.github.luckybearbear</groupId><artifactId>forge-spring-boot-starter</artifactId><version>${revision}</version></dependency>
    </dependencies>
  </dependencyManagement>

  <build><plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>flatten-maven-plugin</artifactId>
      <configuration><flattenMode>bom</flattenMode></configuration>
    </plugin>
  </plugins></build>
</project>
```

- [ ] **Step 5: 写 forge-platform 聚合 pom 与 10 个子模块 pom**

`forge-platform/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform</artifactId>
  <packaging>pom</packaging>
  <name>Forge :: Platform</name>
  <modules>
    <module>forge-platform-core</module>
    <module>forge-platform-web</module>
    <module>forge-platform-security</module>
    <module>forge-platform-data</module>
    <module>forge-platform-cache</module>
    <module>forge-platform-messaging</module>
    <module>forge-platform-observability</module>
    <module>forge-platform-audit</module>
    <module>forge-platform-storage</module>
    <module>forge-platform-test</module>
  </modules>
</project>
```

`forge-platform/forge-platform-core/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-core</artifactId>
  <name>Forge :: Platform :: Core</name>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`forge-platform/forge-platform-web/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-web</artifactId>
  <name>Forge :: Platform :: Web</name>
  <dependencies>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`forge-platform/forge-platform-security/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-security</artifactId>
  <name>Forge :: Platform :: Security</name>
  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`forge-platform/forge-platform-data/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-data</artifactId>
  <name>Forge :: Platform :: Data</name>
  <dependencies>
    <dependency>
      <groupId>org.springframework.data</groupId>
      <artifactId>spring-data-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`forge-platform/forge-platform-cache/pom.xml`（messaging / audit / storage 同构，仅 artifactId/name/SPI 类名不同）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-cache</artifactId>
  <name>Forge :: Platform :: Cache</name>
</project>
```

（`forge-platform-messaging`、`forge-platform-audit`、`forge-platform-storage`：同上，替换 artifactId 与 name。）

`forge-platform/forge-platform-observability/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-observability</artifactId>
  <name>Forge :: Platform :: Observability</name>
</project>
```

`forge-platform/forge-platform-test/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-platform</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-platform-test</artifactId>
  <name>Forge :: Platform :: Test</name>
  <dependencies>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 6: 其余聚合 pom（contracts / services / apps / starters / distributions）**

`forge-contracts/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-contracts</artifactId>
  <packaging>pom</packaging>
  <name>Forge :: Contracts</name>
  <modules>
    <module>forge-contract-mold</module>
  </modules>
</project>
```

`forge-contracts/forge-contract-mold/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-contracts</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-contract-mold</artifactId>
  <name>Forge :: Contract :: Mold</name>
  <dependencies>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-core</artifactId>
    </dependency>
  </dependencies>
</project>
```

`forge-services/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-services</artifactId>
  <packaging>pom</packaging>
  <name>Forge :: Services</name>
</project>
```

`forge-apps/pom.xml`：同上结构，artifactId `forge-apps`，name `Forge :: Apps`。

`forge-starters/pom.xml`：同上结构，artifactId `forge-starters`，name `Forge :: Starters`。**暂无 `<modules>`**（Task 8 创建子模块时再加入，保证本任务 `validate` 通过）。

`forge-distributions/pom.xml`：同上结构，artifactId `forge-distributions`，name `Forge :: Distributions`。**暂无 `<modules>`**（Task 9 创建子模块时再加入）。

- [ ] **Step 7: 占位包结构与 README**

每个占位模块（cache/messaging/audit/storage/observability + services/apps 聚合 + template）建 `README.md`。示例 `forge-platform/forge-platform-cache/README.md`：

```markdown
# forge-platform-cache

缓存抽象（SPI）。Phase 1 仅占位：定义 `CacheFacade` 接口，Phase 2 提供 Redis 实现与 starter。

- 依赖：无（Phase 2 将依赖 spring-data-redis）
- 状态：📦 占位
- 排期：Phase 2
```

其余占位 README 同构（替换职责一句话：messaging=消息发布/消费抽象（Phase 3）；audit=操作审计 SPI（Phase 2）；storage=文件存储抽象（Phase 3）；observability=MDC 常量与日志规范（本阶段实现常量类，见 Task 5）；services=业务共享能力聚合（Phase 2 起逐个落模块）；apps=具体业务应用聚合（Phase 2+ 起）；forge-template=新项目模板（Phase 5））。

四个占位模块的 SPI 接口（spec §4：SPI 接口 + README 占位）：

`forge-platform/forge-platform-cache/src/main/java/io/github/luckybearbear/forge/platform/cache/CacheFacade.java`：

```java
package io.github.luckybearbear.forge.platform.cache;

import java.time.Duration;
import java.util.Optional;

/** 缓存抽象 SPI。Phase 2 提供 Redis 实现与 forge-cache-starter。 */
public interface CacheFacade {

    void put(String key, Object value, Duration ttl);

    Optional<Object> get(String key);

    void evict(String key);
}
```

`forge-platform/forge-platform-messaging/src/main/java/io/github/luckybearbear/forge/platform/messaging/MessagePublisher.java`：

```java
package io.github.luckybearbear.forge.platform.messaging;

/** 消息发布抽象 SPI。Phase 3 按需提供实现；模块内默认事件通道是 Spring Modulith 事件。 */
public interface MessagePublisher {

    void publish(String topic, Object payload);
}
```

`forge-platform/forge-platform-audit/src/main/java/io/github/luckybearbear/forge/platform/audit/AuditEntry.java`：

```java
package io.github.luckybearbear.forge.platform.audit;

import java.time.Instant;

/** 操作审计条目（操作人/动作/对象/时间）。 */
public record AuditEntry(String actor, String action, String targetType, String targetId, Instant at) {}
```

`forge-platform/forge-platform-audit/src/main/java/io/github/luckybearbear/forge/platform/audit/AuditRecorder.java`：

```java
package io.github.luckybearbear.forge.platform.audit;

/** 审计记录 SPI。Phase 2 接操作日志落库（经 Modulith 事件异步化）。 */
public interface AuditRecorder {

    void record(AuditEntry entry);
}
```

`forge-platform/forge-platform-storage/src/main/java/io/github/luckybearbear/forge/platform/storage/StorageClient.java`：

```java
package io.github.luckybearbear.forge.platform.storage;

import java.io.InputStream;

/** 文件存储抽象 SPI。Phase 3 提供本地/对象存储实现与文件中心。 */
public interface StorageClient {

    /** 返回存储 key。 */
    String store(InputStream content, String filename, String contentType);

    InputStream read(String key);
}
```

`forge-template/README.md`：

```markdown
# forge-template

新 SaaS 项目模板（Phase 5 交付）：Maven 结构、配置基线、CI/CD、示例业务模块、模块按需装配说明。
```

- [ ] **Step 8: 验证全 reactor 解析 + 提交**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q validate
# 预期 BUILD SUCCESS（所有模块被 reactor 识别；messaging 等空模块不影响 validate）
```

`.gitignore` 追加一行：`.flattened-pom.xml`

```bash
git add -A && git commit -m "build: Maven 多模块 reactor 骨架（root/bom/platform/contracts/starters/distributions + wrapper）"
```

---

### Task 3: platform-core 统一规范（Result / ErrorCode / BusinessException / IdGenerator）

**Files:**
- Create: `forge-platform/forge-platform-core/src/main/java/io/github/luckybearbear/forge/platform/core/Result.java`
- Create: 同包 `PageResult.java`、`PageQuery.java`、`ErrorCode.java`、`ErrorCodes.java`、`BusinessException.java`
- Create: 同包 `IdGenerator.java`、`UuidV7Generator.java`
- Test: `forge-platform/forge-platform-core/src/test/java/io/github/luckybearbear/forge/platform/core/` 下 `ResultTest.java`、`PageQueryTest.java`、`BusinessExceptionTest.java`、`UuidV7GeneratorTest.java`

**Interfaces:**
- Consumes: 无（纯 JDK + slf4j-api）
- Produces（后续任务依赖的确切签名）：
  - `record Result<T>(String code, String message, T data)`；`static <T> Result<T> ok(T data)`、`static Result<Void> ok()`、`static Result<Void> error(ErrorCode ec)`、`static <T> Result<T> error(ErrorCode ec, String message)`；常量 `String SUCCESS_CODE = "OK"`
  - `interface ErrorCode { String code(); String defaultMessage(); }`
  - `enum ErrorCodes implements ErrorCode`：`PLATFORM_BAD_REQUEST(FORGE-0400)`、`UNAUTHORIZED(FORGE-0401)`、`FORBIDDEN(FORGE-0403)`、`NOT_FOUND(FORGE-0404)`、`METHOD_NOT_ALLOWED(FORGE-0405)`、`UNSUPPORTED_MEDIA_TYPE(FORGE-0415)`、`CONCURRENT_LOCKED(FORGE-0423)`、`TOO_MANY_REQUESTS(FORGE-0429)`、`INTERNAL_SERVER_ERROR(FORGE-0500)`、`NOT_IMPLEMENTED(FORGE-0501)`
  - `class BusinessException extends RuntimeException`：`ErrorCode errorCode()`、`Integer httpStatus()`（null=默认 200）、`BusinessException withHttpStatus(int)`、`static BusinessException of(ErrorCode ec, Object... messageArgs)`（`{}` 占位格式化 defaultMessage）
  - `record PageQuery(int page, int size)`（紧凑构造器钳制 page≥1、1≤size≤200）、`int offset()`
  - `record PageResult<T>(List<T> items, long total, int page, int size)`、`static <T> PageResult<T> of(PageQuery q, long total, List<T> items)`
  - `interface IdGenerator { String nextId(); }`；`class UuidV7Generator implements IdGenerator`

- [ ] **Step 1: 写失败测试（先测后写）**

`ResultTest.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    @Test
    void okCarriesSuccessCodeAndData() {
        Result<String> result = Result.ok("data");
        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data()).isEqualTo("data");
    }

    @Test
    void okVoidHasNullData() {
        assertThat(Result.ok().data()).isNull();
    }

    @Test
    void errorUsesErrorCodeAndMessage() {
        Result<Void> result = Result.error(ErrorCodes.NOT_FOUND, "找不到模具: m-1");
        assertThat(result.code()).isEqualTo("FORGE-0404");
        assertThat(result.message()).isEqualTo("找不到模具: m-1");
        assertThat(result.data()).isNull();
    }

    @Test
    void errorRejectsSuccessCode() {
        ErrorCode fakeOk = new ErrorCode() {
            @Override public String code() { return "OK"; }
            @Override public String defaultMessage() { return "x"; }
        };
        assertThatThrownBy(() -> Result.error(fakeOk))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

`PageQueryTest.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @Test
    void clampsInvalidValues() {
        assertThat(new PageQuery(0, 0)).isEqualTo(new PageQuery(1, 20));
        assertThat(new PageQuery(3, 9999)).isEqualTo(new PageQuery(3, 200));
    }

    @Test
    void offsetIsZeroBasedPageTimesSize() {
        assertThat(new PageQuery(2, 50).offset()).isEqualTo(50);
    }
}
```

`BusinessExceptionTest.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void ofFormatsPlaceholders() {
        BusinessException ex = BusinessException.of(ErrorCodes.NOT_FOUND, "m-1");
        assertThat(ex.errorCode()).isEqualTo(ErrorCodes.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("请求的资源不存在: m-1");
        assertThat(ex.httpStatus()).isNull();
    }

    @Test
    void withHttpStatusIsFluent() {
        BusinessException ex = BusinessException.of(ErrorCodes.NOT_FOUND, "m-1").withHttpStatus(404);
        assertThat(ex.httpStatus()).isEqualTo(404);
    }
}
```

`UuidV7GeneratorTest.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    void producesVersion7Variant2Uuid() {
        UUID uuid = UUID.fromString(generator.nextId());
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void producesUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void roughlyMonotonicTimestampPrefix() {
        long previous = 0;
        for (int i = 0; i < 100; i++) {
            UUID uuid = UUID.fromString(generator.nextId());
            long ts = uuid.getMostSignificantBits() >>> 16;
            assertThat(ts).isGreaterThanOrEqualTo(previous);
            previous = ts;
        }
    }
}
```

- [ ] **Step 2: 运行确认编译失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-core test
# 预期：COMPILATION ERROR（Result 等类不存在）
```

- [ ] **Step 3: 写实现**

`Result.java`：

```java
package io.github.luckybearbear.forge.platform.core;

/**
 * 统一响应体。成功码为字符串常量 "OK"；错误码见 {@link ErrorCode} 体系。
 * 注意：platform-core 保持零 Spring 依赖，校验手写（勿用 org.springframework.util.Assert）。
 */
public record Result<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static Result<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.defaultMessage());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        requireError(errorCode);
        return new Result<>(errorCode.code(), message, null);
    }

    private static void requireError(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        if (errorCode.code() == null || errorCode.code().isBlank()) {
            throw new IllegalArgumentException("errorCode.code must not be blank");
        }
        if (SUCCESS_CODE.equals(errorCode.code())) {
            throw new IllegalArgumentException("errorCode must not be the success code");
        }
    }
}
```

`ErrorCode.java`：

```java
package io.github.luckybearbear.forge.platform.core;

/**
 * 业务错误码。code 形如 "FORGE-0404"（^[A-Z][A-Z0-9]{1,9}-\d{4}$），与 HTTP 状态码解耦。
 */
public interface ErrorCode {

    String code();

    String defaultMessage();
}
```

`ErrorCodes.java`：

```java
package io.github.luckybearbear.forge.platform.core;

/**
 * 平台级错误码（FORGE- 前缀保留段）。业务域前缀在 docs/standards/error-codes.md 登记。
 */
public enum ErrorCodes implements ErrorCode {

    PLATFORM_BAD_REQUEST("FORGE-0400", "请求参数不正确: {}"),
    UNAUTHORIZED("FORGE-0401", "账号未登录"),
    FORBIDDEN("FORGE-0403", "没有该操作权限"),
    NOT_FOUND("FORGE-0404", "请求的资源不存在: {}"),
    METHOD_NOT_ALLOWED("FORGE-0405", "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE("FORGE-0415", "媒体类型不支持"),
    CONCURRENT_LOCKED("FORGE-0423", "请求冲突，请稍后重试"),
    TOO_MANY_REQUESTS("FORGE-0429", "请求过于频繁"),
    INTERNAL_SERVER_ERROR("FORGE-0500", "系统异常"),
    NOT_IMPLEMENTED("FORGE-0501", "功能未实现或未开启");

    private final String code;
    private final String defaultMessage;

    ErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
```

`BusinessException.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import org.slf4j.helpers.MessageFormatter;

/**
 * 业务异常。HTTP 状态仅作传输提示：默认 200，可通过 {@link #withHttpStatus(int)} 覆盖。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private Integer httpStatus;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 按 defaultMessage 的 {} 占位格式化构造。 */
    public static BusinessException of(ErrorCode errorCode, Object... messageArgs) {
        String message = MessageFormatter.arrayFormat(errorCode.defaultMessage(), messageArgs).getMessage();
        return new BusinessException(errorCode, message);
    }

    public BusinessException withHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    public ErrorCode errorCode() { return errorCode; }

    /** null 表示使用默认 200。 */
    public Integer httpStatus() { return httpStatus; }
}
```

`PageQuery.java`：

```java
package io.github.luckybearbear.forge.platform.core;

/**
 * 分页查询参数。page 1 起始；size 钳制在 [1, 200]，默认 20。
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 200;
    public static final int DEFAULT_SIZE = 20;

    public PageQuery {
        if (page < 1) { page = 1; }
        if (size < 1) { size = DEFAULT_SIZE; }
        if (size > MAX_SIZE) { size = MAX_SIZE; }
    }

    public int offset() {
        return (page - 1) * size;
    }
}
```

`PageResult.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import java.util.List;

/**
 * 分页结果。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(PageQuery query, long total, List<T> items) {
        return new PageResult<>(items, total, query.page(), query.size());
    }
}
```

`IdGenerator.java`：

```java
package io.github.luckybearbear.forge.platform.core;

/**
 * ID 生成 SPI。
 */
public interface IdGenerator {

    String nextId();
}
```

`UuidV7Generator.java`：

```java
package io.github.luckybearbear.forge.platform.core;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7：48-bit 毫秒时间戳 + 随机位，时间有序利于索引。
 */
public class UuidV7Generator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextId() {
        long ts = System.currentTimeMillis();
        byte[] r = new byte[10];
        random.nextBytes(r);
        byte[] b = new byte[16];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) ((ts >> ((5 - i) * 8)) & 0xFF);
        }
        b[6] = (byte) (0x70 | (r[0] & 0x0F));
        b[7] = r[1];
        b[8] = (byte) (0x80 | (r[2] & 0x3F));
        System.arraycopy(r, 3, b, 9, 7);
        ByteBuffer buffer = ByteBuffer.wrap(b);
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }
}
```

- [ ] **Step 4: 运行测试通过**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-core test
# 预期：Tests run: 10, Failures: 0（Result 4 + PageQuery 2 + BusinessException 2 + UuidV7 3，共 11；以实际为准全绿即可）
```

- [ ] **Step 5: Commit**

```bash
git add forge-platform/forge-platform-core && git commit -m "feat(platform-core): 统一响应/错误码/业务异常/UUIDv7"
```

---

### Task 4: platform-web（FilterOrder + GlobalExceptionHandler）

**Files:**
- Create: `forge-platform/forge-platform-web/src/main/java/io/github/luckybearbear/forge/platform/web/FilterOrder.java`
- Create: 同包 `GlobalExceptionHandler.java`
- Test: `forge-platform/forge-platform-web/src/test/java/io/github/luckybearbear/forge/platform/web/GlobalExceptionHandlerTest.java` + 同包 `TestRestController.java`

**Interfaces:**
- Consumes: Task 3 的 `Result`/`ErrorCodes`/`BusinessException`
- Produces: `class GlobalExceptionHandler`（starter 将以 `@ConditionalOnMissingBean` 注册）；`class FilterOrder`（int 常量：`CORS_FILTER=-200`、`REQUEST_BODY_WRAPPER=-150`、`TENANT_FILTER=-120`、`SECURITY_FILTER=-100`、`ACCESS_LOG_FILTER=-80`、`BUSINESS_FILTER_DEFAULT=0`）

- [ ] **Step 1: 写失败测试**

`TestRestController.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestRestController {

    record EchoRequest(@NotBlank String name) {}

    @GetMapping("/test/ok")
    Result<String> ok() {
        return Result.ok("hello");
    }

    @GetMapping("/test/biz-404")
    Result<Void> biz404() {
        throw BusinessException.of(ErrorCodes.NOT_FOUND, "m-1").withHttpStatus(404);
    }

    @GetMapping("/test/biz-default")
    Result<Void> bizDefault() {
        throw new BusinessException(ErrorCodes.CONCURRENT_LOCKED);
    }

    @PostMapping("/test/validate")
    Result<String> validate(@Valid @RequestBody EchoRequest request) {
        return Result.ok(request.name());
    }

    @GetMapping("/test/boom")
    Result<Void> boom() {
        throw new IllegalStateException("boom");
    }
}
```

`GlobalExceptionHandlerTest.java`：

```java
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
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-web test
# 预期 COMPILATION ERROR（FilterOrder/GlobalExceptionHandler 不存在）
```

- [ ] **Step 3: 写实现**

`FilterOrder.java`：

```java
package io.github.luckybearbear.forge.platform.web;

/**
 * 全底座 Filter 顺序段（借鉴 yudao WebFilterOrderEnum）。业务自定义 Filter 从
 * {@link #BUSINESS_FILTER_DEFAULT} 起排，禁止占用负数段。
 */
public final class FilterOrder {

    /** CORS 最前。 */
    public static final int CORS_FILTER = -200;
    /** 请求体可重复读包装（Phase 2）。 */
    public static final int REQUEST_BODY_WRAPPER = -150;
    /** 租户上下文解析（Phase 2 预留）。 */
    public static final int TENANT_FILTER = -120;
    /** 安全认证（Phase 2 预留，与 Spring Security 链衔接）。 */
    public static final int SECURITY_FILTER = -100;
    /** API 访问日志（Phase 2 预留）。 */
    public static final int ACCESS_LOG_FILTER = -80;
    /** 业务自定义 Filter 默认起点。 */
    public static final int BUSINESS_FILTER_DEFAULT = 0;

    private FilterOrder() {}
}
```

`GlobalExceptionHandler.java`：

```java
package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。HTTP 状态保留语义（默认按异常类别 4xx/5xx；BusinessException 默认 200 可携带覆盖），
 * 业务语义在 Result.code。@Order(0) 抢占三方 advice。
 */
@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        // 防御：业务码为成功码属于编码错误，改判 500（借鉴 yudao Issue #1196）
        if (Result.SUCCESS_CODE.equals(ex.errorCode().code())) {
            log.error("BusinessException carried success code, remapped to FORGE-0500", ex);
            return ResponseEntity.status(500).body(Result.error(ErrorCodes.INTERNAL_SERVER_ERROR));
        }
        log.warn("business exception [{}]: {}", ex.errorCode().code(), ex.getMessage());
        int status = ex.httpStatus() != null ? ex.httpStatus() : 200;
        return ResponseEntity.status(status).body(Result.error(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return badRequest(fieldErrors(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Map<String, String>>> handleBind(BindException ex) {
        return badRequest(fieldErrors(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return badRequest(errors);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidation(ValidationException ex) {
        return badRequestMessage(ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequestMessage("缺少请求参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequestMessage("参数类型不正确: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMessage());
        return badRequestMessage("请求体不可读或格式错误");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415)
                .body(Result.error(ErrorCodes.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405)
                .body(Result.error(ErrorCodes.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return badRequestMessage("上传文件超出大小限制");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(Result.error(ErrorCodes.NOT_FOUND, "请求路径不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception", ex);
        return ResponseEntity.status(500).body(Result.error(ErrorCodes.INTERNAL_SERVER_ERROR));
    }

    private Map<String, String> fieldErrors(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.put(e.getObjectName(), e.getDefaultMessage()));
        return errors;
    }

    private ResponseEntity<Result<Map<String, String>>> badRequest(Map<String, String> errors) {
        return ResponseEntity.status(400)
                .body(Result.error(ErrorCodes.PLATFORM_BAD_REQUEST, "请求参数不正确", errors));
    }

    private ResponseEntity<Result<Void>> badRequestMessage(String message) {
        return ResponseEntity.status(400).body(Result.error(ErrorCodes.PLATFORM_BAD_REQUEST, message));
    }
}
```

注意：上面 `badRequest` 调用了三参 `Result.error(code, message, data)` —— 在 Task 3 的 `Result` 中补一个静态方法：

```java
    /** 校验失败等需要携带结构化 data 的错误。 */
    public static <T> Result<T> error(ErrorCode errorCode, String message, T data) {
        Assert.notNull(errorCode, "errorCode must not be null");
        return new Result<>(errorCode.code(), message, data);
    }
```

（在 Task 3 提交后于本任务内补加，`ResultTest` 补一个用例：`Result.error(code,"m",Map.of("a","b")).data()` 返回该 map。）

- [ ] **Step 4: 运行测试通过**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-core,forge-platform/forge-platform-web test
# 预期全绿
```

- [ ] **Step 5: Commit**

```bash
git add forge-platform/forge-platform-core forge-platform/forge-platform-web \
  && git commit -m "feat(platform-web): 全局异常处理（yudao 覆盖清单）+ Filter 顺序常量"
```

---

### Task 5: platform-security / observability / test 三件套

**Files:**
- Create: `forge-platform/forge-platform-security/src/main/java/io/github/luckybearbear/forge/platform/security/` 下 `CurrentUser.java`、`CurrentTenant.java`、`CurrentUserProvider.java`、`CurrentTenantProvider.java`、`ThreadLocalUserProvider.java`、`ThreadLocalTenantProvider.java`
- Create: `forge-platform/forge-platform-observability/src/main/java/io/github/luckybearbear/forge/platform/observability/MdcKeys.java`
- Create: `forge-platform/forge-platform-test/src/main/java/io/github/luckybearbear/forge/platform/test/ResultAssertions.java`
- Test: security 模块 `ThreadLocalUserProviderTest.java`；test 模块 `ResultAssertionsTest.java`

**Interfaces:**
- Consumes: Task 3 `Result`/`ErrorCode`
- Produces:
  - `record CurrentUser(String userId, String username)`、`record CurrentTenant(String tenantId)`
  - `interface CurrentUserProvider { Optional<CurrentUser> currentUser(); }`、`interface CurrentTenantProvider { Optional<CurrentTenant> currentTenant(); }`
  - `ThreadLocalUserProvider implements CurrentUserProvider`：静态 `void set(CurrentUser)`、`void clear()`；Tenant 同构
  - `final class MdcKeys { String TRACE_ID="traceId"; String TENANT_ID="tenantId"; String USER_ID="userId"; }`
  - `final class ResultAssertions { static <T> void assertOk(Result<T> result, T expectedData); static void assertError(Result<?> result, String expectedCode); }`

- [ ] **Step 1: 写失败测试**

`ThreadLocalUserProviderTest.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThreadLocalUserProviderTest {

    private final ThreadLocalUserProvider provider = new ThreadLocalUserProvider();

    @AfterEach
    void tearDown() {
        ThreadLocalUserProvider.clear();
    }

    @Test
    void setThenReadInSameThread() {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        assertThat(provider.currentUser()).contains(new CurrentUser("u-1", "alice"));
    }

    @Test
    void emptyWhenUnset() {
        assertThat(provider.currentUser()).isEmpty();
    }

    @Test
    void isolatedAcrossThreads() throws InterruptedException {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertThat(new ThreadLocalUserProvider().currentUser()).isEmpty();
            } catch (Exception e) {
                failure.set(e);
            }
        });
        other.start();
        other.join();
        assertThat(failure.get()).isNull();
    }

    @Test
    void clearRemovesValue() {
        ThreadLocalUserProvider.set(new CurrentUser("u-1", "alice"));
        ThreadLocalUserProvider.clear();
        assertThat(provider.currentUser()).isEmpty();
    }
}
```

`ResultAssertionsTest.java`：

```java
package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultAssertionsTest {

    @Test
    void assertOkPassesOnMatchingData() {
        assertThatCode(() -> ResultAssertions.assertOk(Result.ok("x"), "x")).doesNotThrowAnyException();
    }

    @Test
    void assertErrorChecksCode() {
        Result<Void> result = Result.error(ErrorCodes.NOT_FOUND);
        assertThatCode(() -> ResultAssertions.assertError(result, "FORGE-0404"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ResultAssertions.assertError(result, "FORGE-0500"))
                .isInstanceOf(AssertionError.class);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-security,forge-platform/forge-platform-test test
# 预期 COMPILATION ERROR
```

- [ ] **Step 3: 写实现**

`CurrentUser.java` / `CurrentTenant.java`：

```java
package io.github.luckybearbear.forge.platform.security;

/** 当前用户快照。Phase 2 由 Spring Security 认证结果填充。 */
public record CurrentUser(String userId, String username) {}
```

```java
package io.github.luckybearbear.forge.platform.security;

/** 当前租户快照。Phase 2 多租户实施时评估 TransmittableThreadLocal / ScopedValue 替换。 */
public record CurrentTenant(String tenantId) {}
```

`CurrentUserProvider.java` / `CurrentTenantProvider.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> currentUser();
}
```

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public interface CurrentTenantProvider {
    Optional<CurrentTenant> currentTenant();
}
```

`ThreadLocalUserProvider.java`：

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

/** ThreadLocal 实现。Web 场景必须由 Filter 在 finally 中 clear，防止串号。 */
public class ThreadLocalUserProvider implements CurrentUserProvider {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        return Optional.ofNullable(HOLDER.get());
    }
}
```

`ThreadLocalTenantProvider.java`（同构）：

```java
package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public class ThreadLocalTenantProvider implements CurrentTenantProvider {

    private static final ThreadLocal<CurrentTenant> HOLDER = new ThreadLocal<>();

    public static void set(CurrentTenant tenant) {
        HOLDER.set(tenant);
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Override
    public Optional<CurrentTenant> currentTenant() {
        return Optional.ofNullable(HOLDER.get());
    }
}
```

`MdcKeys.java`：

```java
package io.github.luckybearbear.forge.platform.observability;

/** MDC key 约定，logback pattern 与链路追踪共用。 */
public final class MdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    private MdcKeys() {}
}
```

`ResultAssertions.java`：

```java
package io.github.luckybearbear.forge.platform.test;

import io.github.luckybearbear.forge.platform.core.Result;
import org.assertj.core.api.Assertions;

/** Demo/业务测试常用断言。 */
public final class ResultAssertions {

    public static <T> void assertOk(Result<T> result, T expectedData) {
        Assertions.assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
        Assertions.assertThat(result.data()).isEqualTo(expectedData);
    }

    public static void assertError(Result<?> result, String expectedCode) {
        Assertions.assertThat(result.code()).isEqualTo(expectedCode);
    }

    private ResultAssertions() {}
}
```

- [ ] **Step 4: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-security,forge-platform/forge-platform-test test
# 预期全绿
git add forge-platform/forge-platform-security forge-platform/forge-platform-observability forge-platform/forge-platform-test \
  && git commit -m "feat(platform): 用户/租户上下文抽象 + MDC 常量 + Result 断言工具"
```

---

### Task 6: platform-data（审计字段基类，半成品）

**Files:**
- Create: `forge-platform/forge-platform-data/src/main/java/io/github/luckybearbear/forge/platform/data/AbstractAuditableEntity.java`
- Test: `forge-platform/forge-platform-data/src/test/java/io/github/luckybearbear/forge/platform/data/AbstractAuditableEntityTest.java`
- Create: `forge-platform/forge-platform-data/README.md`

**Interfaces:**
- Consumes: Spring Data JDBC 注解（版本由 Boot BOM 管理）
- Produces: `abstract class AbstractAuditableEntity`（字段：`String id` `@Id`、`long version` `@Version`、`String createdBy` `@CreatedBy`、`Instant createdAt` `@CreatedDate`、`String updatedBy` `@LastModifiedBy`、`Instant updatedAt` `@LastModifiedDate`，含 getter/setter）；应用侧启用审计需 `@EnableJdbcAuditing`（Phase 2）

- [ ] **Step 1: 写失败测试（反射断言注解就位）**

`AbstractAuditableEntityTest.java`：

```java
package io.github.luckybearbear.forge.platform.data;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import static org.assertj.core.api.Assertions.assertThat;

class AbstractAuditableEntityTest {

    @Test
    void auditFieldsCarrySpringDataAnnotations() {
        Map<String, Class<?>> expected = Map.of(
                "id", Id.class,
                "version", Version.class,
                "createdBy", CreatedBy.class,
                "createdAt", CreatedDate.class,
                "updatedBy", LastModifiedBy.class,
                "updatedAt", LastModifiedDate.class);
        expected.forEach((fieldName, annotation) -> {
            Field field = AbstractAuditableEntity.class.getDeclaredField(fieldName);
            assertThat(field.getAnnotation(annotation))
                    .as("field %s should carry @%s", fieldName, annotation.getSimpleName())
                    .isNotNull();
        });
    }
}
```

（`getDeclaredField` 抛出的 `NoSuchFieldException` 为受检异常——把测试方法签名加 `throws Exception`。）

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-data test
# 预期 COMPILATION ERROR
```

- [ ] **Step 3: 写实现**

`AbstractAuditableEntity.java`：

```java
package io.github.luckybearbear.forge.platform.data;

import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

/**
 * Spring Data JDBC 审计基类：ID（String/UUIDv7 策略）+ 乐观锁 + 审计字段。
 * 应用侧需 @EnableJdbcAuditing + AuditorAware Bean（Phase 2 随 identity 服务提供）。
 * 本阶段为半成品：无实体、Demo 不连库、不进 forge-spring-boot-starter。
 */
public abstract class AbstractAuditableEntity {

    @Id
    private String id;

    @Version
    private long version;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }

    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getUpdatedBy() { return updatedBy; }

    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

`forge-platform/forge-platform-data/README.md`：

```markdown
# forge-platform-data

数据访问基线（Spring Data JDBC）。Phase 1 半成品：

- `AbstractAuditableEntity`：ID（String/UUIDv7）+ 乐观锁 `@Version` + 审计字段
- Flyway 规范：见 `docs/database/flyway-conventions.md`（目录 `src/main/resources/db/migration`，命名 `V{yyyyMMddHHmm}__{snake_case}.sql`）
- 状态：⚙️ 半成品（有意）；Phase 2 随 PostgreSQL + Testcontainers 实装 `@EnableJdbcAuditing`、AuditorAware、多租户列策略
- **不进 forge-spring-boot-starter**：连库模块自行引入，保证非 DB 应用零数据库依赖
```

- [ ] **Step 4: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-platform/forge-platform-data test
git add forge-platform/forge-platform-data && git commit -m "feat(platform-data): Spring Data JDBC 审计基类（半成品）"
```

---

### Task 7: forge-contract-mold（示例契约）

**Files:**
- Create: `forge-contracts/forge-contract-mold/src/main/java/io/github/luckybearbear/forge/contract/mold/` 下 `MoldStatus.java`、`MoldDTO.java`、`MoldRepairRequested.java`、`MoldQueryService.java`、`MoldErrorCodes.java`
- Test: `forge-contracts/forge-contract-mold/src/test/java/io/github/luckybearbear/forge/contract/mold/MoldErrorCodesTest.java`

**Interfaces:**
- Consumes: Task 3 `ErrorCode`
- Produces（Demo 两模块共用）：
  - `enum MoldStatus { IN_SERVICE, UNDER_MAINTENANCE, RETIRED }`
  - `record MoldDTO(String id, String name, String specification, MoldStatus status)`
  - `record MoldRepairRequested(String moldId, String description, Instant occurredAt)`（领域事件）
  - `interface MoldQueryService { MoldDTO findById(String moldId); }`
  - `enum MoldErrorCodes implements ErrorCode { MOLD_NOT_FOUND("MOLD-0404", "模具不存在: {}") }`

- [ ] **Step 1: 写失败测试**

`MoldErrorCodesTest.java`：

```java
package io.github.luckybearbear.forge.contract.mold;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MoldErrorCodesTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}-\\d{4}$");

    @Test
    void allCodesFollowSegmentedPattern() {
        for (MoldErrorCodes code : MoldErrorCodes.values()) {
            assertThat(code.code()).matches(CODE_PATTERN);
        }
    }

    @Test
    void notFoundCarriesPlaceholderMessage() {
        assertThat(MoldErrorCodes.MOLD_NOT_FOUND.defaultMessage()).isEqualTo("模具不存在: {}");
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-contracts/forge-contract-mold test
# 预期 COMPILATION ERROR
```

- [ ] **Step 3: 写实现（五个文件）**

```java
package io.github.luckybearbear.forge.contract.mold;

public enum MoldStatus {
    IN_SERVICE, UNDER_MAINTENANCE, RETIRED
}
```

```java
package io.github.luckybearbear.forge.contract.mold;

/** 模具快照 DTO，跨模块同步查询用。 */
public record MoldDTO(String id, String name, String specification, MoldStatus status) {}
```

```java
package io.github.luckybearbear.forge.contract.mold;

import java.time.Instant;

/** 模具报修事件：mold 模块发布，maintenance 模块监听。 */
public record MoldRepairRequested(String moldId, String description, Instant occurredAt) {}
```

```java
package io.github.luckybearbear.forge.contract.mold;

/** 跨模块同步查询契约：mold 模块实现，其他模块消费。 */
public interface MoldQueryService {

    MoldDTO findById(String moldId);
}
```

```java
package io.github.luckybearbear.forge.contract.mold;

import io.github.luckybearbear.forge.platform.core.ErrorCode;

/** 模具域错误码（MOLD- 前缀，登记于 docs/standards/error-codes.md）。 */
public enum MoldErrorCodes implements ErrorCode {

    MOLD_NOT_FOUND("MOLD-0404", "模具不存在: {}");

    private final String code;
    private final String defaultMessage;

    MoldErrorCodes(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
}
```

- [ ] **Step 4: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-contracts/forge-contract-mold test
git add forge-contracts && git commit -m "feat(contract-mold): 示例契约（DTO/事件/查询接口/错误码）"
```

---

### Task 8: forge-spring-boot-starter

**Files:**
- Create: `forge-starters/forge-spring-boot-starter/pom.xml`
- Create: `forge-starters/forge-spring-boot-starter/src/main/java/io/github/luckybearbear/forge/starter/web/ForgeWebAutoConfiguration.java`
- Create: `.../starter/openapi/ForgeOpenApiAutoConfiguration.java`、`ForgeOpenApiProperties.java`
- Create: `.../starter/defaults/ForgeDefaultsEnvironmentPostProcessor.java`
- Create: `forge-starters/forge-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `forge-starters/forge-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- Create: `forge-starters/forge-spring-boot-starter/src/main/resources/logback-spring.xml`
- Test: `.../src/test/java/io/github/luckybearbear/forge/starter/StarterSmokeTest.java`

**Interfaces:**
- Consumes: Task 3/4 的 core 与 web；springdoc 3.2.0
- Produces: 引入本 starter 即获得：webmvc + validation + actuator（默认仅 health,info）+ 全局异常处理 + OpenAPI 基线（`forge.openapi.*`）+ 日志基线（logback-spring，MDC traceId 占位）

- [ ] **Step 1: 写 starter pom（并把子模块挂入 forge-starters 聚合）**

`forge-starters/pom.xml` 追加：

```xml
  <modules>
    <module>forge-spring-boot-starter</module>
  </modules>
```

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
  <artifactId>forge-spring-boot-starter</artifactId>
  <name>Forge :: Starter :: Spring Boot</name>
  <dependencies>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-core</artifactId>
    </dependency>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-web</artifactId>
    </dependency>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-platform-observability</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`forge-starters/pom.xml` 的 parent 为 forge-parent（Task 2 已建）。

- [ ] **Step 2: 写失败冒烟测试**

`StarterSmokeTest.java`：

```java
package io.github.luckybearbear.forge.starter;

import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StarterSmokeTest.SmokeApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StarterSmokeTest {

    private static final Instant FIXED = Instant.parse("2026-11-09T00:00:00Z");

    @Autowired
    TestRestTemplate rest;

    @SpringBootApplication
    @RestController
    static class SmokeApp {

        record EchoRequest(@NotBlank String name) {}

        @GetMapping("/smoke/time")
        Result<Instant> time() {
            return Result.ok(FIXED);
        }

        @PostMapping("/smoke/echo")
        Result<String> echo(@Valid @RequestBody EchoRequest request) {
            return Result.ok(request.name());
        }
    }

    @Test
    void instantSerializesAsIso8601() {
        ResponseEntity<String> response = rest.getForEntity("/smoke/time", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("2026-11-09T00:00:00Z").contains("\"OK\"");
    }

    @Test
    void validationFailureMappedByStarter() {
        ResponseEntity<String> response = rest.postForEntity("/smoke/echo",
                new org.springframework.http.HttpEntity<>("{\"name\":\"\"}",
                        org.springframework.http.HttpHeaders(org.springframework.http.MediaType.APPLICATION_JSON)),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("FORGE-0400");
    }

    @Test
    void actuatorHealthExposed() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void openApiDocsAvailable() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
```

（`HttpEntity`/`HttpHeaders` 构造如编译不过，用 `rest.postForEntity("/smoke/echo", body, String.class)` 的重载传 `org.springframework.http.HttpEntity`，或最简单：`new HttpEntity<>("{\"name\":\"\"}", headers)` 标准写法——以能编译的最朴素形式为准。）

- [ ] **Step 3: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-starters/forge-spring-boot-starter test
# 预期 COMPILATION ERROR 或 context 启动失败（自动配置未就位）
```

- [ ] **Step 4: 写实现**

`ForgeWebAutoConfiguration.java`：

```java
package io.github.luckybearbear.forge.starter.web;

import io.github.luckybearbear.forge.platform.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ForgeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
```

`ForgeOpenApiProperties.java`：

```java
package io.github.luckybearbear.forge.starter.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "forge.openapi")
public record ForgeOpenApiProperties(
        @DefaultValue("Forge Application") String title,
        @DefaultValue("") String description,
        @DefaultValue("1.0.0") String version) {
}
```

`ForgeOpenApiAutoConfiguration.java`：

```java
package io.github.luckybearbear.forge.starter.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(ForgeOpenApiProperties.class)
public class ForgeOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI forgeOpenApi(ForgeOpenApiProperties properties) {
        return new OpenAPI().info(new Info()
                .title(properties.title())
                .description(properties.description())
                .version(properties.version()));
    }
}
```

`ForgeDefaultsEnvironmentPostProcessor.java`：

```java
package io.github.luckybearbear.forge.starter.defaults;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** 低优先级默认值：应用自身配置可覆盖。 */
public class ForgeDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("management.endpoints.web.exposure.include", "health,info");
        environment.getPropertySources().addLast(new MapPropertySource("forgeDefaults", defaults));
    }
}
```

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
io.github.luckybearbear.forge.starter.web.ForgeWebAutoConfiguration
io.github.luckybearbear.forge.starter.openapi.ForgeOpenApiAutoConfiguration
```

`META-INF/spring.factories`：

```
org.springframework.boot.env.EnvironmentPostProcessor=\
  io.github.luckybearbear.forge.starter.defaults.ForgeDefaultsEnvironmentPostProcessor
```

`logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="appName" source="spring.application.name" defaultValue="forge-app"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%X{traceId:-}] [${appName}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

（生产结构化日志：Phase 4 随 Boot 4 structured logging 配置项验证后启用，见 spec §6.8。）

`forge-starters/forge-spring-boot-starter/README.md`：

```markdown
# forge-spring-boot-starter

底座统一入口。引入即获得：webmvc + validation + actuator（默认仅 health,info）+ 全局异常处理
+ OpenAPI 基线（forge.openapi.title/description/version）+ 日志基线（logback-spring.xml，MDC traceId）。

状态：✅ Phase 1 完整；后续 starter 命名规范 forge-<feature>-starter。
```

- [ ] **Step 5: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-starters/forge-spring-boot-starter test
# 预期 4 个测试全绿。注意：instantSerializesAsIso8601 若失败（Jackson 3 默认时间戳化），
# 在 ForgeDefaultsEnvironmentPostProcessor 的 defaults 里加
// defaults.put("spring.jackson.serialization.write-dates-as-timestamps", "false");
# （Jackson 3 默认已禁用时间戳，理论上不需要；以实测为准）
git add forge-starters && git commit -m "feat(starter): 自动配置（异常处理/OpenAPI/默认值后处理器）+ 日志基线"
```

---

### Task 9: Demo — mold 业务模块

**Files:**
- Create: `forge-distributions/forge-demo-app/pom.xml`
- Create: `forge-distributions/forge-demo-app/src/main/java/io/github/luckybearbear/forge/demo/mold/` 下 `package-info.java`、`MoldQueryServiceImpl.java`、`api/MoldController.java`、`api/RegisterMoldRequest.java`、`api/RequestRepairRequest.java`、`app/MoldService.java`、`domain/MoldEntity.java`、`domain/MoldRegistry.java`
- Test: `.../src/test/java/io/github/luckybearbear/forge/demo/mold/app/MoldServiceTest.java`

**Interfaces:**
- Consumes: Task 3 core、Task 7 契约（`MoldDTO`/`MoldStatus`/`MoldRepairRequested`/`MoldQueryService`/`MoldErrorCodes`）
- Produces:
  - `MoldService`：`MoldDTO register(RegisterMoldRequest request)`、`MoldDTO findById(String id)`、`void requestRepair(String moldId, RequestRepairRequest request)`
  - REST：`POST /api/molds`、`GET /api/molds/{id}`、`POST /api/molds/{id}/repairs`
  - Bean `MoldQueryServiceImpl implements MoldQueryService`（模块根包 = Modulith 默认暴露面）

- [ ] **Step 1: 写 demo-app pom（并把子模块挂入 forge-distributions 聚合）**

`forge-distributions/pom.xml` 追加：

```xml
  <modules>
    <module>forge-demo-app</module>
  </modules>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.luckybearbear</groupId>
    <artifactId>forge-distributions</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>forge-demo-app</artifactId>
  <name>Forge :: Distributions :: Demo App</name>
  <description>可运行 Demo：双业务模块演示契约 + 事件 + Modulith 边界</description>
  <dependencies>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-spring-boot-starter</artifactId>
      <version>${revision}</version>
    </dependency>
    <dependency>
      <groupId>io.github.luckybearbear</groupId>
      <artifactId>forge-contract-mold</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build><plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <executions>
        <execution>
          <goals><goal>repackage</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins></build>
</project>
```

- [ ] **Step 2: 写失败测试**

`MoldServiceTest.java`：

```java
package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MoldServiceTest {

    private ApplicationEventPublisher publisher;
    private MoldService service;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        IdGenerator idGenerator = new UuidV7Generator();
        service = new MoldService(new MoldRegistry(), idGenerator, publisher);
    }

    @Test
    void registerCreatesInServiceMold() {
        var dto = service.register(new RegisterMoldRequest("测试模具", "两腔"));
        assertThat(dto.name()).isEqualTo("测试模具");
        assertThat(dto.status()).isEqualTo(MoldStatus.IN_SERVICE);
        assertThat(dto.id()).isNotBlank();
    }

    @Test
    void findByIdThrowsMoldNotFoundWith404() {
        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.errorCode().code()).isEqualTo("MOLD-0404");
                            assertThat(ex.httpStatus()).isEqualTo(404);
                        });
    }

    @Test
    void requestRepairPublishesContractEvent() {
        var dto = service.register(new RegisterMoldRequest("测试模具", "两腔"));
        service.requestRepair(dto.id(), new RequestRepairRequest("导柱磨损"));

        ArgumentCaptor<MoldRepairRequested> captor = ArgumentCaptor.forClass(MoldRepairRequested.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().moldId()).isEqualTo(dto.id());
        assertThat(captor.getValue().description()).isEqualTo("导柱磨损");
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    @Test
    void requestRepairOnMissingMoldPublishesNothing() {
        assertThatThrownBy(() -> service.requestRepair("missing", new RequestRepairRequest("x")))
                .isInstanceOf(BusinessException.class);
        verify(publisher, never()).publishEvent(any());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app test
# 预期 COMPILATION ERROR
```

- [ ] **Step 4: 写实现**

`mold/package-info.java`：

```java
/**
 * 模具模块：登记、查询、报修（发布 MoldRepairRequested）。
 * 根包直接类型即 Modulith 暴露面（如 MoldQueryServiceImpl）；api/app/domain 为模块内部包。
 */
@org.springframework.modulith.ApplicationModule
package io.github.luckybearbear.forge.demo.mold;
```

`domain/MoldEntity.java`：

```java
package io.github.luckybearbear.forge.demo.mold.domain;

import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import java.time.Instant;

/** 内存域对象（Phase 1 无 DB；Phase 2 换 Spring Data JDBC 实体）。 */
public record MoldEntity(String id, String name, String specification, MoldStatus status, Instant createdAt) {}
```

`domain/MoldRegistry.java`：

```java
package io.github.luckybearbear.forge.demo.mold.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MoldRegistry {

    private final Map<String, MoldEntity> store = new ConcurrentHashMap<>();

    public void save(MoldEntity entity) {
        store.put(entity.id(), entity);
    }

    public Optional<MoldEntity> find(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
```

`api/RegisterMoldRequest.java`：

```java
package io.github.luckybearbear.forge.demo.mold.api;

import jakarta.validation.constraints.NotBlank;

public record RegisterMoldRequest(@NotBlank String name, String specification) {}
```

`api/RequestRepairRequest.java`：

```java
package io.github.luckybearbear.forge.demo.mold.api;

import jakarta.validation.constraints.NotBlank;

public record RequestRepairRequest(@NotBlank String description) {}
```

`app/MoldService.java`：

```java
package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.domain.MoldEntity;
import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MoldService {

    private final MoldRegistry registry;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public MoldService(MoldRegistry registry, IdGenerator idGenerator,
                       ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
    }

    public MoldDTO register(RegisterMoldRequest request) {
        String id = idGenerator.nextId();
        registry.save(new MoldEntity(id, request.name(), request.specification(),
                MoldStatus.IN_SERVICE, Instant.now()));
        return toDto(registry.find(id).orElseThrow());
    }

    public MoldDTO findById(String id) {
        return registry.find(id).map(this::toDto)
                .orElseThrow(() -> BusinessException.of(MoldErrorCodes.MOLD_NOT_FOUND, id)
                        .withHttpStatus(404));
    }

    public void requestRepair(String moldId, RequestRepairRequest request) {
        findById(moldId); // 确保存在，不存在则抛 MOLD-0404
        eventPublisher.publishEvent(new MoldRepairRequested(moldId, request.description(), Instant.now()));
    }

    private MoldDTO toDto(MoldEntity entity) {
        return new MoldDTO(entity.id(), entity.name(), entity.specification(), entity.status());
    }
}
```

（`MoldService` 头部需 import `io.github.luckybearbear.forge.contract.mold.MoldErrorCodes`。）

`MoldQueryServiceImpl.java`（模块根包，暴露面）：

```java
package io.github.luckybearbear.forge.demo.mold;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import org.springframework.stereotype.Component;

/** 契约实现：其他模块经此同步查询，禁止直接访问模块内部。 */
@Component
public class MoldQueryServiceImpl implements MoldQueryService {

    private final MoldService moldService;

    public MoldQueryServiceImpl(MoldService moldService) {
        this.moldService = moldService;
    }

    @Override
    public MoldDTO findById(String moldId) {
        return moldService.findById(moldId);
    }
}
```

`api/MoldController.java`：

```java
package io.github.luckybearbear.forge.demo.mold.api;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/molds")
public class MoldController {

    private final MoldService moldService;

    public MoldController(MoldService moldService) {
        this.moldService = moldService;
    }

    @PostMapping
    public Result<MoldDTO> register(@Valid @RequestBody RegisterMoldRequest request) {
        return Result.ok(moldService.register(request));
    }

    @GetMapping("/{id}")
    public Result<MoldDTO> get(@PathVariable String id) {
        return Result.ok(moldService.findById(id));
    }

    @PostMapping("/{id}/repairs")
    public Result<Void> requestRepair(@PathVariable String id,
                                      @Valid @RequestBody RequestRepairRequest request) {
        moldService.requestRepair(id, request);
        return Result.ok();
    }
}
```

`MoldRegistry`/`IdGenerator` 需注册为 Bean——在模块内加 `app/MoldModuleConfiguration.java`：

```java
package io.github.luckybearbear.forge.demo.mold.app;

import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MoldModuleConfiguration {

    @Bean
    public MoldRegistry moldRegistry() {
        return new MoldRegistry();
    }

    @Bean
    public IdGenerator idGenerator() {
        return new UuidV7Generator();
    }
}
```

- [ ] **Step 5: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app test
# 预期 MoldServiceTest 4 个用例全绿
git add forge-distributions && git commit -m "feat(demo): mold 业务模块（登记/查询/报修发事件）"
```

---

### Task 10: Demo — maintenance 业务模块

**Files:**
- Create: `forge-distributions/forge-demo-app/src/main/java/io/github/luckybearbear/forge/demo/maintenance/` 下 `package-info.java`、`api/MaintenanceController.java`、`api/MaintenanceOrderDTO.java`、`app/MaintenanceService.java`、`app/MoldRepairRequestedListener.java`、`domain/MaintenanceOrder.java`、`domain/MaintenanceRegistry.java`

**Interfaces:**
- Consumes: Task 7 契约（`MoldRepairRequested` 事件、`MoldQueryService` 同步查询）、Task 9 的 `IdGenerator` Bean
- Produces:
  - REST：`GET /api/maintenances/{id}`、`GET /api/maintenances?moldId={id}`
  - 监听器：`@ApplicationModuleListener` 异步消费 `MoldRepairRequested`，经 `MoldQueryService` 补全模具名后生成维修单

- [ ] **Step 1: 写失败测试**

`.../maintenance/app/MaintenanceServiceTest.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import io.github.luckybearbear.forge.platform.core.UuidV7Generator;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceServiceTest {

    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        MoldQueryService moldQueryService = moldId ->
                new MoldDTO(moldId, "测试模具", "两腔", MoldStatus.IN_SERVICE);
        IdGenerator idGenerator = new UuidV7Generator();
        service = new MaintenanceService(new MaintenanceRegistry(), idGenerator, moldQueryService);
    }

    @Test
    void createsOrderFromEventEnrichedByContractQuery() {
        var dto = service.createFromEvent("m-1", "导柱磨损", Instant.now());
        assertThat(dto.moldId()).isEqualTo("m-1");
        assertThat(dto.moldName()).isEqualTo("测试模具");  // 由 MoldQueryService 补全
        assertThat(dto.description()).isEqualTo("导柱磨损");
        assertThat(dto.status()).isEqualTo("OPEN");
    }

    @Test
    void listsOrdersByMold() {
        service.createFromEvent("m-1", "a", Instant.now());
        service.createFromEvent("m-1", "b", Instant.now());
        service.createFromEvent("m-2", "c", Instant.now());
        assertThat(service.findByMoldId("m-1")).hasSize(2);
        assertThat(service.findByMoldId("m-2")).hasSize(1);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app test
# 预期 COMPILATION ERROR（maintenance 包不存在）
```

- [ ] **Step 3: 写实现**

`maintenance/package-info.java`：

```java
/**
 * 维修单模块：异步消费 MoldRepairRequested（契约事件），经 MoldQueryService（契约）补全模具信息。
 * 禁止 import io.github.luckybearbear.forge.demo.mold.** —— Modulith verify() 强制。
 */
@org.springframework.modulith.ApplicationModule
package io.github.luckybearbear.forge.demo.maintenance;
```

`domain/MaintenanceOrder.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.domain;

import java.time.Instant;

public record MaintenanceOrder(String id, String moldId, String moldName,
                               String description, String status, Instant createdAt) {}
```

`domain/MaintenanceRegistry.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MaintenanceRegistry {

    private final Map<String, MaintenanceOrder> store = new ConcurrentHashMap<>();
    private final List<String> moldOrder = new CopyOnWriteArrayList<>();

    public void save(MaintenanceOrder order) {
        store.put(order.id(), order);
        moldOrder.add(order.moldId() + ":" + order.id());
    }

    public Optional<MaintenanceOrder> find(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<MaintenanceOrder> findByMoldId(String moldId) {
        return moldOrder.stream()
                .filter(entry -> entry.startsWith(moldId + ":"))
                .map(entry -> store.get(entry.substring(entry.indexOf(':') + 1)))
                .toList();
    }
}
```

`app/MaintenanceService.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.demo.maintenance.api.MaintenanceOrderDTO;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceOrder;
import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import io.github.luckybearbear.forge.platform.core.IdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceService {

    private final MaintenanceRegistry registry;
    private final IdGenerator idGenerator;
    private final MoldQueryService moldQueryService;

    public MaintenanceService(MaintenanceRegistry registry, IdGenerator idGenerator,
                              MoldQueryService moldQueryService) {
        this.registry = registry;
        this.idGenerator = idGenerator;
        this.moldQueryService = moldQueryService;
    }

    public MaintenanceOrderDTO createFromEvent(String moldId, String description, Instant occurredAt) {
        var mold = moldQueryService.findById(moldId); // 契约同步查询：事件只携带 id，消费方补全当前状态
        var order = new MaintenanceOrder(idGenerator.nextId(), moldId, mold.name(),
                description, "OPEN", occurredAt);
        registry.save(order);
        return toDto(order);
    }

    public MaintenanceOrderDTO findById(String id) {
        return registry.find(id).map(this::toDto).orElse(null);
    }

    public List<MaintenanceOrderDTO> findByMoldId(String moldId) {
        return registry.findByMoldId(moldId).stream().map(this::toDto).toList();
    }

    private MaintenanceOrderDTO toDto(MaintenanceOrder order) {
        return new MaintenanceOrderDTO(order.id(), order.moldId(), order.moldName(),
                order.description(), order.status(), order.createdAt());
    }
}
```

`api/MaintenanceOrderDTO.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.api;

import java.time.Instant;

public record MaintenanceOrderDTO(String id, String moldId, String moldName,
                                  String description, String status, Instant createdAt) {}
```

`app/MoldRepairRequestedListener.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.contract.mold.MoldRepairRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 异步事务提交后监听（Modulith 默认）。无持久化 Event Publication Registry 时事件为内存投递
 * （spec §10：registry 持久化 Phase 2 随 PostgreSQL 启用）。
 */
@Component
public class MoldRepairRequestedListener {

    private final MaintenanceService maintenanceService;

    public MoldRepairRequestedListener(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @ApplicationModuleListener
    public void on(MoldRepairRequested event) {
        maintenanceService.createFromEvent(event.moldId(), event.description(), event.occurredAt());
    }
}
```

注意：`@ApplicationModuleListener` 的实际包名以 spring-modulith 2.1.2 为准——标准位置是 `org.springframework.modulith.events.ApplicationModuleListener`；若编译报找不到，改为 `org.springframework.modulith.core.ApplicationModuleListener`（先 `JAVA_HOME="$FORGE_JDK" ./mvnw -q dependency:tree -pl forge-distributions/forge-demo-app | grep modulith` 后到本地仓库 jar 里 `jar tf` 确认）。

`api/MaintenanceController.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.api;

import io.github.luckybearbear.forge.demo.maintenance.app.MaintenanceService;
import io.github.luckybearbear.forge.platform.core.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenances")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/{id}")
    public Result<MaintenanceOrderDTO> get(@PathVariable String id) {
        return Result.ok(maintenanceService.findById(id));
    }

    @GetMapping
    public Result<List<MaintenanceOrderDTO>> list(@RequestParam String moldId) {
        return Result.ok(maintenanceService.findByMoldId(moldId));
    }
}
```

`app/MaintenanceModuleConfiguration.java`：

```java
package io.github.luckybearbear.forge.demo.maintenance.app;

import io.github.luckybearbear.forge.demo.maintenance.domain.MaintenanceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceModuleConfiguration {

    @Bean
    public MaintenanceRegistry maintenanceRegistry() {
        return new MaintenanceRegistry();
    }
}
```

- [ ] **Step 4: 运行测试通过 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app test
# 预期 mold + maintenance 测试全绿
git add forge-distributions && git commit -m "feat(demo): maintenance 模块（事件监听 + 契约查询补全）"
```

---

### Task 11: Demo 装配 + Modulith 边界验证 + 全链路集成测试

**Files:**
- Create: `forge-distributions/forge-demo-app/src/main/java/io/github/luckybearbear/forge/demo/ForgeDemoApplication.java`
- Create: `forge-distributions/forge-demo-app/src/main/resources/application.yaml`
- Test: `.../src/test/java/io/github/luckybearbear/forge/demo/ModulithBoundaryTest.java`、`DemoFlowIntegrationTest.java`
- Create: `forge-distributions/forge-demo-app/README.md`

**Interfaces:**
- Consumes: Task 9/10 全部产物
- Produces: 可运行应用（`java -jar` / `spring-boot:run`）；`ApplicationModules.of(ForgeDemoApplication.class).verify()` 边界守护

- [ ] **Step 1: 写主应用与配置**

`ForgeDemoApplication.java`：

```java
package io.github.luckybearbear.forge.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Modulith 根包：io.github.luckybearbear.forge.demo。 */
@SpringBootApplication
public class ForgeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeDemoApplication.class, args);
    }
}
```

`application.yaml`：

```yaml
spring:
  application:
    name: forge-demo-app
server:
  port: 8080
forge:
  openapi:
    title: Forge Demo API
    description: 模块化单体演示：mold + maintenance（契约 + 事件 + 边界）
    version: 1.0.0-SNAPSHOT
```

- [ ] **Step 2: 写失败测试**

`ModulithBoundaryTest.java`：

```java
package io.github.luckybearbear.forge.demo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithBoundaryTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(ForgeDemoApplication.class);

    @Test
    void moduleBoundariesAreSound() {
        MODULES.verify();
    }

    @Test
    void containsExactlyMoldAndMaintenance() {
        assertThat(MODULES.stream().map(m -> m.getName().getLocalName()))
                .containsExactlyInAnyOrder("mold", "maintenance");
    }
}
```

`DemoFlowIntegrationTest.java`：

```java
package io.github.luckybearbear.forge.demo;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ForgeDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoFlowIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void fullFlowRegisterFindRepairAndMaintenanceOrder() {
        // 1. 登记模具
        ResponseEntity<String> register = rest.postForEntity("/api/molds",
                json("{\"name\":\"测试模具\",\"specification\":\"两腔\"}"), String.class);
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        String moldId = JsonPath.read(register.getBody(), "$.data.id");
        assertThat((String) JsonPath.read(register.getBody(), "$.data.status")).isEqualTo("IN_SERVICE");

        // 2. 查询
        ResponseEntity<String> found = rest.getForEntity("/api/molds/" + moldId, String.class);
        assertThat(JsonPath.read(found.getBody(), "$.code")).isEqualTo("OK");

        // 3. 不存在 → MOLD-0404 + HTTP 404
        ResponseEntity<String> missing = rest.getForEntity("/api/molds/missing", String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(JsonPath.read(missing.getBody(), "$.code")).isEqualTo("MOLD-0404");

        // 4. 校验失败 → FORGE-0400 + 400 + 字段 map
        ResponseEntity<String> invalid = rest.postForEntity("/api/molds",
                json("{\"name\":\"\",\"specification\":\"x\"}"), String.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(JsonPath.read(invalid.getBody(), "$.code")).isEqualTo("FORGE-0400");
        assertThat(JsonPath.read(invalid.getBody(), "$.data.name")).isNotBlank();

        // 5. 报修 → 异步事件
        ResponseEntity<String> repair = rest.postForEntity("/api/molds/" + moldId + "/repairs",
                json("{\"description\":\"导柱磨损\"}"), String.class);
        assertThat(JsonPath.read(repair.getBody(), "$.code")).isEqualTo("OK");

        // 6. 等待 maintenance 生成维修单（异步），并验证契约查询补全的模具名
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<String> orders = rest.getForEntity(
                    "/api/maintenances?moldId=" + moldId, String.class);
            java.util.List<?> items = JsonPath.read(orders.getBody(), "$.data");
            assertThat(items).hasSize(1);
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].moldName"))
                    .isEqualTo("测试模具");
            assertThat((String) JsonPath.read(orders.getBody(), "$.data[0].description"))
                    .isEqualTo("导柱磨损");
        });

        // 7. 维修单详情
        ResponseEntity<String> orderList = rest.getForEntity(
                "/api/maintenances?moldId=" + moldId, String.class);
        String orderId = JsonPath.read(orderList.getBody(), "$.data[0].id");
        ResponseEntity<String> detail = rest.getForEntity("/api/maintenances/" + orderId, String.class);
        assertThat(JsonPath.read(detail.getBody(), "$.code")).isEqualTo("OK");
    }

    @Test
    void openApiAndHealthAreServed() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
    }
}
```

- [ ] **Step 3: 运行全量 demo 测试**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app test
# 预期：MoldServiceTest(4) + MaintenanceServiceTest(2) + ModulithBoundaryTest(2) + DemoFlowIntegrationTest(2) 全绿
```

若上下文启动失败且日志指向 Event Publication Registry 需要持久化存储（预期**不会**发生，见 spec §10）：
将 `MoldRepairRequestedListener` 上的 `@ApplicationModuleListener` 换成 `org.springframework.context.event.EventListener`（同步投递，验证流程不受影响），并在模块 README 记录该偏差；Modulith verify() 不受影响。

- [ ] **Step 4: 负向边界验证（手工确认，不提交）**

临时在 `MaintenanceService.java` 顶部加 `import io.github.luckybearbear.forge.demo.mold.domain.MoldRegistry;`（仅 import 不使用），运行 `JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app -Dtest=ModulithBoundaryTest test`，预期 `moduleBoundariesAreSound` 失败并报告非法跨模块引用；确认后删除该 import 并重跑至绿。

- [ ] **Step 5: 应用可启动验证 + Commit**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw -q -pl forge-distributions/forge-demo-app spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=18080 &
sleep 30
curl -s http://localhost:18080/actuator/health   # 预期 {"status":"UP"...}
kill %1
git add forge-distributions && git commit -m "feat(demo): 应用装配 + Modulith 边界验证 + 全链路集成测试"
```

`forge-distributions/forge-demo-app/README.md`：

```markdown
# forge-demo-app

可运行 Demo：mold + maintenance 双业务模块，演示契约同步查询（MoldQueryService）+ 领域事件
（MoldRepairRequested）+ Modulith 边界强制。内存存储、零 DB 依赖。

运行：`JAVA_HOME=<jdk25> ./mvnw -pl forge-distributions/forge-demo-app spring-boot:run`

体验流：POST /api/molds → GET /api/molds/{id} → POST /api/molds/{id}/repairs →
GET /api/maintenances?moldId={id}；API 文档 /swagger-ui/index.html；健康 /actuator/health。
```

---

### Task 12: 工程交付物（docker-compose / CI / 根 README / docs 四目录）

**Files:**
- Create: `docker-compose.yml`、`.gitlab-ci.yml`、`README.md`
- Create: `docs/architecture/dependency-rules.md`、`docs/development/getting-started.md`、`docs/database/flyway-conventions.md`、`docs/standards/error-codes.md`、`docs/standards/coding-standards.md`

**Interfaces:**
- Consumes: Task 2-11 全部事实（模块名、命令、端口）
- Produces: 开发者上手文档 + CI/CD 骨架 + 可选基础设施栈

- [ ] **Step 1: docker-compose.yml**

```yaml
# Phase 1 可选基础设施栈（Demo 不依赖）：docker compose --profile infra up -d
services:
  postgres:
    image: postgres:18-alpine
    profiles: [infra]
    environment:
      POSTGRES_DB: forge
      POSTGRES_USER: forge
      POSTGRES_PASSWORD: forge
    ports: ["5432:5432"]
    volumes: [forge-pgdata:/var/lib/postgresql/data]
  redis:
    image: redis:8-alpine
    profiles: [infra]
    ports: ["6379:6379"]
volumes:
  forge-pgdata: {}
```

- [ ] **Step 2: .gitlab-ci.yml**

```yaml
stages: [verify]

verify:
  stage: verify
  # 若该 tag 在你的 GitLab Runner 上不存在：换 eclipse-temurin:25，Maven 由 wrapper 自带下载
  image: maven:3.9.9-eclipse-temurin-25
  variables:
    MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"
  cache:
    key: forge-maven
    paths: [.m2/repository]
  script:
    - ./mvnw --batch-mode --no-transfer-progress verify
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == "main"
```

- [ ] **Step 3: 根 README.md**

```markdown
# Forge

**A Modular Java Application Foundation for Building SaaS Products.**

模块化单体底座：platform（技术基础）/ contracts（模块契约）/ services（业务共享）/ apps（业务应用）
+ starters / distributions / bom。跨模块协作只走契约 + 领域事件，Spring Modulith 强制模块边界。

## 快速开始

前置：JDK 25（本仓库不依赖本机 Maven，wrapper 自带）。

```bash
# 全量构建 + 测试（含 Modulith 边界验证）
./mvnw verify

# 运行 Demo（mold + maintenance，内存存储零 DB）
./mvnw -pl forge-distributions/forge-demo-app spring-boot:run
# API: http://localhost:8080/swagger-ui/index.html

# 可选基础设施（PostgreSQL 18 + Redis 8，Phase 2 起需要）
docker compose --profile infra up -d
```

## 模块导览

| 目录 | 职责 | 状态 |
|---|---|---|
| forge-bom | 依赖版本唯一入口 | ✅ |
| forge-platform/* | 日志/异常/审计/数据/缓存/安全/存储技术底座 | 按模块 |
| forge-contracts/* | 跨模块接口/DTO/事件/错误码 | ✅ 示例 |
| forge-services | 身份/组织/权限/租户等业务共享能力 | 📦 Phase 2 |
| forge-apps | 具体业务应用 | 📦 Phase 2+ |
| forge-starters | 统一自动装配 | ✅ |
| forge-distributions | 应用组装（先看 forge-demo-app） | ✅ Demo |

文档：`docs/architecture`（依赖规则）· `docs/development`（上手）· `docs/database`（Flyway）· `docs/standards`（错误码/编码规范）

## 技术栈

JDK 25 · Spring Boot 4.1.x · Spring Modulith 2.1.x · PostgreSQL（Phase 2）· Maven（wrapper）· 无 Lombok
```

- [ ] **Step 4: docs 四目录**

`docs/architecture/dependency-rules.md`：

```markdown
# 分层依赖规则

| 调用方 | 允许依赖 | 禁止 |
|---|---|---|
| platform | JDK、第三方基础组件 | 任何业务模块 |
| contracts | platform-core（可选） | services / apps 实现 |
| services | platform、contracts | apps 内部实现 |
| apps | platform、services、contracts | 其他 apps 内部实现 |

执行机制（三层）：
1. Maven reactor 结构性强制（apps 互依在 POM 层不可达）
2. Spring Modulith `ApplicationModules.verify()`（应用内边界，见 forge-demo-app ModulithBoundaryTest）
3. 本文档 + Code Review（ArchUnit 评估于 Phase 4）

跨模块协作方式：
- 同步查询：消费方依赖契约模块（如 forge-contract-mold 的 MoldQueryService），实现方在模块暴露面提供 Bean
- 异步通知：发布方发布契约事件（如 MoldRepairRequested），消费方 @ApplicationModuleListener 监听
- 禁止：直接访问其他模块的内部包/内部表
```

`docs/development/getting-started.md`：

```markdown
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
./mvnw -pl forge-distributions/forge-demo-app spring-boot:run   # 跑 Demo
```

依赖优先直连 Maven Central；网速不佳时可用（不入库）`.mvn/local-settings.xml` 指向阿里云镜像，
构建命令加 `-s .mvn/local-settings.xml`。

## IDE

将仓库作为 Maven 项目导入；Annotation Processing 无需配置（本项目不使用 Lombok）。
Enforcer 要求 IDE 内置 Maven/JDK 同样满足 25。
```

`docs/database/flyway-conventions.md`：

```markdown
# Flyway 规范（Phase 2 实装，本阶段先立规矩）

- 目录：各连库模块 `src/main/resources/db/migration`
- 命名：`V{yyyyMMddHHmm}__{snake_case_description}.sql`（时间戳序避免多人冲突），如
  `V202611200930__create_tenant_table.sql`
- 一次性变更：已合入 main 的脚本禁止修改，只能新增
- 每个业务域的表使用域前缀（如 `mold_`、`identity_`），禁止跨域外键（模块边界）
- 基线：Phase 2 第一个脚本为 baseline，随后按时间戳递增
```

`docs/standards/error-codes.md`：

```markdown
# 错误码登记表

规则：正则 `^[A-Z][A-Z0-9]{1,9}-\d{4}$`；成功码 `OK`；HTTP 状态仅传输提示（默认 200）。
数字段建议：0xxx 参数/通用 · 4xxx 资源不存在/冲突 · 9xxx 系统级。

| 前缀 | 归属 | 定义位置 | 状态 |
|---|---|---|---|
| FORGE- | 平台 | platform-core `ErrorCodes` | 启用 |
| MOLD- | 模具域（Demo） | forge-contract-mold `MoldErrorCodes` | 启用 |
| MAINT- | 维修域（Demo 预留） | 待定义 | 预留 |

新增前缀在此登记后方可使用。平台预置码见 `ErrorCodes` javadoc（FORGE-0400/0401/0403/0404/0405/0415/0423/0429/0500/0501）。
```

`docs/standards/coding-standards.md`：

```markdown
# 编码规范（v0）

- **不使用 Lombok**：值对象用 record；实体用普通类 + 显式 getter/setter；日志 `private static final Logger log = LoggerFactory.getLogger(X.class)`
- 包根：`io.github.luckybearbear.forge`；platform 模块 `…forge.platform.<x>`；契约 `…forge.contract.<domain>`
- 时间：一律 `Instant`（UTC 存储，ISO-8601 序列化，Jackson 3 默认）
- ID：`IdGenerator`（UUIDv7）字符串
- 控制器显式返回 `Result<T>`（不做自动包装）；错误码实现 `ErrorCode` 接口并登记前缀
- Filter 顺序从 `FilterOrder` 取段，业务 Filter ≥ 0
- 模块内分层：`api`（REST 入参/控制器）/ `app`（服务编排）/ `domain`（领域对象+存储）
- 单测 JUnit 5 + AssertJ；集成测试坚持 Testcontainers（不用 H2/jedis-mock）
```

- [ ] **Step 5: 全仓验证 + Commit + Push**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw --batch-mode verify
# 预期 BUILD SUCCESS，全模块测试全绿
git add -A && git commit -m "docs: 交付物（README/依赖规则/上手/Flyway/错误码/编码规范）+ docker-compose + CI 骨架"
git push
```

---

### Task 13: 最终验证与交付报告

**Files:** 无新文件

- [ ] **Step 1: 干净全量构建**

```bash
JAVA_HOME="$FORGE_JDK" ./mvnw --batch-mode clean verify
# 预期 BUILD SUCCESS；记录测试总数
```

- [ ] **Step 2: Demo 可执行产物验证**

```bash
ls forge-distributions/forge-demo-app/target/*.jar   # 预期 repackage 后的可执行 jar
```

- [ ] **Step 3: 验收对照（对照 spec §9 六条 Done 标准）**

逐条确认并记录：verify 全绿 / Demo 可启动+集成测试 / 模块 README / docs 四目录 / BOM 可导入 / CI+compose+gitignore+README 就位。

- [ ] **Step 4: Push 并输出交付报告**

```bash
git push
git log --oneline   # 输出全部提交
```

交付报告内容：提交列表、测试统计、验收六条逐项结论、遗留项（如 Modulith registry 持久化 → Phase 2、结构化日志 → Phase 4）。

---

## Self-Review 记录

- **Spec 覆盖**：§2.1 交付 1↔Task2、2↔Task3/4、3↔Task3、4↔Task4/8、5↔Task8、6↔Task7、7↔Task9-11、8↔Task1/2/12、9↔docs（Task12）；§6.3↔Task5、§6.4↔Task6、§6.9↔Task4 FilterOrder；附录A↔docs/standards/error-codes.md。无缺口。
- **占位符**：无 TBD/TODO；两处"以实测为准"（Jackson3 ISO 默认、@ApplicationModuleListener 包名）均附了确定性的修正动作与代码。
- **类型一致性**：`Result.ok/error`、`BusinessException.of().withHttpStatus()`、`MoldErrorCodes.MOLD_NOT_FOUND`、`MoldQueryService.findById` 在 Task 3/7/9/10 间签名一致；Task 4 补的 `Result.error(code,message,data)` 与 Task 3 工厂共存。
