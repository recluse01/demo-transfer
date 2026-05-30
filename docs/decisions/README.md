# 架构决策记录（ADR）

本目录记录关键设计决策的背景、权衡与后果。每篇 ADR 是对应"为什么"的单一来源；其他文档只保留一句话结论并链接到此处。

| 编号 | 决策 | 一句话摘要 |
| --- | --- | --- |
| [0001](0001-orchestrated-saga.md) | 编排式 Saga | 用中心协调者 + 状态机替代分布式事务，便于教学与排查。 |
| [0002](0002-credit-failed-retry-no-compensation.md) | CREDIT_FAILED 只重试不补偿 | 源账户已确认扣减后不反向补偿，停在 `CREDIT_FAILED` 重试入账。 |
| [0003](0003-shared-account-service.md) | 共用 account-service | A/B 服务复用同一份账户实现，避免资产逻辑漂移。 |
| [0004](0004-idempotency-by-transferid-operationtype.md) | 幂等键 transferId+operationType | 抵抗超时、重试、重复点击导致的重复扣款/入账。 |
| [0005](0005-eventual-consistency-no-xa.md) | 只保证最终一致 | 不引入 XA/Seata/MQ/对账中心，靠状态与幂等收敛。 |

ADR 格式：背景 / 决策 / 理由 / 权衡 / 后果。状态统一为 Accepted（教学基础版）。
