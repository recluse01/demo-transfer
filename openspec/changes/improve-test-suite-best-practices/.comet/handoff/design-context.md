# Comet Design Handoff

- Change: improve-test-suite-best-practices
- Phase: design
- Mode: compact
- Context hash: cc7f3c834400678727a918cb174fe2f32450b43600217645d41160876b8249ef

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/improve-test-suite-best-practices/proposal.md

- Source: openspec/changes/improve-test-suite-best-practices/proposal.md
- Lines: 1-29
- SHA256: f4a4f215ede15f28ae4c6684f5538a76e9477584ca9c65d225fb9b5c3b1bca16

```md
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
```

## openspec/changes/improve-test-suite-best-practices/design.md

- Source: openspec/changes/improve-test-suite-best-practices/design.md
- Lines: 1-64
- SHA256: 9573035068daf02352b420ace7c39109ee3346d93e47f8575e2a5d3d0be6c56a

```md
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
```

## openspec/changes/improve-test-suite-best-practices/tasks.md

- Source: openspec/changes/improve-test-suite-best-practices/tasks.md
- Lines: 1-30
- SHA256: a648c1eba1e554f6b48b2899c640f05e77a2fc6481eeeffc98e604cb01bd1e18

```md
## 1. Web 与契约测试

- [ ] 1.1 将 transfer-service Controller 测试规划为 `@WebMvcTest` + MockMvc，覆盖 create/review/get 的成功、校验失败和统一响应结构。
- [ ] 1.2 保留或迁移 Workflow 启动失败测试，确保 `INIT_FAILED` 持久化与 HTTP 错误响应同时被断言。
- [ ] 1.3 补齐 account-service 内部资产接口的 request validation、业务失败和成功 envelope 覆盖。

## 2. Temporal 场景与状态机测试

- [ ] 2.1 重写并启用 `TransferScenarioIntegrationTest`，基于当前 Temporal Workflow / Activity 实现覆盖人工审核通过、拒绝、自动完成。
- [ ] 2.2 覆盖 `FREEZE_FAILED`、`CREDIT_FAILED`、`INIT_FAILED` 等终态/失败态，不恢复旧 `retryOne` 或 scheduler 入口。
- [ ] 2.3 增加“源账户已扣减后目标入账失败不反向补偿”的可观察断言，证明只重试 target credit。

## 3. 账户资金不变量测试

- [ ] 3.1 扩展 account-service 快速轨测试，覆盖冻结/扣减/解冻/入账的边界金额、非法金额和状态前置条件。
- [ ] 3.2 扩展真实 MySQL 保真轨幂等测试，覆盖重复请求、唯一索引和并发同幂等键竞争。
- [ ] 3.3 保持 BigDecimal / DECIMAL(32,8) 精度测试，并补齐 scale、rounding 和零/负数拒绝边界。

## 4. 测试夹具与可维护性

- [ ] 4.1 新增 test-source helper，统一构造 `ApiResponse` JSON、WireMock stub、transfer/account 请求和唯一业务键。
- [ ] 4.2 新增或整理 Temporal test harness，确保 `TestWorkflowEnvironment` 创建、Worker 注册、关闭和异常诊断一致。
- [ ] 4.3 消除重复手写 JSON、重复 stub 和脆弱的全局状态依赖，保证测试彼此隔离。

## 5. 验证与文档同步

- [ ] 5.1 更新 `docs/design/testing-strategy.md`，让测试策略与新测试代码布局一致。
- [ ] 5.2 运行 `mvn -q test -DfailIfNoTests=false` 验证快速轨无 Docker 可运行。
- [ ] 5.3 在 Docker 可用环境运行 `mvn -q verify -DfailIfNoTests=false`，验证保真轨和 JaCoCo 门禁。
- [ ] 5.4 运行 `openspec validate --all --strict`，并记录不能执行的环境限制。
```

## openspec/changes/improve-test-suite-best-practices/specs/automated-testing/spec.md

