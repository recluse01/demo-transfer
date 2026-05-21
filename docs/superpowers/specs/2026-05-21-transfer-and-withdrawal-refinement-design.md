# 站内转账与链上提币边界修订设计

## 背景

当前基础版已经实现 A/B 两个账户之间的跨服务、跨库资产划转。实现方式是由 `transfer-service` 编排 Saga 流程，账户 A 和账户 B 分别由独立服务、独立 MySQL 库管理。

这次修订要澄清两个业务概念：

- 站内转账：资产仍在系统内部，只是在账户 A 和账户 B 之间划转，不涉及链上交易。
- 链上提币：资产从账户 A 或账户 B 提出到外部链上地址，涉及广播交易和确认数。

当前代码中的 `AUTO_WITHDRAW` 实际用于站内自动完成划转，不应该被理解为链上提币。

## 本次实现范围

本次只调整站内转账能力：

- `AUTO_WITHDRAW` 同时支持 `A_TO_B` 和 `B_TO_A`。
- 站内 `AUTO_WITHDRAW` 在源账户冻结成功后，由系统主动继续完成扣冻结和目标入账。
- 站内 `AUTO_WITHDRAW` 不再等待人工调用 `/withdraw-result` 才继续推进。
- 保留现有失败状态与重试机制：`DEBIT_FAILED`、`CREDIT_FAILED`、`CANCEL_FAILED`。
- 更新中文文档，明确站内转账与链上提币的边界。

本次不实现链上提币代码，仅沉淀下一阶段设计。

## 非目标

- 不接入真实链节点。
- 不新增交易哈希、确认数、链上手续费等字段。
- 不实现链上广播、确认回调或轮询。
- 不新增风控、对账、运营后台。
- 不引入消息队列或分布式事务中间件。

## 站内转账模式

### 人工审核模式

`MANUAL_REVIEW` 继续保持现有流程。

```text
创建站内转账
 -> 源账户 -可用 +冻结
 -> 等待人工审核
 -> 审核通过：源账户 -冻结，目标账户 +可用
 -> 审核驳回：源账户 +可用 -冻结
```

支持方向：

- `A_TO_B`：账户 A 为源账户，账户 B 为目标账户。
- `B_TO_A`：账户 B 为源账户，账户 A 为目标账户。

### 自动完成模式

`AUTO_WITHDRAW` 在站内转账场景中表示“系统自动完成站内划转”，不表示链上提币。

```text
创建站内转账
 -> 源账户 -可用 +冻结
 -> 系统主动确认扣减源账户冻结金额
 -> 系统主动给目标账户加可用金额
 -> 转账成功
```

支持方向：

- `A_TO_B`
- `B_TO_A`

## 状态机调整

### 保持不变的状态

以下状态继续复用：

- `CREATED`：转账单已创建，尚未完成冻结。
- `FREEZE_FAILED`：源账户冻结失败。
- `WAIT_REVIEW`：源账户已冻结，等待人工审核。
- `DEBIT_FAILED`：源账户确认扣冻结失败。
- `DEBIT_SUCCESS`：源账户确认扣冻结成功，目标账户尚未入账。
- `CREDIT_FAILED`：目标账户入账失败。
- `CANCEL_FAILED`：取消冻结失败。
- `REJECTED`：已驳回并完成解冻。
- `SUCCESS`：源账户扣减和目标账户入账均完成。

### 兼容保留的状态

`WITHDRAW_PENDING` 和 `WITHDRAW_FAILED` 暂时保留，用于兼容已有接口和历史语义，但站内自动完成的新流程不再主动停留在 `WITHDRAW_PENDING`。

后续如果实现链上提币，应为链上提币建立独立状态，而不是继续把链上提币塞进站内转账状态机。

## 站内自动完成状态流转

创建 `AUTO_WITHDRAW` 转账时：

```text
CREATED
 -> FREEZE_FAILED
```

或：

```text
CREATED
 -> DEBIT_SUCCESS
 -> SUCCESS
```

如果确认扣减失败：

```text
CREATED
 -> DEBIT_FAILED
```

如果目标入账失败：

```text
CREATED
 -> DEBIT_SUCCESS
 -> CREDIT_FAILED
```

