# 跨账户划转服务实现总览

这份文档解释当前版本是怎么实现跨服务、跨库资产划转的。启动命令、演示请求和数据库操作不在这里重复，分别看 `README.md`、`docs/demo/cross-account-transfer-demo.md` 和 `docs/sql/`。

## 1. 一句话架构

系统用 Temporal Workflow 编排 Saga 流程，三个 Activity 负责账户操作（freeze / confirmDebit / credit / cancelFreeze），幂等和余额一致性由账户服务本地事务保证。

```text
transfer-service (Temporal Worker)
  TransferWorkflow
    |-- freeze Activity     --> account-a-service / account-b-service --> account_x
    |-- confirmDebit Activity
    |-- credit Activity
    +-- cancelFreeze Activity (审核驳回时调用)
```

## 2. 模块职责

| 模块 | 职责 |
| --- | --- |
| `common` | 共享枚举、请求 DTO、统一响应结构。 |
| `account-service` | 账户资产核心实现：余额、冻结、扣减、解冻、入账、流水、幂等。 |
| `account-a-service` | A 账户启动应用，复用 `account-service`，连接 `account_a` 库。 |
| `account-b-service` | B 账户启动应用，复用 `account-service`，连接 `account_b` 库。 |
| `transfer-service` | 转账入口、Temporal Workflow 编排、Activity 实现、状态持久化。 |

A/B 服务的业务代码刻意共用一份 `account-service`，避免两边资产逻辑漂移。

## 3. 核心数据

### transfer 库

| 表 | 用途 |
| --- | --- |
| `transfer_order` | 转账主状态。记录源账户、目标账户、金额、模式和当前状态。 |
| `transfer_step_log` | 转账步骤日志。记录冻结、扣减、入账、解冻等步骤的成功或失败。 |

### account_a / account_b 库

| 表 | 用途 |
| --- | --- |
| `account_balance` | 用户资产余额：可用金额和冻结金额。 |
| `asset_operation` | 账户操作幂等记录，唯一键是 `transfer_id + operation_type`。 |
| `finance_ledger` | 财务流水。每次资产变动写一条，记录变动前后的业务含义。 |

金额统一使用 `BigDecimal` 和 MySQL `DECIMAL(32, 8)`，不使用浮点数。

## 4. 对外接口

### 转账服务接口

| 接口 | 作用 |
| --- | --- |
| `POST /transfers` | 创建转账单，启动 Temporal Workflow。 |
| `POST /transfers/{transferId}/review` | 向对应 Workflow 发送 review Signal。 |
| `GET /transfers/{transferId}` | 查询转账单（直接读 transfer_order 表）。 |

### 账户服务内部接口

| 接口 | 资产效果 |
| --- | --- |
| `POST /internal/accounts/assets/freeze` | 源账户：可用减少，冻结增加。 |
| `POST /internal/accounts/assets/confirm-debit` | 源账户：冻结减少，最终扣减完成。 |
| `POST /internal/accounts/assets/cancel-freeze` | 源账户：冻结减少，可用恢复。 |
| `POST /internal/accounts/assets/credit` | 目标账户：可用增加。 |

账户接口由 `transfer-service` 的 Activity 通过 Feign 调用。

## 5. 正常流程

### 人工审核通过

```text
POST /transfers
  -> transfer_order = CREATED
  -> Temporal Workflow 启动
  -> freeze Activity  -> transfer_order = WAIT_REVIEW

POST /transfers/{id}/review approved=true
  -> review Signal 发送至 Workflow
  -> confirmDebit Activity  -> transfer_order = DEBIT_SUCCESS
  -> credit Activity        -> transfer_order = SUCCESS
```

### 人工审核驳回

```text
POST /transfers/{id}/review approved=false
  -> review Signal 发送至 Workflow
  -> cancelFreeze Activity  -> transfer_order = REJECTED
```

### 站内自动完成

```text
POST /transfers (mode=AUTO_WITHDRAW)
  -> Temporal Workflow 启动
  -> freeze Activity
  -> confirmDebit Activity  -> transfer_order = DEBIT_SUCCESS
  -> credit Activity        -> transfer_order = SUCCESS
```

