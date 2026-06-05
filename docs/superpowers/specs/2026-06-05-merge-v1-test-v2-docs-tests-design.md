---
comet_change: merge-v1-test-v2-docs-tests
role: technical-design
canonical_spec: openspec
---

# 合并 claude/v1-test 与 claude/v2，并按 v1 对齐测试和文档

## 目标

创建新的合并工作分支，收敛 `claude/v1-test` 与 `claude/v2` 的有效成果。最终结果保留 v2 的生产实现变更，尤其是 Temporal Workflow、状态处理修复和业务日志；同时恢复或更新 v2 中被删除/改写的 v1 测试、文档、CI 和 OpenSpec 基线。

## 技术方案

采用“v1 基线分支 + 选择性合入 v2”的方案：

1. 从 `claude/v1-test` 创建 `claude/merge-v1-test-v2-docs-tests`。
2. 合入 `claude/v2`，生产代码冲突优先保留 v2 的 Temporal、状态修复、日志和必要依赖。
3. 测试、文档、CI、OpenSpec 主 spec 与归档资料优先恢复 v1 版本。
4. 对 v1 中引用旧 Saga/Retry/Scheduler 类的测试，不机械恢复到无法编译；保留测试意图，迁移到 Temporal Workflow、Activity、Controller 或状态服务的等价入口。
5. 以 Maven 编译、快速测试和保真轨验证驱动收敛，所有任务完成前不得通过删除有效测试或削弱断言来绕过失败。

## 冲突解决规则

| 路径/类型 | 优先来源 | 处理规则 |
| --- | --- | --- |
| 生产代码 | v2 | 保留 Temporal、状态修复、日志等实现，并按 v1 工程约定修正编译/测试问题 |
| 测试代码与测试资源 | v1 为基线，必要处适配 v2 | 恢复 v1 删除的测试资产；旧 Saga 专属测试迁移为 Temporal 等价断言 |
| 文档与 ADR | v1 为基线，必要处补充 v2 | 恢复文档中心、ADR、测试策略、CI 文档；Temporal 事实补入既有结构 |
| CI 配置 | v1 | 保留 GitHub Actions 与 GitLab CI 双平台配置和 JaCoCo 门禁 |
| OpenSpec 主 spec/归档 | v1 | 恢复 automated-testing、continuous-integration 主 spec 与归档资料 |
| 本地环境/生成物 | 默认不纳入 | `.understand-anything/**`、未跟踪 agent/skill 文件不作为本 change 交付内容 |

## 测试迁移策略

必须保留的测试能力：

- `mvn test` 快速轨不依赖 Docker。
- `mvn verify` 保真轨执行 Testcontainers MySQL 与 JaCoCo 门禁。
- 账户金额精度、幂等、Web 层校验与统一响应结构。
- 转账关键状态：初始化失败、冻结失败、入账失败不反向补偿、人工审核路径。
- v2 Temporal Workflow/Activity 测试覆盖保留，并与 v1 的业务不变量对齐。

迁移规则：

- 如果测试引用 `TransferSagaService`、`TransferRetryService`、`TransferRetryScheduler` 等 v2 已删除类，先识别原断言的业务不变量，再迁移到 `TransferWorkflowImpl`、`TransferActivitiesImpl`、`TransferController` 或 `TransferOrderStateService`。
- 如果 v2 已有等价测试，保留更贴近当前实现的测试，并补齐 v1 中缺失的边界断言。
- 不删除有效测试来换取编译通过；确实过时的测试只在等价覆盖存在后移除。

## 文档同步策略

恢复 v1 的文档信息架构和决策记录，包括 README、文档中心、术语表、ADR、测试策略、CI/OpenSpec 资料。v2 中关于 Temporal、初始化失败、冻结失败、业务日志等新的事实，应补入既有文档结构中，而不是覆盖掉 v1 的背景解释和导航。

## 验证计划

1. 合并前后运行 `git status --short --branch`，确认未跟踪 agent/skill 文件不被纳入本 change。
2. 合并后执行 `git diff --check`，排查冲突残留和空白错误。
3. 执行 `mvn -q test -DfailIfNoTests=false`。
4. Docker 可用时执行 `mvn -q verify -DfailIfNoTests=false`。
5. 检查 `openspec/changes/merge-v1-test-v2-docs-tests/tasks.md` 全部任务勾选，并确认 diff 只包含本 change 范围。

## 风险与处理

- v1 测试引用旧实现导致编译失败：迁移断言到 Temporal 等价入口，而不是删除测试。
- v2 文档包含新事实但 v1 文档结构更完整：先恢复 v1，再补入 v2 必要事实。
- 合并冲突范围较大：按路径来源优先级解决，避免顺手重构。
- 工作区有未跟踪环境文件：只暂存本 change 相关文件，后续提交前再次检查。
