## Why

`TransferController.create()` 在 `stateService.createOrder()` 成功提交后、`WorkflowClient.start()` 执行前若发生异常，DB 中会残留一条 `status=CREATED` 的孤儿订单，该订单永远无法推进到任何终态，且与正常的 `CREATED`（Workflow 已启动、冻结进行中）在数据库层面完全无法区分，形成不可识别的僵尸记录。

## What Changes

- **新增** `TransferStatus.INIT_FAILED`：Workflow 启动失败的终态枚举值
- **新增** `TransferOrderStateService.markInitFailed()`：持久化 Workflow 启动失败状态及错误上下文
- **修改** `TransferController.create()`：用 try-catch 包裹 `WorkflowClient.start()`，失败时调用 `markInitFailed()` 后重新抛出异常
- **更新** `TransferStatus` 枚举注释：补充状态转移图中的 `INIT_FAILED` 分支

## Capabilities

### New Capabilities

- `init-failed-handling`：Workflow 启动阶段失败的终态处理能力——当 `WorkflowClient.start()` 抛出异常时，将已落库的订单标记为 `INIT_FAILED`，使孤儿订单可识别、可运营排查

### Modified Capabilities

（无现有 spec 文件涉及此阶段，不需要变更已有规格）

## Impact

**代码**
- `common`：`TransferStatus` 枚举新增 `INIT_FAILED` 值，更新状态转移图注释
- `transfer-service`：`TransferOrderStateService` 新增 `markInitFailed()` 约 5 行；`TransferController.create()` 增加 try-catch 约 6 行

**API**
- `POST /transfers` 正常路径行为不变；Workflow 启动失败时 `GET /transfers/{id}` 返回的 `status` 由之前无终态（永远 `CREATED`）变为 `INIT_FAILED`

**数据库**
- `transfer_order.status` 列为 `VARCHAR(32)`，无需 DDL 变更

**依赖**
- 无新依赖
