## Why

`freeze` Activity 在遭遇业务性失败（如余额不足）时，直接抛出可重试异常，导致 Temporal Workflow 以指数退避重试最多 10 次（约 20 分钟），期间 `transfer_order.status` 始终停在 `CREATED`，最终 Workflow 进入 `FAILED` 状态后 DB 状态仍无法反映失败原因，形成无法区分"刚创建"与"冻结失败"的僵尸订单。

## What Changes

- **新增** `TransferStatus.FREEZE_FAILED`：冻结业务失败的终态枚举值
- **新增** `TransferOrderStateService.markFreezeFailed()`：持久化冻结失败状态及错误上下文
- **修改** `TransferActivitiesImpl.freeze()`：业务失败时先写 `FREEZE_FAILED` 状态，再抛 `ApplicationFailure.newNonRetryableFailure()`，Workflow 立即终止，不重试
- **更新** `TransferStatus` 枚举注释：补充状态转移图中的 `FREEZE_FAILED` 分支
- **补充** `TransferActivitiesImplTest`：覆盖 freeze 业务失败场景
- **补充** `TransferWorkflowImplTest`：覆盖 freeze 失败后 Workflow 立即终止的场景
- **更新** `openspec/changes/temporal-saga-refactor/design.md`：回答遗留 Open Question，补充 D7 决策

## Capabilities

### New Capabilities

- `freeze-failure-handling`：冻结 Activity 对业务性失败的终态处理能力——区分网络/瞬时错误（可重试）与业务确定性失败（不可重试），并将失败状态持久化到 `transfer_order`

### Modified Capabilities

（无现有 spec 文件，不涉及已有规格变更）

## Impact

**代码**
- `common`：`TransferStatus` 枚举新增 `FREEZE_FAILED` 值
- `transfer-service`：`TransferOrderStateService`、`TransferActivitiesImpl` 各新增/修改约 5 行；测试类补充两个测试方法

**API**
- `POST /transfers` 行为不变；余额不足时 `GET /transfers/{id}` 返回的 `status` 由 `CREATED`（错误）变为 `FREEZE_FAILED`（正确）

**数据库**
- `transfer_order.status` 列为 `VARCHAR(32)`，无需 DDL 变更

**依赖**
- 引入 `io.temporal:temporal-sdk` 已有的 `ApplicationFailure` API，无新依赖
