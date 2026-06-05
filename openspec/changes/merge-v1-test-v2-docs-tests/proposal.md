## Why

`claude/v2` 引入了 Temporal 工作流、状态处理修复和日志等实现变更，但相对 `claude/v1-test` 同时删除或改写了大量测试、CI 与项目文档资产。需要创建一个新的合并工作分支，把两条分支的实现成果收敛到一起，并将 v2 中的测试和文档内容按 v1 的版本恢复或对齐，避免测试策略、ADR、CI 说明和保真测试覆盖在合并后丢失。

## What Changes

- 从 `claude/v1-test` 与 `claude/v2` 合并出一个新的工作分支，作为后续集成与验证的唯一落点。
- 保留 `claude/v2` 中确有价值的生产代码变更，例如 Temporal Workflow 编排、初始化失败终态、冻结失败终态、业务日志和相关依赖配置。
- 对 v2 中被删除或改写的测试内容按 `claude/v1-test` 版本恢复/更新，包括快速轨、保真轨、测试资源、测试支撑基类和 CI 覆盖率门禁相关内容。
- 对 v2 中被删除或改写的文档内容按 `claude/v1-test` 版本恢复/更新，包括 README、文档中心、ADR、测试策略、演示文档、OpenSpec 主 spec 与归档资料。
- 解决合并冲突时优先保持项目既有原则：JDK 8、Maven 多模块、双轨测试、Testcontainers MySQL、JaCoCo 门禁、GitHub/GitLab CI 并存说明。
- 不在本 change 中引入新的业务能力、外部服务、数据库迁移策略或额外框架。

## Capabilities

### New Capabilities
- `branch-merge-and-baseline-sync`: 定义 claude 分支合并与 v1 测试/文档基线同步的交付约束。

### Modified Capabilities
- `automated-testing`: v2 合并结果必须保留 v1 的双轨测试、保真轨支撑、测试资源和覆盖率门禁要求。
- `continuous-integration`: v2 合并结果必须保留 v1 的 GitHub/GitLab CI 文档与配置约束，不能因 v2 合并删除现有 CI 能力。

## Impact

- 分支：新增一个从 `claude/v1-test`/`claude/v2` 合并出的工作分支。
- 测试：影响 `account-service/src/test/**`、`transfer-service/src/test/**`、各模块 `src/test/resources/**`、测试支撑基类、Temporal 相关测试适配。
- 文档：影响 `README.md`、`AGENTS.md`、`docs/**`、`openspec/specs/**`、`openspec/changes/archive/**`、`docs/superpowers/**`。
- CI/构建：影响 `.github/workflows/ci.yml`、`.gitlab-ci.yml`、根 `pom.xml` 以及模块 `pom.xml` 中与测试、Temporal、覆盖率相关的依赖和插件配置。
- 风险：冲突解决可能让 v2 的新实现与 v1 的旧测试断言不一致；后续 build 阶段必须以编译、单元测试和 `mvn verify` 为准逐步修正。
