# ADR-0001 用编排式 Saga，不用 XA/Seata/TCC

- 状态：Accepted

## 背景

跨账户划转涉及两个独立数据库（`account_a` / `account_b`）和一个转账库，需要跨服务保证资产一致。

## 决策

用 `transfer-service` 作为中心协调者，通过 Temporal Workflow 按状态机显式驱动冻结、确认扣减、入账、解冻等步骤；每个账户服务只提交本地事务。

## 理由

- 教学目标是讲清跨库一致性，编排式把流程与状态集中在转账单上，最直观。
- 无需引入额外中间件，本地即可运行演示。

## 权衡

| 方案 | 一致性 | 复杂度 | 运行依赖 |
| --- | --- | --- | --- |
| XA/2PC | 强一致 | 高 | 数据库 XA 支持 |
| Seata/TCC | 接近强一致 | 高 | Seata 服务 |
| 编排式 Saga（本项目） | 最终一致 | 中 | 无额外依赖 |

## 后果

- 只保证最终一致（见 [ADR-0005](0005-eventual-consistency-no-xa.md)），失败靠重试收敛。
- 协调者成为流程核心，Temporal Workflow、Activity 幂等和状态机正确性是关键。
