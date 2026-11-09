# forge-platform-core

统一响应/错误码/业务异常/分页/ID 生成签名基座：`Result`、`ErrorCode`/`ErrorCodes`、
`BusinessException`（`withHttpStatus` 可选传输提示）、`PageQuery`/`PageResult`、
`IdGenerator` + `UuidV7Generator`（UUIDv7 默认实现）。

- 零 Spring 依赖（纯 JDK），后续全部平台/业务模块消费
- 状态：✅ Phase 1 完整
