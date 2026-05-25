## Context

`temporal-saga-refactor` 变更在精简 `TransferStatus` 枚举时，将原本存在的 `FREEZE_FAILED` 移除，依赖 Temporal 的 RetryPolicy（最多重试 10 次、指数退避）兜底。这一决策对于 `credit` 失败是合理的（不反向补偿、重试收敛），但对 `freeze` 失败是错误的：余额不足属于确定性业务失败，每次重试都会得到相同结果，导致 Workflow 在 RUNNING 状态停留约 20 分钟后才进入 FAILED，而 `transfer_order.status` 始终停留在 `CREATED`，外部无法区分"刚提交"和"冻结失败"两种语义。

`temporal-saga-refactor` 的 design.md Open Questions 中曾提出但未回答：

> `transfer_order` 的 `status` 字段在 Activity 失败重试期间是否需要体现"重试中"状态？（当前方案：不需要）

本变更将这个问题的答案修订为：**业务终态失败应立即反映到 DB，而不是等待 Temporal 重试耗尽**。

## Goals / Non-Goals

**Goals:**
- `freeze` Activity 对业务性失败（账户服务返回 `success=false`）立即终止 Workflow，不重试
- 终止前将 `FREEZE_FAILED` 状态及错误上下文持久化到 `transfer_order`
- 保留瞬时/网络失败（Feign 抛异常）的重试行为不变
- 与 `credit()` 的"先写失败状态、再上抛"模式保持一致

**Non-Goals:**
- 不修复 `cancelFreeze` 失败时状态停在 `WAIT_REVIEW` 的问题（独立变更）
- 不修复 `confirmDebit` 失败时无中间状态的问题（发生率极低，独立变更）
- 不引入"重试进行中"可见状态（Temporal 内部重试对外不可见，保持现状）
- 不变更账户服务的响应结构或错误码

## Decisions

### D1：使用 `ApplicationFailure.newNonRetryableFailure()` 标记业务失败

**决策**：freeze 收到 `success=false` 响应时，调用 `ApplicationFailure.newNonRetryableFailure(message, errorCode)` 而非普通 `RuntimeException`。

**备选方案**：
- *修改 RetryPolicy*：将 `ACCOUNT_OPERATION_FAILED` 加入 `doNotRetry` 列表。问题：需要在 Workflow 层感知账户服务错误码，耦合度高；且 RetryPolicy 是 Workflow 级别，无法按 Activity 差异化配置。
- *捕获并静默*：在 freeze 内部 catch 掉业务失败，直接返回（不抛异常）。问题：Temporal 认为 Activity 成功，Workflow 继续推进，将产生错误的状态转移。

**理由**：`ApplicationFailure.newNonRetryableFailure()` 是 Temporal SDK 提供的标准非重试信号，语义明确；Workflow 收到后立即转为 FAILED，无需在 Workflow 层做任何改动。

### D2：业务失败时先写 DB 再抛异常

**决策**：freeze 失败处理顺序：`markFreezeFailed()` → `throw ApplicationFailure.newNonRetryableFailure()`。

**理由**：与 `credit()` 的现有模式完全一致（先 `markCreditFailed()`，再 throw）。如果先抛、不写 DB，Temporal 记录失败但 DB 无对应状态，查询方看到的仍是 `CREATED`。由于是非重试性失败，不存在"Activity 因 DB 写入失败而被重试导致重复写入"的问题。

### D3：以 `response.isSuccess()` 作为业务失败的判断边界

**决策**：仅当 `response.isSuccess() == false` 时走非重试路径；Feign 抛出的所有异常（网络超时、5xx 等）仍走原有可重试路径。

**理由**：当前代码中，网络/超时失败已经通过 Feign 异常上浮，由 Temporal 自动重试——这是正确的。只有账户服务主动返回业务失败（HTTP 200 + `success=false`）才是确定性的业务终态。两条路径在代码层面已天然分离，无需额外判断。

## Risks / Trade-offs

- **[存量 CREATED 订单无影响]** 变更上线前处于 `CREATED` 状态的订单（若有）仍由 Temporal Workflow 推进，因为它们的 freeze 要么已成功、要么已在重试中——新代码只影响新触发的 `freeze` 调用。
- **[FREEZE_FAILED 为终态，无恢复路径]** 用户需重新提交转账请求。这是期望行为：余额不足后充值，再次发起新的转账单。如未来需要支持"补充余额后继续"的场景，需单独设计恢复机制。
- **[Temporal Workflow 进入 FAILED 状态]** freeze 失败后 Temporal 侧 Workflow 为 FAILED，DB 侧为 FREEZE_FAILED，两者语义一致，无需额外处理。监控告警如果基于 Temporal Workflow FAILED 计数，需区分"业务终态"与"系统异常"，建议在错误类型或 Workflow 元数据中加以区分（超出本次变更范围）。

## Migration Plan

1. 部署新版本 `transfer-service`（含 `FREEZE_FAILED` 枚举值和更新后的 `freeze` Activity）
2. 存量订单不受影响：已在 WAIT_REVIEW / DEBIT_SUCCESS 等状态的订单，其 Workflow 不会重新执行 freeze
3. 无需数据迁移：`status` 列为 `VARCHAR(32)`，新枚举值直接可写入
4. 回滚：回退 `transfer-service` 版本即可；已写入的 `FREEZE_FAILED` 记录在旧版本中不会被读取到，对业务无影响

## Open Questions

（无）
