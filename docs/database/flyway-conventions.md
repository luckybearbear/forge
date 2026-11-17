# Flyway 规范（Phase 2a 起实装：demo 首个脚本已合入）

- 目录：各连库模块 `src/main/resources/db/migration`
- 命名：`V{yyyyMMddHHmm}__{snake_case_description}.sql`（时间戳序避免多人冲突），如
  `V202611200930__create_tenant_table.sql`
- 一次性变更：已合入 main 的脚本禁止修改，只能新增
- 每个业务域的表使用域前缀（如 `mold_`、`identity_`），禁止跨域外键（模块边界）
- 基线：Phase 2 第一个脚本为 baseline，随后按时间戳递增

## 多模块 classpath 合并执行

每个连库模块（如 forge-demo-app、后续各服务模块）自带自己的 `db/migration` 目录；应用打包后
所有模块的脚本落在**同一 classpath 的 `db/migration` 下**，Flyway 默认扫描会**合并发现全部脚本**、
按版本号（即时间戳）**全局定序**后在一个迁移历史（`flyway_schema_history` 表）里依次执行。

因此：不存在"每个模块独立执行/独立历史"；时间戳命名天然承担跨模块全局定序，提交前确认
时间戳晚于 main 上已有最新脚本即可（同秒冲突时后合入者改时间戳重命名——脚本尚未合入 main 前
允许，合入后禁止改）。
