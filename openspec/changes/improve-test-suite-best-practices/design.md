## Context

当前项目测试体系已经有不错的基础：Maven surefire/failsafe 双轨、JaCoCo 合并门禁、Testcontainers MySQL、WireMock、Temporal `TestWorkflowEnvironment`、账户服务幂等和金额精度测试。但测试代码仍处于“可证明部分关键点”的阶段，距离行业最佳实践还差几个关键能力：

- `TransferScenarioIntegrationTest` 整类被注释，导致跨 Workflow + Activity + DB 的业务场景缺口。
- `TransferControllerTest` 当前是 `@DataJpaTest` + 直接调用 Controller，不是标准 `@WebMvcTest`，HTTP 契约、校验和统一响应结构覆盖不足。
- `TransferWorkflowImplTest` 与 `TransferActivitiesImplTest` 分别覆盖流程和状态，但尚未组合证明资金核心不变量在保真路径中成立。
- 测试夹具分散，JSON 响应、资产操作响应、Workflow 启动、WireMock stub、数据库清理容易重复。

## Goals / Non-Goals

**Goals:**

- 建立分层清晰、可执行、可诊断的测试套件规划。
- 让核心资金不变量可由快速轨和保真轨共同证明。
- 使新增测试与现有 Maven 双轨、JaCoCo 门禁、JDK 8 约束一致。
- 优先通过测试代码和测试夹具完成，不把业务逻辑改成“为测试而测试”的结构。

**Non-Goals:**

- 不引入新测试框架或契约测试平台。
- 不修改 REST API、数据库 schema、Temporal RetryPolicy 或生产业务流程。
- 不把所有测试都升级为慢速 `@SpringBootTest`。
- 不追求 100% 覆盖率；重点是资金不变量、状态机、边界和失败路径。

## Decisions

### 1. 继续采用测试金字塔，而不是端到端优先

推荐方案是“快速轨兜住决策逻辑和契约，保真轨兜住真实边界”。`*Test` 保持无 Docker，可覆盖 Controller 契约、状态服务、Workflow 分支、Activity 状态更新；`*IT` 只覆盖 H2 测不准或跨 HTTP/DB/Workflow 的场景。

替代方案是大量新增 `@SpringBootTest` 端到端测试。它信心高，但慢、脆、定位差，容易让 CI 时间和维护成本失控。

### 2. Web 层回归 `@WebMvcTest`

transfer-service 的 Controller 测试应从直接调用 Controller 切回 `@WebMvcTest(TransferController.class)` + MockMvc。这样才能验证真实 HTTP method/path、JSON 绑定、`@Valid` 校验、状态码、`ApiResponse` 结构和 service/workflow 交互边界。

直接调用 Controller 可以保留少量纯逻辑测试，但不应替代 Web contract。

### 3. 重写而非解注释旧 `TransferScenarioIntegrationTest`

旧文件虽保留了场景意图，但已混合旧命名、H2、Temporal Worker、Mockito 客户端等多种临时实现。恢复时应按当前实现重写为明确的保真场景测试：真实 `TransferActivitiesImpl`、真实 repository、Temporal `TestWorkflowEnvironment`、账户服务用 WireMock 或测试替身，数据清理用唯一 transferId 和显式清理。

### 4. 抽取测试夹具但不引入新依赖

在各模块测试源码下创建小型 helper：

- `ApiResponseJson` / `AccountApiStubs`：生成标准成功/失败响应和 WireMock stub。
- `TransferWorkflowTestHarness`：封装 `TestWorkflowEnvironment`、Worker 注册和关闭。
- `TransferTestData` / `AccountTestData`：生成唯一用户、transferId、金额和请求 DTO。

这些 helper 只放在 test source，不跨生产模块，不引入新框架。

### 5. 并发与幂等只在保真轨验证数据库不变量

真正的并发和唯一索引行为必须在 MySQL 上验证。快速轨可以覆盖服务层幂等分支，保真轨必须覆盖真实唯一约束、重复调用无副作用，以及至少一个并发同幂等键的竞争场景。

## Risks / Trade-offs

- 保真轨变慢 → 只把真实 DB、HTTP、并发和 Workflow 场景放进 `*IT`，其余留在快速轨。
- Temporal 测试易泄漏线程或环境 → 使用 harness 统一创建/关闭 `TestWorkflowEnvironment`。
- WireMock stub 重复导致用例脆弱 → 抽取 helper，所有响应使用统一 `ApiResponse` JSON。
- 并发测试可能偶发 → 使用固定线程池、超时、唯一业务键和最终状态断言，避免依赖执行顺序。
- 恢复场景测试可能暴露当前实现缺陷 → 按 TDD 处理；若是生产 bug，单独记录或拆分 hotfix，不在测试规划里静默绕过。
