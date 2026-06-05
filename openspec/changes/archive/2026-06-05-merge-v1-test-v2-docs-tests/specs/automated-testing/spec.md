# automated-testing Delta

## MODIFIED Requirements

### Requirement: 分层测试金字塔与命名约定
合并后的项目 SHALL 保留 `claude/v1-test` 的双轨测试命名与执行约定：快速用例以 `*Test` 命名并在 `mvn test` 运行；保真集成用例以 `*IT` 命名并在 `mvn verify` 运行。`claude/v2` 的实现迁移不得通过删除 v1 测试资产来降低覆盖。

#### Scenario: v2 合并后快速轨仍可运行
- **WHEN** 开发者在合并分支执行 `mvn test`
- **THEN** 快速轨测试不依赖 Docker 并覆盖 Controller、账户服务和转账核心行为

#### Scenario: v2 合并后保真轨仍存在
- **WHEN** 开发者在 Docker 可用环境执行 `mvn verify`
- **THEN** 保真轨测试资源、Testcontainers MySQL 支撑和 JaCoCo 门禁仍按 v1 基线运行

### Requirement: Saga 状态机与不补偿原则
合并后的测试 SHALL 用 v2 的 Temporal Workflow/Activity 或等价入口覆盖 v1 规定的状态机关键不变量，包括源账户扣减后目标入账失败不反向补偿、失败态可重试收敛、人工审核路径、初始化失败与冻结失败终态。

#### Scenario: 旧 Saga 测试迁移到 Temporal 语义
- **WHEN** v1 测试引用已被 v2 删除的 Saga/Retry/Scheduler 类
- **THEN** 测试被改写到 Temporal Workflow、Activity、Controller 或状态服务入口，且保留原业务断言
