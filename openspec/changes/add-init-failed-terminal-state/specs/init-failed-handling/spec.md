## ADDED Requirements

### Requirement: Workflow 启动失败时订单进入 INIT_FAILED 终态

当 `WorkflowClient.start()` 在转账订单已落库后抛出异常时，系统 SHALL 将该订单状态更新为 `INIT_FAILED`，并记录失败错误信息，使订单不再停留在 `CREATED` 状态。

#### Scenario: WorkflowClient.start() 抛出异常
- **WHEN** `stateService.createOrder()` 成功提交，且 `WorkflowClient.start()` 随后抛出任意异常
- **THEN** 系统 SHALL 调用 `markInitFailed(transferId, errorMessage)` 将订单状态置为 `INIT_FAILED`
- **THEN** `GET /transfers/{transferId}` 返回的 `status` 字段值为 `INIT_FAILED`
- **THEN** `POST /transfers` 接口仍返回失败响应（不改变对调用方的错误语义）

### Requirement: INIT_FAILED 是不可推进的终态

`INIT_FAILED` 状态 SHALL 是一个终态，系统不得在该状态上自动触发任何补偿或重试动作。

#### Scenario: 查询 INIT_FAILED 订单
- **WHEN** 订单 `status` 为 `INIT_FAILED`
- **THEN** `GET /transfers/{transferId}` 正常返回该订单，`status` 字段为 `INIT_FAILED`
- **THEN** 系统不自动对该订单发起任何 Workflow 重启或状态流转

### Requirement: INIT_FAILED 与正常 CREATED 可区分

订单进入 `INIT_FAILED` 后，系统 SHALL 保证 DB 中不存在 `status=CREATED` 且无对应 Temporal Workflow 的孤儿订单（因 Workflow 启动失败导致）。

#### Scenario: 模拟 Workflow 启动失败场景（amount=90）
- **WHEN** 请求金额为 90，触发 `TransferController` 中的模拟异常路径
- **THEN** `transfer_order` 表中该笔订单 `status` 为 `INIT_FAILED`，而非 `CREATED`
- **THEN** Temporal 中不存在该 `transferId` 对应的 Workflow 实例
