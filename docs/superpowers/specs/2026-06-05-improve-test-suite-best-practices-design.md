---
comet_change: improve-test-suite-best-practices
role: technical-design
canonical_spec: openspec
---

# 测试体系最佳实践升级设计

## 目标

把现有测试从“覆盖了部分关键路径”升级为“分层清晰、可执行、可诊断、可长期演进”的测试体系。核心目标是让跨账户划转的资金不变量、状态机、HTTP 契约、Temporal Workflow 编排、幂等和真实数据库边界都有对应证据，同时保持快速反馈轨道稳定。

本变更优先修改测试代码、测试夹具和测试策略文档。除非发现生产代码存在不可测边界，否则不修改业务逻辑、REST API、数据库 schema、Maven 依赖或 CI 策略。

## 推荐方案

采用“分层补强 + 少量高价值保真场景 + 测试夹具复用”的方案。

1. 快速轨继续由 `*Test` 承担，覆盖 Controller 契约、Workflow 分支、Activity 状态更新、服务层边界和错误处理。
2. 保真轨继续由 `*IT` 承担，只覆盖真实 MySQL、Feign/WireMock、Temporal Workflow + Activity + DB 组合路径和并发幂等这些 H2 或 mock 难以证明的边界。
3. transfer-service 的 Controller 测试改为标准 `@WebMvcTest(TransferController.class)` + MockMvc，验证真实 HTTP method/path、JSON 绑定、`@Valid` 校验、统一 `ApiResponse` envelope 和 Workflow/StateService 交互。
4. `INIT_FAILED` 持久化不塞进 Web slice。用现有 JPA/服务切片或一个专门的 Controller 协作测试验证“订单已创建但 Workflow 启动失败时持久化 `INIT_FAILED`”。
5. 重写而不是解注释 `TransferScenarioIntegrationTest`，按当前 Temporal Workflow、Activity、状态服务和 repository 结构组织场景。
6. 在 test source 下抽取小型 helper：请求数据构造、`ApiResponse` JSON、账户客户端 stub、Temporal test harness、唯一业务键生成。helper 只服务测试，不跨入生产源码。

## 备选方案

不选择 E2E 优先方案。把大部分覆盖都推到 `@SpringBootTest` 或全链路 Docker 测试会提升单次信心，但反馈慢、失败定位差，也容易让 CI 变脆。

不选择最小补丁方案。只把注释掉的 `TransferScenarioIntegrationTest` 解开，无法解决旧命名、旧入口、H2/Temporal 线程边界和重复夹具问题，也无法补齐 Web 契约。

不引入 Pact、ArchUnit、REST Assured 或新的测试平台。当前 Maven、Spring Test、WireMock、Testcontainers、Temporal test SDK 足够支撑本阶段目标。

## 测试分层

### Web 契约层

`transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerTest.java` 应重构为 `@WebMvcTest`。它只关心 HTTP 行为：

- `POST /transfers` 成功返回 `success=true`、`code=OK`，并验证 `TransferOrderStateService.createOrder` 与 `WorkflowClient` 被调用。
- `POST /transfers` 入参非法返回 400，且不触碰状态服务和 Workflow。
- `POST /transfers/{transferId}/review` 发送 Signal，并返回 `stateService.loadOrder` 的订单。
- `GET /transfers/{transferId}` 返回统一 envelope。
- Workflow 启动异常返回 `TRANSFER_OPERATION_FAILED`，并验证 Controller 请求层行为；持久化状态由下层协作测试断言。

account-service 已有 `@WebMvcTest(AccountAssetController.class)` 基础，应补齐四类接口的失败 envelope 和校验覆盖，避免每个端点只覆盖成功路径。

### Temporal 场景层

`TransferWorkflowImplTest` 保留为纯 Workflow 分支测试：自动转账、人工审核通过、人工审核拒绝、Activity retry、非重试冻结失败。

`TransferActivitiesImplTest` 保留为 Activity 状态转换测试：冻结失败进入 `FREEZE_FAILED`、扣减成功进入 `DEBIT_SUCCESS`、入账失败进入 `CREDIT_FAILED`、拒绝取消冻结进入 `REJECTED`。

新的 `TransferScenarioIntegrationTest` 负责组合证明：

- 人工审核通过：`CREATED -> WAIT_REVIEW -> DEBIT_SUCCESS -> SUCCESS`。
- 人工审核拒绝：冻结后取消冻结，终态 `REJECTED`。
- 站内自动完成：不等待 review，终态 `SUCCESS`。
- 冻结业务失败：终态 `FREEZE_FAILED`，且不调用扣减、入账、解冻补偿。
- 入账失败：终态 `CREDIT_FAILED`，已扣减后不反向补偿，只允许 Temporal 对 target credit 重试。

场景测试应使用真实 `TransferActivitiesImpl`、真实 `TransferOrderStateService`、真实 repository。账户侧可先用 Mockito client stub；若需要证明 Feign HTTP 边界，再放入 WireMock `*IT`。

### 账户资金不变量层

account-service 快速轨覆盖领域规则和异常：

- 金额必须大于 0，非法金额不产生流水和余额变化。
- 冻结余额不足失败。
- 未冻结不能扣减。
- 重复同一幂等键返回成功但 `applied=false`。
- 解冻和入账只影响对应字段。

保真轨覆盖 MySQL 边界：

- `DECIMAL(32,8)` scale 保持。
- 同一幂等键重复调用只落一条流水。
- 并发相同幂等键竞争时只有一次真实资产变化。
- 并发测试使用唯一 `transferId`、固定线程池、`CountDownLatch` 起跑、超时和最终数据库状态断言，不依赖线程执行顺序。

## 测试夹具设计

建议新增以下测试源码 helper，命名可按实现时的局部上下文微调，但职责边界不变：

- `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferTestData.java`：创建 `TransferOrder`、`CreateTransferRequest`、`ReviewTransferRequest`、唯一 transferId/userId。
- `transfer-service/src/test/java/com/demo/transfer/transfer/support/ApiResponseJson.java`：生成账户服务 WireMock 需要的成功/失败 JSON。
- `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferWorkflowTestHarness.java`：封装 `TestWorkflowEnvironment`、Worker 注册、Workflow stub 创建和关闭。
- `account-service/src/test/java/com/demo/transfer/account/support/AccountTestData.java`：创建账户余额、资产操作请求和幂等键。

helper 必须保持小而直接。不要创建通用测试框架，也不要把业务断言隐藏进过度抽象的 DSL。

## 风险与控制

- 保真测试变慢：只把 Docker、真实 DB、并发和 Workflow 组合场景放入 `*IT`。
- Temporal 环境泄漏：统一通过 harness close，并在 `@AfterEach` 兜底关闭。
- 并发测试偶发：使用超时、唯一键、最终状态断言和真实 MySQL 唯一约束，不断言线程先后。
- Web slice 过度 mock：Controller 测试只验证 HTTP 契约；状态持久化用 JPA/服务协作测试补证。
- 夹具膨胀：每个 helper 只负责一类数据或一类外部 stub，不跨模块共享生产代码。

## 验证策略

必须保留双轨命令边界：

```bash
mvn -q test -DfailIfNoTests=false
mvn -q verify -DfailIfNoTests=false
openspec validate --all --strict
```

`mvn test` 应在无 Docker 环境可跑，覆盖快速轨。`mvn verify` 需要 Docker，覆盖 `*IT`、Testcontainers MySQL 和 JaCoCo 门禁。若本机 Docker 不可用，只记录环境限制，不把全量验证报告为已通过。
