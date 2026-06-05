## Why

项目已经具备双轨测试、JaCoCo、Testcontainers、WireMock 和 Temporal 测试基础，但测试代码仍有明显行业实践差距：端到端场景测试被整体注释，transfer-service Controller 测试未采用标准 Web slice，核心资金不变量还缺少跨 Workflow/Activity/HTTP/DB 的保真覆盖。继续扩展业务前，应先把测试体系升级为可长期演进的质量防线。

## What Changes

- 规划并实施一套更完整的测试代码升级方案，覆盖快速轨、保真轨、契约测试、Temporal Workflow 场景测试、幂等并发和测试夹具复用。
- 恢复或重写 `TransferScenarioIntegrationTest`，用当前 Temporal Workflow / Activity 实现表达端到端业务场景，而不是恢复旧手写 Saga 测试入口。
- 将 transfer-service 对外接口测试拆回行业常用的 `@WebMvcTest` Web slice，补齐请求校验、错误响应、review Signal、get 查询和 Workflow 启动失败契约。
- 补强 account-service 的资金不变量测试，包括高精度金额、幂等键、重复/并发调用、冻结/扣减/解冻边界。
- 抽取测试夹具，减少 JSON、账户响应、Workflow 环境、WireMock stub 和数据库清理的重复代码。
- 保持生产代码行为、REST API、数据库 schema 和 Maven 依赖不变；如实现中发现不可测边界，只允许提出最小可测性重构。

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `automated-testing`: 扩展测试套件要求，使其达到可维护、分层、可执行、可诊断的行业最佳实践水平。

## Impact

- 影响测试代码：`account-service/src/test/**`、`transfer-service/src/test/**`。
- 可能影响测试支撑类：测试 fixtures、stub builders、Temporal test harness、WireMock helpers。
- 影响文档/规格：`openspec/specs/automated-testing/spec.md`、`docs/design/testing-strategy.md`、必要时 README 的验证说明。
- 不影响生产代码、接口行为、数据库 schema、依赖版本或 CI 执行策略。
