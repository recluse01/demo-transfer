# 术语表 / 概念索引

本表集中解释项目涉及的核心概念，每条给出定义与在本项目中的体现。其他文档首次出现术语时链接到此处对应锚点。

## Saga

把一个跨服务的长事务拆成一串本地事务，每步要么成功推进，要么通过补偿/重试收敛。本项目用 Saga 替代分布式事务，详见 [ADR-0001](../decisions/0001-orchestrated-saga.md)。

## 编排式 vs 协同式

- **编排式（Orchestration）**：由一个中心协调者（本项目的 `transfer-service`）显式驱动每一步。
- **协同式（Choreography）**：各服务通过事件互相触发，无中心协调者。

本项目采用编排式，流程与状态集中在转账单上，便于教学和排查。

## 幂等 / 幂等键

同一操作执行一次和多次效果相同。账户服务以 `transferId + operationType` 作为幂等键，重复请求返回成功但 `applied=false`，不重复改余额。详见 [ADR-0004](../decisions/0004-idempotency-by-transferid-operationtype.md)。

## 悲观锁

读取时即加锁，阻止其他事务并发修改。账户服务用悲观锁读取 `account_balance` 后再改余额，避免并发扣减出错。

## 乐观锁（version）

`account_balance.version` 字段标记记录版本，更新时校验版本未变。与悲观锁配合提供并发保护。

## 冻结 / 确认扣减 / 解冻 / 入账

账户服务的四类资产操作：

- **冻结（FREEZE）**：可用减少、冻结增加。
- **确认扣减（CONFIRM_DEBIT）**：冻结减少，资金真正离开源账户。
- **解冻（CANCEL_FREEZE）**：冻结减少、可用恢复。
- **入账（CREDIT）**：目标账户可用增加。

## 最终一致 vs 强一致

- **强一致**：任意时刻各副本数据完全一致（如 XA/2PC）。
- **最终一致**：允许中间态不一致，依赖重试在有限时间内收敛。

本项目只保证最终一致，详见 [ADR-0005](../decisions/0005-eventual-consistency-no-xa.md)。

## 补偿事务

对已提交的本地事务做反向操作以回滚业务效果。本项目在源账户确认扣减后**不做**反向补偿，而是停在 `CREDIT_FAILED` 重试入账，详见 [ADR-0002](../decisions/0002-credit-failed-retry-no-compensation.md)。

## 财务流水（finance_ledger）

每次资产变动写一条流水，记录变动前后金额与业务含义，用于审计与排查。

## Feign

声明式 HTTP 客户端。`transfer-service` 通过 Feign 调用账户服务的内部接口。
