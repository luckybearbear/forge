# forge-contracts

契约层聚合 POM：跨模块协作只走契约——接口（查询服务）/DTO/领域事件/错误码常量，
实现细节禁止越界（Spring Modulith verify() 强制）。

- 业务域错误码常量随契约模块发布（登记规则见 `docs/standards/error-codes.md`）
- 依赖规则：可依赖 platform-core（可选），禁止依赖 services/apps 实现
- 状态：✅ 聚合（含 forge-contract-mold 示例 + tenant/identity/organization/authorization 四域契约）