- Source: openspec/changes/improve-test-suite-best-practices/specs/automated-testing/spec.md
- Lines: 1-64
- SHA256: 51ef93f205eda0d557a0fc9c9c3c46855d8bdf0fbb4b113a9c1e85e435a57971

```md
## ADDED Requirements

### Requirement: Transfer Web Contract Tests
transfer-service SHALL test its public transfer HTTP API through `@WebMvcTest` + MockMvc, not only by directly invoking controller methods.

#### Scenario: Create transfer request validation is enforced at HTTP boundary
- **WHEN** a client submits an invalid `POST /transfers` JSON body
- **THEN** the test asserts HTTP response status, `ApiResponse` error shape, and that no Workflow is started

#### Scenario: Create transfer workflow startup failure is exposed consistently
- **WHEN** order persistence succeeds but Temporal Workflow startup fails
- **THEN** the test asserts `INIT_FAILED` is persisted and the HTTP response uses `TRANSFER_OPERATION_FAILED`

#### Scenario: Review and get endpoints are covered as HTTP contracts
- **WHEN** tests exercise `POST /transfers/{transferId}/review` and `GET /transfers/{transferId}`
- **THEN** they assert path binding, JSON shape, service/workflow interactions, and success/failure envelope behavior

### Requirement: Temporal Scenario Integration Tests
transfer-service SHALL have enabled scenario tests for current Temporal Workflow behavior, covering manual review, auto mode, terminal failures, and the no-reverse-compensation invariant.

#### Scenario: Manual review approval and rejection are executable
- **WHEN** a manual-review transfer is approved or rejected in the Temporal test environment
- **THEN** enabled tests assert the final persisted status is respectively `SUCCESS` or `REJECTED`

#### Scenario: Auto mode completes without review signal
- **WHEN** an `AUTO_WITHDRAW` transfer runs in the Temporal test environment
- **THEN** enabled tests assert Workflow executes freeze, confirmDebit, and credit without waiting for review

#### Scenario: Credit failure does not compensate source account
- **WHEN** source debit has succeeded and target credit fails before later retry convergence
- **THEN** enabled tests assert `CREDIT_FAILED` is persisted, source `cancelFreeze` is not called, and later retry only targets credit

### Requirement: Account Idempotency and Concurrency Tests
account-service SHALL test idempotency and database uniqueness under realistic duplicate and concurrent calls.

#### Scenario: Duplicate operations have no second side effect
- **WHEN** the same `transferId + operationType` request is submitted twice
- **THEN** tests assert `applied=false` on the duplicate response and balance/ledger changes happen once

#### Scenario: Concurrent duplicate operations converge to one applied write
- **WHEN** multiple threads submit the same idempotency key against real MySQL
- **THEN** exactly one operation is applied, all other responses are duplicate/no-op or safely rejected, and final balances remain correct

### Requirement: Reusable Test Fixtures
The test suite SHALL use focused test-source helpers for repeated response JSON, WireMock stubs, transfer data, account data, and Temporal test harness setup.

#### Scenario: WireMock response helpers produce canonical ApiResponse JSON
- **WHEN** Feign/WireMock integration tests need success or failure responses
- **THEN** they use shared test helpers rather than duplicating handwritten JSON strings

#### Scenario: Temporal test harness closes resources consistently
- **WHEN** Workflow or scenario tests create `TestWorkflowEnvironment`
- **THEN** they use a helper or explicit lifecycle pattern that always closes the environment after each test

### Requirement: Verification Commands Remain Split by Track
The improved tests SHALL preserve the existing Maven split: fast feedback through `mvn test`, full confidence through `mvn verify`.

#### Scenario: Fast track remains Docker-free
- **WHEN** `mvn test` runs without Docker
- **THEN** all `*Test` tests pass without requiring Testcontainers

#### Scenario: Full track proves real boundary behavior
- **WHEN** `mvn verify` runs with Docker available
- **THEN** all `*Test` and `*IT` tests pass, JaCoCo merged gates run, and Testcontainers-backed tests cover real MySQL behavior
```

