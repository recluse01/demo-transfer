## Purpose
定义账户资产操作在并发请求下的锁顺序与幂等检查顺序，确保相同业务操作只生效一次并以成功幂等响应收敛。

## Requirements

### Requirement: 幂等检查在行锁保护范围内执行

`AccountAssetService` 的 `apply` 方法 SHALL 先获取 `account_balance` 的 `FOR UPDATE` 行锁，再执行 `asset_operation` 的幂等检查。幂等检查 SHALL NOT 在行锁之前执行。

此顺序确保：同一 `transferId + operationType` 的并发请求在行锁处串行化，第二个请求在获得锁后可见第一个请求已写入的幂等记录，直接返回 `applied=false`，不触发余额二次变更，也不向上游产生误报失败。

#### Scenario: 并发相同操作仅应用一次

- **WHEN** 同一 `transferId + operationType` 的两个并发请求同时到达 `AccountAssetService`
- **THEN** 第一个请求正常修改余额并写入幂等记录；第二个请求在获得行锁后命中幂等记录，返回 `applied=false`，余额不再次变更，不抛出约束异常

#### Scenario: 幂等命中返回成功响应而非异常

- **WHEN** 某操作已成功执行，相同请求再次到达
- **THEN** 系统返回 `AssetOperationResponse(applied=false)`，HTTP 状态码为 200，上游 `transfer-service` 不将其视为失败

#### Scenario: 行锁后幂等检查不影响不同 transferId 的并发

- **WHEN** 两个不同 `transferId` 对同一账户的并发请求同时到达
- **THEN** 两个请求在行锁处串行化，各自完成自己的余额变更，不互相干扰
