## Why

当前 `TransferSagaService` 将 Feign 远程调用嵌套在本地 `@Transactional` 边界内，导致账户资产被冻结后若 DB 提交失败则产生无对应转账单的孤立冻结（F1），同时 DB 连接在整个网络 IO 期间持续占用（F3）；手写的 11 状态机与定时重试调度器增加了维护负担；`AccountAssetService` 的幂等检查在行锁之前执行，在并发场景下依赖 DB 约束兜底并向上游产生误报失败（F2）。引入 Temporal 工作流引擎可从根本上消除上述问题，同时将编排逻辑从 240 行降至约 50 行。

## What Changes

- **新增** Temporal Worker 集成到 `transfer-service`（Spring Boot 启动时注册 Workflow 和 Activity）
- **新增** `TransferWorkflow` 接口与 `TransferWorkflowImpl` 实现，取代 `TransferSagaService` 中的状态机编排逻辑
- **新增** `TransferActivities` 接口与实现，封装四类账户操作的 Feign 调用和 DB 状态更新
- **修改** `TransferController`：`POST /transfers` 改为启动 Temporal Workflow；`POST /{id}/review` 改为向 Workflow 发送 Signal
- **修改** `AccountAssetService.apply`：将幂等检查移到 `FOR UPDATE` 行锁之后（F2 修复，2 行调序）
- **修改** `docker-compose.yml`：新增 Temporal Server 和 Temporal UI 服务
- **修改** `pom.xml`：新增 `io.temporal:temporal-sdk` 和 `io.temporal:temporal-spring-boot-starter` 依赖
- **删除** `TransferSagaService`（编排逻辑迁移至 Workflow）
- **删除** `TransferRetryService` 和 `TransferRetryScheduler`（由 Temporal 内置 RetryPolicy 替代）
- **简化** `TransferStatus`：移除 5 个中间失败状态（`FREEZE_FAILED`、`DEBIT_FAILED`、`CREDIT_FAILED`、`CANCEL_FAILED`、`WITHDRAW_FAILED`、`WITHDRAW_PENDING`）
- **保留** `transfer_order` 表作为查询投影，由 Activities 作为副作用更新状态

## Capabilities

### New Capabilities

- `temporal-workflow-orchestration`：基于 Temporal 的 Saga 工作流编排能力，包括 Workflow 定义、Activity 实现、Worker 注册、Signal 驱动的人工审核等待
- `idempotency-lock-ordering`：修正账户资产操作的幂等检查顺序，确保检查在行锁保护范围内执行

### Modified Capabilities

（无现有 spec 文件，不涉及已有规格变更）

## Impact

**代码**
- `transfer-service`：主要改动区，新增 Workflow/Activity 实现，删除 SagaService/RetryService/Scheduler
- `account-service`：仅 `AccountAssetService.apply` 方法内 2 行调序，无接口变更

**API**
- `POST /transfers` 响应结构不变（仍返回 transferId），内部改为异步启动 Workflow
- `POST /{id}/review` 改为 Temporal Signal，行为语义不变
- `POST /{id}/withdraw-result` 旧兼容接口随 `WITHDRAW_PENDING` 状态一并移除（**BREAKING**，旧流程不再支持）
- `POST /{id}/retry` 手动重试接口随 RetryService 一并移除（**BREAKING**，重试由 Temporal 自动处理）

**新增依赖**
- `io.temporal:temporal-sdk:1.25+`
- `io.temporal:temporal-spring-boot-starter:1.25+`
- Temporal Server（Docker，需要 MySQL 后端）

**测试**
- 现有 `TransferSagaServiceTest`、`TransferRetryServiceTest` 需重写为 `TestWorkflowEnvironment` 形式
- `TransferScenarioIntegrationTest` 迁移为 Temporal 测试环境下的场景验证
