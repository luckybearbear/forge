# 错误码登记表

规则：正则 `^[A-Z][A-Z0-9]{1,9}-\d{4}$`；成功码 `OK`；HTTP 状态仅传输提示（默认 200）。
数字段建议：0xxx 参数/通用 · 4xxx 资源不存在/冲突 · 9xxx 系统级。

| 前缀 | 归属 | 定义位置 | 状态 |
|---|---|---|---|
| FORGE- | 平台 | platform-core `ErrorCodes` | 启用 |
| MOLD- | 模具域（Demo） | forge-contract-mold `MoldErrorCodes` | 启用 |
| MAINT- | 维修域（Demo 预留） | 待定义 | 预留 |

新增前缀在此登记后方可使用。平台预置码见 `ErrorCodes` javadoc（FORGE-0400/0401/0403/0404/0405/0415/0423/0429/0500/0501）。
