# forge-contract-mold

示例契约（模具域）：`MoldDTO`/`MoldStatus`、领域事件 `MoldRepairRequested`、
跨模块查询接口 `MoldQueryService`、错误码 `MoldErrorCodes`（MOLD-0404 等）。

- 跨模块协作打样：业务模块间同步查询走查询接口、异步走事件，新契约模块照此结构
- 状态：✅ Phase 1 示例（Demo 的 mold↔maintenance 协作即基于本契约）
