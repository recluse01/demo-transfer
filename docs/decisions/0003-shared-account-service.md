# ADR-0003 A/B 服务共用一份 account-service

- 状态：Accepted

## 背景

A、B 两个账户服务的资产逻辑（余额、冻结、扣减、入账、流水、幂等）完全相同，只是连接的数据库不同。

## 决策

把账户资产核心实现放在共享模块 `account-service`，由 `account-a-service` 与 `account-b-service` 复用；启动类各自配置数据源、`@EntityScan` 与 `@EnableJpaRepositories`。

## 理由

- 避免两边资产逻辑各写一份后逐渐漂移、行为不一致。
- 修改一处即同时生效于 A/B，降低维护成本。

## 权衡

- 共享实现耦合了两个服务的演进节奏；若未来 A/B 资产逻辑需分化，需要再拆分。
- 教学场景下逻辑一致性收益 > 解耦收益。

## 后果

- A/B 服务体积很薄，主要是启动类与数据源配置。
- 阅读代码时看一份 `AccountAssetService` 即可理解两侧行为。
