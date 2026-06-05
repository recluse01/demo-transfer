## Purpose
定义 transfer-service 使用 Temporal Workflow 编排跨账户划转的启动、Activity 重试、人工审核 Signal、确定性约束与对外接口边界。

## Requirements

### Requirement: Workflow 启动

系统 SHALL 在接收到 `POST /transfers` 请求时，先将 `transfer_order` 以 `CREATED` 状态持久化，再以 `transferId` 为 Workflow ID 启动 Temporal Workflow，并立即返回 `transferId`。Workflow ID 与 `transferId` SHALL 保持一一对应，确保同一笔转账不会启动两个工作流。

#### Scenario: AUTO_WITHDRAW 模式启动工作流

- **WHEN** 用户提交 `direction=A_TO_B, mode=AUTO_WITHDRAW` 的创建请求
- **THEN** 系统在 DB 中创建状态为 `CREATED` 的 `transfer_order`，启动 Temporal Workflow，并返回 `transferId`

#### Scenario: MANUAL_REVIEW 模式启动工作流

- **WHEN** 用户提交 `direction=B_TO_A, mode=MANUAL_REVIEW` 的创建请求
- **THEN** 系统在 DB 中创建状态为 `CREATED` 的 `transfer_order`，启动 Temporal Workflow，Workflow 在完成冻结后进入 Signal 等待状态，DB 中 `transfer_order` 状态更新为 `WAIT_REVIEW`

#### Scenario: 重复 transferId 不允许重复启动

- **WHEN** 以相同 `transferId` 再次调用启动逻辑
- **THEN** Temporal 返回 `WorkflowExecutionAlreadyStarted`，系统 SHALL 向调用方返回错误，不创建重复工作流

---

### Requirement: Activity 执行与自动重试

系统 SHALL 将每个账户操作（freeze、confirmDebit、credit、cancelFreeze）实现为独立的 Temporal Activity。每个 Activity SHALL：
1. 调用对应账户服务的 Feign 接口
2. 将 Feign 调用结果以独立 `@Transactional` 更新到 `transfer_order`
3. Feign 调用和 DB 更新 SHALL 不在同一个事务边界内

Activity 失败时 Temporal SHALL 按 RetryPolicy 自动重试（初始间隔 2s，指数退避系数 2，最大间隔 5min，最大尝试次数 10）。

#### Scenario: freeze Activity 成功

- **WHEN** `freeze` Activity 调用账户服务返回成功
- **THEN** `transfer_order.status` 更新为 `FROZEN`，Workflow 继续执行下一步

#### Scenario: freeze Activity 失败并自动重试

- **WHEN** `freeze` Activity 调用账户服务超时或返回错误
- **THEN** Temporal 按 RetryPolicy 自动重试，`transfer_order.status` 保持上一个已知状态，调用方无感知

#### Scenario: credit Activity 失败时不反向补偿

- **WHEN** `credit` Activity 多次重试后仍失败（达到最大尝试次数）
- **THEN** Workflow 以失败状态结束，`transfer_order.status` 更新为 `CREDIT_FAILED`，不自动调用 cancelFreeze（与当前设计一致）

---

### Requirement: 人工审核 Signal

系统 SHALL 通过 Temporal Signal 实现人工审核。`POST /{transferId}/review` 接口 SHALL 向对应 Workflow 发送 `review` Signal，Workflow 收到 Signal 后继续执行审核后流程。

#### Scenario: 审核通过

- **WHEN** 调用 `POST /{transferId}/review` 且 `approved=true`
- **THEN** Workflow 收到 Signal，继续执行 `confirmDebit` → `credit` 流程

#### Scenario: 审核驳回

- **WHEN** 调用 `POST /{transferId}/review` 且 `approved=false`
- **THEN** Workflow 收到 Signal，执行 `cancelFreeze`，`transfer_order.status` 更新为 `REJECTED`，Workflow 结束

#### Scenario: 对非 WAIT_REVIEW 状态发送 Signal

- **WHEN** Workflow 不处于等待 Signal 状态（如已完成）时发送审核 Signal
- **THEN** 系统 SHALL 返回错误，不产生副作用

---

### Requirement: 工作流确定性约束

Workflow 实现代码 SHALL 满足 Temporal 确定性要求：不得在 Workflow 代码中直接调用 `System.currentTimeMillis()`、`new Date()`、`Math.random()`、线程 sleep、文件 IO 或网络 IO。所有非确定性操作 SHALL 封装在 Activity 中。

#### Scenario: 时间获取在 Activity 中执行

- **WHEN** 工作流需要记录操作时间戳
- **THEN** 时间戳由 Activity 在执行时生成并写入 DB，Workflow 代码不直接获取系统时间

---

### Requirement: transfer_order 查询投影

系统 SHALL 通过 `GET /transfers/{transferId}` 接口返回 `transfer_order` 表中的当前状态，该接口 SHALL 不依赖 Temporal Query API。`transfer_order` 的状态字段由各 Activity 作为副作用维护。

#### Scenario: 查询进行中的转账

- **WHEN** 调用 `GET /transfers/{transferId}`，对应 Workflow 仍在执行中
- **THEN** 返回 `transfer_order` 表中最新状态，不阻塞等待 Workflow 完成

#### Scenario: 查询已完成的转账

- **WHEN** 调用 `GET /transfers/{transferId}`，对应 Workflow 已结束
- **THEN** 返回终态（`SUCCESS` 或 `REJECTED`）

---

### Requirement: 删除手动重试接口

系统 SHALL 移除 `POST /{transferId}/retry` 接口，重试由 Temporal RetryPolicy 自动处理，不再对外暴露。

#### Scenario: 调用已移除的重试接口

- **WHEN** 调用 `POST /{transferId}/retry`
- **THEN** 系统返回 404，不执行任何重试操作

---

### Requirement: 删除旧兼容提币接口

系统 SHALL 移除 `POST /{transferId}/withdraw-result` 接口，`WITHDRAW_PENDING` 和 `WITHDRAW_FAILED` 状态不再出现在新订单中。

#### Scenario: 调用已移除的兼容接口

- **WHEN** 调用 `POST /{transferId}/withdraw-result`
- **THEN** 系统返回 404
