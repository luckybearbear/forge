# 错误码登记表

规则：正则 `^[A-Z][A-Z0-9]{1,9}-\d{4}$`；成功码 `OK`；HTTP 状态仅传输提示（默认 200）。
数字段建议：0xxx 参数/通用 · 4xxx 资源不存在/冲突 · 9xxx 系统级。

| 前缀 | 归属 | 定义位置 | 状态 |
|---|---|---|---|
| FORGE- | 平台 | platform-core `ErrorCodes` | 启用 |
| MOLD- | 模具域（Demo） | forge-contract-mold `MoldErrorCodes` | 启用 |
| MAINT- | 维修域（Demo） | demo maintenance 模块 `MaintenanceErrorCodes` | 启用（Demo） |

新增前缀在此登记后方可使用。平台预置码见 `ErrorCodes` javadoc（FORGE-0400/0401/0403/0404/0405/0415/0423/0429/0500/0501）。

预置码启用状态（Phase 2a 起）：

- **FORGE-0401（未认证）/ FORGE-0403（无权限）：已启用**——forge-security-starter 的默认
  SecurityFilterChain 对未认证/无权限请求分别输出 401/403 + 统一 `Result`（原 Phase 1 预留码转正）。
- **FORGE-0423（请求冲突）：已启用**——platform-web 全局异常处理将乐观锁冲突
  （ObjectOptimisticLockingFailureException）映射为 409 + FORGE-0423。
- 其余预置码（0400/0404/0405/0415/0500）已由 platform-web 全局异常处理器按异常类型输出；
  FORGE-0429（限流）、FORGE-0501（功能未实现）仍为预留，随对应能力启用。
