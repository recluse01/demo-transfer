## ADDED Requirements

### Requirement: 保真轨隔离不变量与本地容器复用
保真轨测试 MUST NOT 依赖共享单例 `MySQLContainer` 的全局状态（如「表为空」）来通过断言：冒烟 IT SHALL 以「按不存在的唯一键查询返回空 + 表可计数不抛异常」证明 schema 存在且可查询；所有写库 IT MUST 通过唯一业务键、事务回滚或显式清理保证彼此隔离，不污染共享容器。容器复用（`withReuse`）SHALL 仅作为开发者本地 opt-in 的提速手段，MUST NOT 成为测试正确性的前提——已存在的复用容器不会重新执行初始化脚本，故测试不得依赖容器初始状态或跨 JVM 残留数据。

#### Scenario: 冒烟 IT 不依赖全局空表
- **WHEN** 保真冒烟 IT 单独运行、在全量 `mvn verify` 中运行、或置于其他写库 IT 之后运行
- **THEN** 它均通过，不依赖目标表为空——通过「唯一键查空 + 表可计数」验证 schema 可查询

#### Scenario: 写库 IT 彼此隔离
- **WHEN** 多个写库 IT 在同一 JVM 内共享单例容器顺序执行
- **THEN** 每个用例靠唯一业务键、事务回滚或显式清理保证不受其他用例残留数据影响

#### Scenario: 本地容器复用不影响正确性
- **WHEN** 开发者在 `~/.testcontainers.properties` 开启 `testcontainers.reuse.enable=true` 并跨多次运行复用容器
- **THEN** 写库 IT 仍全部通过、不依赖残留状态；CI 因不设该属性而始终使用全新容器，行为不变
