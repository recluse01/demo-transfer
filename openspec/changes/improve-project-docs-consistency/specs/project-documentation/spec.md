## ADDED Requirements

### Requirement: 当前文档必须反映当前实现事实

项目入口文档、设计文档、接口调试文档、演示文档、测试策略和 ADR SHALL 描述当前 Temporal Workflow 实现，而不是已删除的旧手写 Saga/Retry/Scheduler 实现。

#### Scenario: 删除旧接口引用

- **WHEN** 读者查看接口文档或演示文档
- **THEN** 文档只列出当前存在的 `POST /transfers`、`POST /transfers/{transferId}/review` 和 `GET /transfers/{transferId}`，不得把 `/retry` 或 `/withdraw-result` 描述为当前接口

#### Scenario: 重试实现载体一致

- **WHEN** 文档解释 `CREDIT_FAILED` 的收敛方式
- **THEN** 文档说明由 Temporal RetryPolicy / Workflow Activity 重试推进，不引用 `TransferRetryService`、`TransferRetryScheduler` 或 `retryOne`

### Requirement: 验证命令必须区分快速轨与保真轨

项目文档 SHALL 明确 `mvn test` 只运行快速轨 `*Test`，`mvn verify` 才运行快速轨、保真轨 `*IT` 和 JaCoCo 门禁。

#### Scenario: README 验证命令不误导

- **WHEN** README 描述如何运行测试
- **THEN** 它分别给出快速验证和全量验证命令，并注明全量验证需要 Docker

### Requirement: 测试覆盖描述必须匹配已启用测试

项目文档 SHALL 只把当前 Maven 会执行的测试写作“当前测试覆盖”；被注释、待恢复或历史计划中的测试不得写成当前已启用覆盖。

#### Scenario: 场景测试未启用时不宣称已覆盖

- **WHEN** 端到端场景测试类未被 Maven 实际执行
- **THEN** README 和测试策略不得把该类的方法列为当前可运行覆盖，应改为待恢复或由其他测试入口覆盖的说明
