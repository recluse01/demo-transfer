## ADDED Requirements

### Requirement: 冻结业务失败立即终止 Workflow

当账户服务对冻结请求返回业务失败（`success=false`）时，系统 SHALL 立即将 `transfer_order.status` 设为 `FREEZE_FAILED`，并以不可重试方式终止 Temporal Workflow，不得触发 Activity 重试。

#### Scenario: 余额不足时冻结失败
- **WHEN** 用户提交转账请求，账户可用余额小于转账金额
- **THEN** `transfer_order.status` 变为 `FREEZE_FAILED`
- **THEN** `transfer_order.last_error_code` 记录账户服务返回的错误码
- **THEN** `transfer_order.last_error_message` 记录账户服务返回的错误信息
- **THEN** Temporal Workflow 立即进入 FAILED 状态，不重试 freeze Activity

#### Scenario: 账户不存在时冻结失败
- **WHEN** 用户提交转账请求，源账户在账户服务中不存在
- **THEN** `transfer_order.status` 变为 `FREEZE_FAILED`
- **THEN** Temporal Workflow 立即进入 FAILED 状态，不重试 freeze Activity

### Requirement: 瞬时失败保留重试行为

当冻结请求因网络超时、账户服务不可用等瞬时原因失败时，系统 SHALL 保持原有 Temporal RetryPolicy 重试行为，不将 `transfer_order.status` 更新为 `FREEZE_FAILED`。

#### Scenario: 网络超时时自动重试
- **WHEN** 账户服务调用因网络超时抛出异常（未收到响应）
- **THEN** Temporal 按 RetryPolicy 自动重试 freeze Activity
- **THEN** `transfer_order.status` 保持为 `CREATED`，直至 freeze 最终成功或重试耗尽

#### Scenario: 重试后 freeze 成功
- **WHEN** 首次 freeze 调用因网络超时失败，重试后账户服务返回成功
- **THEN** Workflow 继续正常推进后续步骤
- **THEN** `transfer_order.status` 按正常流程变更（不经过 `FREEZE_FAILED`）

### Requirement: 冻结失败状态可通过查询接口观测

冻结失败后，系统 SHALL 在 `GET /transfers/{transferId}` 接口返回中体现 `FREEZE_FAILED` 状态及错误原因。

#### Scenario: 查询冻结失败的转账单
- **WHEN** 调用 `GET /transfers/{transferId}`，该转账因余额不足处于 `FREEZE_FAILED` 状态
- **THEN** 响应中 `status` 为 `FREEZE_FAILED`
- **THEN** 响应中 `lastErrorCode` 为 `ACCOUNT_OPERATION_FAILED`
- **THEN** 响应中 `lastErrorMessage` 包含失败原因描述
