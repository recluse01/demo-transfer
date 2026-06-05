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

## Verification Strategy

- `git status --short --branch` before and after branch creation/merge.
- Path-level diff review for docs/tests/CI files restored from v1.
- `mvn -q test -DfailIfNoTests=false` for fast feedback.
- `mvn -q verify -DfailIfNoTests=false` if Docker/Testcontainers are available.
- If `verify` cannot run because Docker or network is unavailable, record exact reason and at least run module-level fast tests.

## Risks / Rollback

- v1 tests may reference classes removed by v2; rollback is not to delete tests, but to adapt assertions to the new Temporal entry points.
- v2 docs may contain required Temporal setup details; restore v1 docs first, then reapply v2-only factual updates.
- Merge conflicts may be large. Keep changes path-scoped and review `git diff --check` plus targeted diffs before committing.
- If the new branch becomes inconsistent, abandon only that new branch after explicit user approval; do not reset or rewrite `claude/v1-test`/`claude/v2`.