A 转 B 和 B 转 A 只差源账户、目标账户相反，路由由 `AccountClientRouter` 根据 `TransferDirection` 决定。

## 6. 状态

| 状态 | 含义 | 后续 |
| --- | --- | --- |
| `CREATED` | 转账单已创建，Workflow 已启动。 | Activity 自动推进。 |
| `WAIT_REVIEW` | 冻结成功，等待人工审核（MANUAL_REVIEW 模式）。 | 审核 Signal 触发后续。 |
| `REJECTED` | 审核驳回，冻结已成功取消。 | 终态。 |
| `DEBIT_SUCCESS` | 源账户确认扣减成功，目标账户入账进行中。 | credit Activity 继续。 |
| `CREDIT_FAILED` | 目标账户入账失败，Workflow 重试中或重试耗尽。 | 由 Temporal RetryPolicy 自动重试。 |
| `SUCCESS` | 全部步骤完成。 | 终态。 |

关键原则：一旦源账户冻结金额已确认扣减，目标入账失败不触发反向补偿，持续重试目标入账直到成功或重试耗尽。

## 7. Activity 事务边界

每个 Activity 方法严格遵循两阶段模式，确保 DB 连接不在 Feign 调用期间被持有：

1. `TransferOrderStateService.loadOrder()` — 独立只读事务，加载订单后立即释放连接。
2. Feign 调用 — 无事务，账户服务在自己的事务内完成资产操作并返回结果。
3. `TransferOrderStateService.markXxx()` — 独立写事务，更新 transfer_order 状态。

若 Feign 调用失败，Activity 抛出 RuntimeException，由 Temporal 的 RetryPolicy 自动重试（初始间隔 2s，指数退避，最大间隔 5min，最多 10 次）。

## 8. 幂等与本地事务

账户服务每个操作都在本地事务里完成三件事：

1. 查询 `asset_operation`，发现相同 `transfer_id + operation_type` 已成功则直接返回，不再改余额。
2. 用悲观锁读取 `account_balance`（先加锁再做幂等检查，防止并发下的幂等竞争），检查余额并更新可用/冻结金额。
3. 写入 `finance_ledger` 和 `asset_operation`。

这样可以抵抗 Feign 超时、Temporal 重试、人工重复点击导致的重复请求。重复请求返回成功，但 `AssetOperationResponse.applied=false`。

## 9. 阅读代码顺序

建议按这个顺序看代码：

1. `common`：先看 `TransferStatus`、`TransferMode`、`TransferDirection`、`OperationType`。
2. `account-service`：看 `AccountAssetService`，理解四类资产操作和幂等。
3. `transfer-service`：看 `TransferActivities` 和 `TransferActivitiesImpl`，理解 Activity 事务边界。
4. `transfer-service`：看 `TransferWorkflowImpl`，理解 Workflow 主流程和 Signal 处理。
5. `transfer-service`：看 `TemporalWorkerConfig`，理解 Worker 注册和 RetryPolicy 配置。
6. `account-a-service` / `account-b-service`：看启动类，理解同一账户实现如何连接不同数据库。

测试可以作为行为说明：

- `AccountAssetServiceTest`：账户操作和幂等。
- `AccountOperationIntegrationTest`：账户流水和余额结果。
- `TransferActivitiesImplTest`：Activity DB 状态更新和异常行为。
- `TransferWorkflowImplTest`：Workflow 流程分支和 Activity 重试。
- `TransferScenarioIntegrationTest`：端到端业务场景（真实 Activity + H2 + TestWorkflowEnvironment）。

## 10. 当前限制

- 站内自动转账支持 A 转 B 和 B 转 A；链上提币尚未实现，后续应独立建模。
- 目标入账失败后只做 Temporal 自动重试，不做反向补偿。
- 未实现认证鉴权、风控、限流、对账、运营审批页面。
- Feign 调用异常的精细分类还比较基础，后续可补充超时、熔断和统一错误映射。
- 数据库 schema 当前适合演示和基础验证，生产化还需要迁移工具、审计字段和更完整索引策略。
