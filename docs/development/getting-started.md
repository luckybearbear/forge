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
