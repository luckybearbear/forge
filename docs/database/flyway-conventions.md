# Flyway 规范（Phase 2 实装，本阶段先立规矩）

- 目录：各连库模块 `src/main/resources/db/migration`
- 命名：`V{yyyyMMddHHmm}__{snake_case_description}.sql`（时间戳序避免多人冲突），如
  `V202611200930__create_tenant_table.sql`
- 一次性变更：已合入 main 的脚本禁止修改，只能新增
- 每个业务域的表使用域前缀（如 `mold_`、`identity_`），禁止跨域外键（模块边界）
- 基线：Phase 2 第一个脚本为 baseline，随后按时间戳递增
