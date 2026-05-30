# ADR-0004 以 transferId + operationType 作为幂等键

- 状态：Accepted

## 背景

Feign 超时、转账服务重试、人工重复点击都可能让同一账户操作被请求多次，不能造成重复扣款或重复入账。

## 决策

账户服务对每个操作在本地事务内：先查 `asset_operation`（唯一键 `transfer_id + operation_type`），已成功则直接返回；否则改余额并写入 `finance_ledger` 与 `asset_operation`。

## 理由

- 一笔转账的每类操作语义上只应发生一次，`transferId + operationType` 正是这个唯一性。
- 幂等校验与资产变动在同一本地事务内完成，避免半成功。

## 权衡

- 幂等键依赖调用方传入正确的 `transferId`；调用方传错会绕过保护（教学版可接受）。

## 后果

- 重复请求返回 `success=true` 且 `applied=false`，余额不再变化。
- 该幂等是整个 Saga 失败重试能安全反复执行的基础。
