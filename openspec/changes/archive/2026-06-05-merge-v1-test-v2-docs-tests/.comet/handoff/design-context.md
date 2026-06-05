# Comet Design Handoff

- Change: merge-v1-test-v2-docs-tests
- Phase: design
- Mode: compact
- Context hash: a71793e5ff57910a44d1fd8a6dfa31743b37c5000b967b4d1e0f8e659a96d9d2

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/merge-v1-test-v2-docs-tests/proposal.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/proposal.md
- Lines: 1-29
- SHA256: ed8f818538fed1ceab87bcb13cbe6e7aa63db894c072ae5453215420b0d20984

```md
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
```

## openspec/changes/merge-v1-test-v2-docs-tests/design.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/design.md
- Lines: 1-96
- SHA256: 2735265045ce2e5dd0653c2e34874260f6f39a779e272577865326ad1fb6c1c7

[TRUNCATED]

```md
## Context

当前工作区位于 `claude/v1-test`，该分支相对远端 ahead 2，且有一批未跟踪的 agent/skill 文件；这些环境文件不属于本 change 范围，后续合并时不得顺手纳入。`openspec list --json` 当前没有 active change。

`git diff --name-status claude/v1-test..claude/v2` 显示 v2 的差异可分为三类：

1. 生产实现：Temporal Workflow、Activity、Worker 配置、转账状态服务、状态枚举调整、Controller 行为修复、业务日志等。
2. 测试/文档/CI 基线漂移：v2 删除了 v1 的多份测试、测试资源、CI 文件、ADR、测试策略、文档中心和 OpenSpec 主 spec/归档资料。
3. 环境或生成物：`.understand-anything/**`、agent skill/command 配置等，不应作为测试/文档对齐的核心目标。

本 change 的核心是分支合并与基线同步，不是重新设计转账业务或 Temporal 架构。

## Goals / Non-Goals

**Goals**

- 创建新的合并工作分支，避免直接在 `claude/v1-test` 或 `claude/v2` 上破坏历史。
- 在合并结果中保留 v2 的生产实现成果。
- 将 v2 的测试、文档、CI 与 OpenSpec 资料按 v1 基线恢复或更新。
- 让最终结果至少通过编译、快速测试，并尽可能通过 `mvn -q verify`。

**Non-Goals**

- 不迁移回旧 Saga 实现，不删除 Temporal 作为 v2 的主要实现方向。
- 不新增 Seata、XA、消息队列、对账中心或新的外部依赖。
- 不修改 git 全局配置，不强推，不重写分支历史。
- 不把未跟踪的本地 agent/skill 环境文件作为本次业务交付内容。

## Decisions

### D1：新分支从 v1 基线创建，再合入 v2

新分支建议从 `claude/v1-test` 创建，因为用户要求“将 v2 的测试和文档内容按 v1 的版本更新”。这使 v1 的测试/文档/CI 成为默认保留项，再把 v2 的生产实现变更合入，冲突处显式选择。

分支名在 build 阶段再执行，建议使用：

```bash
claude/merge-v1-test-v2-docs-tests
```

### D2：冲突解决采用按路径的来源优先级

| 路径/类型 | 优先来源 | 处理规则 |
| --- | --- | --- |
| 生产代码 | v2 | 保留 Temporal、状态修复、日志等实现，并按 v1 约定修正编译/测试问题 |
| 测试代码与测试资源 | v1 为基线，必要处适配 v2 | 恢复 v1 删除的测试资产；对旧 Saga 专属测试改写为 Temporal 语义或替换为 v2 等价测试 |
| 文档与 ADR | v1 为基线，必要处补充 v2 | 恢复 v1 文档中心、ADR、测试策略、CI 文档；涉及 Temporal 的内容保留 v2 更新 |
| CI 配置 | v1 | 保留 GitHub Actions 与 GitLab CI 双平台配置和 JaCoCo 门禁 |
| OpenSpec 主 spec/归档 | v1 | 恢复 automated-testing、continuous-integration 主 spec 与归档资料；v2 新 change 资料按是否仍 relevant 决定保留 |
| 生成物/环境配置 | 默认不纳入 | `.understand-anything/**`、本地 agent/skill 文件除非用户另行要求，否则不作为合并目标 |

### D3：测试对齐不是机械覆盖

“按 v1 的版本更新”不等于把所有 v1 测试原样覆盖到 v2 后强行通过。若 v1 测试断言旧 `TransferSagaService`、`TransferRetryService`、`TransferRetryScheduler` 等已被 v2 删除的类，应保留测试意图并改写到 Temporal Workflow、Activity 或 Controller 层的等价行为上。

必须保留的测试能力包括：

- `mvn test` 快速轨不依赖 Docker。
- `mvn verify` 保真轨执行 Testcontainers MySQL 与 JaCoCo 门禁。
- 账户金额精度、幂等、Controller 入参校验、Feign/WireMock 或等价 HTTP 适配测试。
- 转账关键状态：初始化失败、冻结失败、入账失败不反向补偿、人工审核路径。

### D4：文档对齐以 v1 信息架构为主

v2 删除了多份文档中心、ADR、测试策略和 OpenSpec 归档资料。合并后应恢复 v1 的文档导航和决策记录，同时把 v2 的 Temporal 实现事实写入现有文档，而不是删除 v1 的解释性文档。

## Data / Flow

```text
claude/v1-test
  ├─ tests/docs/ci baseline
  └─ create new merge branch
        │
        ├─ merge claude/v2 production implementation
        │
        ├─ restore v1 tests/docs/ci baseline by path
        │
        ├─ adapt old tests to v2 Temporal semantics
        │
        └─ run mvn test / mvn verify and fix minimal failures
```

Full source: openspec/changes/merge-v1-test-v2-docs-tests/design.md

## openspec/changes/merge-v1-test-v2-docs-tests/tasks.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/tasks.md
- Lines: 1-30
- SHA256: 382782cf7cdca86e9ab9ec082dc8209594e5cdb4fddf6a30eb533d84aceb1f81

```md
# Tasks

## 1. 分支准备
- [ ] 1.1 确认当前工作区未跟踪的 agent/skill 文件不纳入本 change 暂存范围
- [ ] 1.2 从 `claude/v1-test` 创建新工作分支 `claude/merge-v1-test-v2-docs-tests`
- [ ] 1.3 合入 `claude/v2`，记录并分类冲突路径

## 2. 测试基线对齐
- [ ] 2.1 恢复 v1 中被 v2 删除的测试支撑类、测试资源和保真轨配置
- [ ] 2.2 恢复或改写账户服务金额精度、幂等、Web 层测试
- [ ] 2.3 恢复或改写转账服务 Controller、跨服务调用、状态流转测试
- [ ] 2.4 将旧 Saga/Retry/Scheduler 专属断言迁移为 Temporal Workflow/Activity 等价断言
- [ ] 2.5 保留 v2 已新增的初始化失败、冻结失败和 Temporal 工作流相关测试覆盖

## 3. 文档与 OpenSpec 基线对齐
- [ ] 3.1 恢复 v1 的 README、文档中心、术语表、ADR 和测试策略文档
- [ ] 3.2 将 v2 的 Temporal 实现事实补入服务实现总览和演示文档，不删除 v1 的设计解释
- [ ] 3.3 恢复 v1 的 `automated-testing`、`continuous-integration` 主 spec 与相关归档资料
- [ ] 3.4 检查 `AGENTS.md`、提交规范和 docs/superpowers 资料，保留 v1 协作约定

## 4. CI 与构建配置
- [ ] 4.1 保留 v1 的 GitHub Actions 与 GitLab CI 双平台配置
- [ ] 4.2 合并 v2 的 Temporal 依赖与配置，确保仍满足 JDK 8 和 JaCoCo 门禁
- [ ] 4.3 检查 Maven 多模块依赖关系，避免测试依赖泄漏到运行时

## 5. 验证与收尾
- [ ] 5.1 运行 `mvn -q test -DfailIfNoTests=false`
- [ ] 5.2 在 Docker 可用时运行 `mvn -q verify -DfailIfNoTests=false`
- [ ] 5.3 检查 `git status --short --branch` 与相关 diff，确认只包含本 change 范围
- [ ] 5.4 更新 tasks 勾选状态并记录无法运行验证的原因
```

## openspec/changes/merge-v1-test-v2-docs-tests/specs/automated-testing/spec.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/specs/automated-testing/spec.md
- Lines: 1-21
- SHA256: f6d3e376f84a53ea98c104e1a15dcd2ebad82f81cc40538e73923d83dd3d1c34

```md
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
```

## openspec/changes/merge-v1-test-v2-docs-tests/specs/branch-merge-and-baseline-sync/spec.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/specs/branch-merge-and-baseline-sync/spec.md
- Lines: 1-32
- SHA256: a5f7a8cb2c5796075f777714ec41438a1f1506821c2f9c3b40691c12ea8f8322

```md
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
```

## openspec/changes/merge-v1-test-v2-docs-tests/specs/continuous-integration/spec.md

- Source: openspec/changes/merge-v1-test-v2-docs-tests/specs/continuous-integration/spec.md
- Lines: 1-14
- SHA256: 3c67633d8a0505596bfb65f17f22bdbefbb591fc7ccb43a289f540d460671358

```md
# continuous-integration Delta

## MODIFIED Requirements

### Requirement: 持续集成自动执行双轨测试与门禁
合并后的项目 SHALL 保留 `claude/v1-test` 的 GitHub Actions 与 GitLab CI 双平台配置，并继续以 `mvn -B verify` 执行快速轨、保真轨和 JaCoCo 合并覆盖率门禁。`claude/v2` 的合并不得删除 CI 配置或降低门禁。

#### Scenario: 合并后 CI 文件仍存在
- **WHEN** 合并 `claude/v2` 到新工作分支
- **THEN** `.github/workflows/ci.yml` 与 `.gitlab-ci.yml` 仍存在，并保持 v1 的双轨测试执行语义

#### Scenario: Temporal 依赖不破坏 JDK 8 CI
- **WHEN** CI 在 JDK 8 环境执行 `mvn -B verify`
- **THEN** v2 引入的 Temporal 依赖和测试依赖不破坏项目 `source/target 1.8` 编译与 JaCoCo 门禁
```