重试策略沿用现有规则：

- `DEBIT_FAILED`：重试源账户 `confirmDebit`，成功后继续目标账户 `credit`。
- `CREDIT_FAILED`：只重试目标账户 `credit`。
- `CANCEL_FAILED`：只重试源账户 `cancelFreeze`。

## `/withdraw-result` 接口处理

现有 `/transfers/{transferId}/withdraw-result` 接口先保留，避免破坏已有调用方或演示脚本。

接口语义调整为兼容旧单据：

- 如果转账单仍处于 `WITHDRAW_PENDING`，继续按旧逻辑处理成功或失败结果。
- 新创建的站内 `AUTO_WITHDRAW` 转账通常不会进入 `WITHDRAW_PENDING`，因此不需要调用该接口。
- 文档中标注该接口为兼容接口，不作为站内自动转账的主路径。

## 链上提币下一阶段设计

链上提币应作为独立业务能力建模，建议不要复用站内 A/B 转账的 `target credit` 步骤。

### 自动链上提币

```text
发起链上提币
 -> 源账户 -可用 +冻结
 -> 自动提币任务触发链上交易
 -> 交易广播上链
 -> 确认数达标
 -> 源账户 -冻结
 -> 提币成功
```

### 人工审核后链上提币

```text
发起链上提币
 -> 源账户 -可用 +冻结
 -> 等待人工审核
 -> 审核通过
 -> 交易广播上链
 -> 确认数达标
 -> 源账户 -冻结
 -> 提币成功
```

### 审核驳回

```text
发起链上提币
 -> 源账户 -可用 +冻结
 -> 等待人工审核
 -> 审核驳回
 -> 源账户 +可用 -冻结
 -> 提币驳回
```

### 链上提币建议新增概念

后续链上提币建议新增独立模型，例如 `withdraw_order`，核心字段包括：

- 提币单号
- 用户 ID
- 源账户类型：账户 A 或账户 B
- 资产编码
- 提币金额
- 外部链上地址
- 链类型或网络
- 交易哈希
- 当前确认数
- 目标确认数
- 提币模式：自动或人工审核
- 提币状态
- 最后错误码和错误信息

建议状态包括：

- `CREATED`：提币单已创建。
- `FREEZE_FAILED`：冻结失败。
- `WAIT_REVIEW`：等待人工审核。
- `BROADCAST_PENDING`：等待广播链上交易。
- `BROADCAST_FAILED`：广播失败，可重试或人工处理。
- `CHAIN_CONFIRMING`：交易已上链，等待确认数。
- `CONFIRM_FAILED`：确认异常，需要人工处理。
- `DEBIT_FAILED`：确认数达标后扣冻结失败。
- `CANCEL_FAILED`：取消冻结失败。
- `REJECTED`：审核驳回并解冻成功。
- `SUCCESS`：确认数达标并扣冻结成功。

## 测试要求

本次站内转账实现需要补充以下测试：

- `AUTO_WITHDRAW + A_TO_B` 创建后自动完成，最终状态为 `SUCCESS`。
- `AUTO_WITHDRAW + B_TO_A` 创建后自动完成，最终状态为 `SUCCESS`。
- `AUTO_WITHDRAW + B_TO_A` 使用账户 B 作为源账户，账户 A 作为目标账户。
- 自动完成过程中源账户调用 `confirmDebit`，目标账户调用 `credit`。
- 源账户确认扣减失败时进入 `DEBIT_FAILED`。
- 目标账户入账失败时进入 `CREDIT_FAILED`，重试时只调用目标账户 `credit`。
- 文档示例不再把站内自动完成描述为链上提币。

## 验收标准

- `AUTO_WITHDRAW` 不再限制为 `A_TO_B`。
- 新建 `AUTO_WITHDRAW + B_TO_A` 转账可以成功完成。
- 新建站内 `AUTO_WITHDRAW` 转账无需调用 `/withdraw-result` 即可推进到终态或失败重试状态。
- 所有资产变动仍由账户服务本地事务保证余额、幂等记录和财务流水一致。
- 跨服务失败仍可通过现有重试入口恢复。
- 设计文档、README、接口文档和演示文档保持中文口径一致。
