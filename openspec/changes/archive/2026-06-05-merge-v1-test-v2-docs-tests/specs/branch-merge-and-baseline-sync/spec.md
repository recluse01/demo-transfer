# branch-merge-and-baseline-sync Specification

## Purpose

定义 `claude/v1-test` 与 `claude/v2` 合并时的测试、文档和 CI 基线同步约束。

## Requirements

### Requirement: 合并工作必须落在新分支
实现 SHALL 创建一个新的工作分支承载 `claude/v1-test` 与 `claude/v2` 的合并结果，不得直接在两条源分支上完成破坏性合并。

#### Scenario: 从 v1 基线创建合并分支
- **WHEN** 开始执行合并
- **THEN** 新分支以 `claude/v1-test` 为起点创建，并合入 `claude/v2` 的变更

### Requirement: 测试和文档以 v1 为基线
合并结果 SHALL 保留 `claude/v1-test` 中的测试、测试资源、CI 配置、项目文档、ADR、OpenSpec 主 spec 和归档资料；`claude/v2` 中对这些内容的删除不得在无等价替代时进入最终结果。

#### Scenario: v2 删除了 v1 测试或文档
- **WHEN** `claude/v2` 相对 `claude/v1-test` 删除测试、测试资源、CI 或文档文件
- **THEN** 合并结果恢复 v1 版本，或提供覆盖同一行为/信息的等价文件

#### Scenario: v2 文档包含新的实现事实
- **WHEN** `claude/v2` 文档描述了 Temporal 等新实现事实
- **THEN** 合并结果将该事实合入 v1 文档结构，而不是用 v2 文档删除 v1 信息架构

### Requirement: v2 生产实现应被保留并适配 v1 测试体系
合并结果 SHALL 保留 `claude/v2` 的生产实现改动，并让测试体系对齐新实现，而不是通过删除 v1 测试来规避失败。

#### Scenario: v1 测试引用 v2 已删除的旧实现类
- **WHEN** v1 测试断言 `TransferSagaService`、`TransferRetryService` 或 `TransferRetryScheduler` 等旧实现入口
- **THEN** 将测试意图迁移到 Temporal Workflow、Activity、Controller 或状态服务的等价行为
