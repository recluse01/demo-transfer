## MODIFIED Requirements

### Requirement: 分层测试金字塔与命名约定

项目文档 SHALL 明确快速轨与保真轨的边界：`mvn test` 执行 `*Test`，`mvn verify` 执行 `*Test` + `*IT` + JaCoCo 门禁。README、测试策略和 demo 文档不得把快速轨命令描述为“全部测试”。

#### Scenario: 文档命令区分 test 与 verify

- **WHEN** 读者查看 README 或测试策略中的验证命令
- **THEN** 文档清楚说明 `mvn test` 不需要 Docker 且只跑快速轨，`mvn verify` 需要 Docker 且包含保真轨和覆盖率门禁

### Requirement: Saga 状态机与不补偿原则

测试文档 SHALL 将源账户扣减后目标入账失败不补偿的断言映射到当前 Temporal Workflow / Activity 测试入口，不引用已删除的旧 `TransferRetryService`、`TransferRetryScheduler` 或 `retryOne`。

#### Scenario: Temporal 重试测试说明不引用旧服务

- **WHEN** 测试策略解释 `CREDIT_FAILED` 重试收敛
- **THEN** 它使用 `TransferWorkflowImplTest`、`TransferActivitiesImplTest` 或已启用的 Temporal 测试入口说明，不再展示 `retryService.retryOne(...)`
