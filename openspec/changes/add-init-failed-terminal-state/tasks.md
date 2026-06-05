## 1. 领域层：新增 INIT_FAILED 枚举值

- [x] 1.1 在 `TransferStatus` 枚举中新增 `INIT_FAILED` 值，并更新类注释中的状态转移图（`CREATED ↘ INIT_FAILED`）

## 2. 服务层：新增 markInitFailed 方法

- [x] 2.1 在 `TransferOrderStateService` 中新增 `markInitFailed(String transferId, String message)` 方法，调用 `order.markFailure(TransferStatus.INIT_FAILED, "WORKFLOW_START_FAILED", message)`

## 3. Controller 层：捕获 Workflow 启动失败

- [x] 3.1 在 `TransferController.create()` 中用 try-catch 包裹 `WorkflowClient.start()`，失败时调用 `stateService.markInitFailed(transferId, e.getMessage())` 后重新抛出异常

## 4. 测试

- [x] 4.1 在 `TransferControllerTest`（或集成测试）中补充测试：amount=90 时，`transfer_order.status` 为 `INIT_FAILED`，`lastErrorCode` 为 `WORKFLOW_START_FAILED`
