# Comet Design Handoff

- Change: improve-project-docs-consistency
- Phase: design
- Mode: compact
- Context hash: d8b3217fba98f45996ccc43cedb36a147acb8e8320bbd9b2a5337bbf924a4f5c

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/improve-project-docs-consistency/proposal.md

- Source: openspec/changes/improve-project-docs-consistency/proposal.md
- Lines: 1-29
- SHA256: 2dbb0ee993fe4d24f9616122fb7ba747d6b70859609a456a7294abd8022ec5fa

```md
## Why

项目主线已迁移到 Temporal Workflow，但部分文档仍保留旧 Saga/Retry/withdraw-result 口径，且 README、demo、测试策略与实际测试文件之间存在不一致。这会让读者按错误命令验证、误以为已删除接口仍存在，或把未启用测试当成当前覆盖。

## What Changes

- 修正 README、接口调试文档、演示文档、测试策略和 ADR 中的旧实现残留。
- 明确 `mvn test` 与 `mvn verify` 的边界，避免把快速轨写成“全部测试”。
- 对齐 Temporal 当前状态模型、接口清单和测试覆盖口径。
- 修正 OpenSpec 主规格中的占位 Purpose、错误状态名和文档一致性要求。
- 不修改业务代码、接口行为、依赖版本或 CI 执行策略。

## Capabilities

### New Capabilities

- `project-documentation`: 项目文档必须与当前 Temporal 实现、测试执行轨道、接口边界和 OpenSpec 主规格保持一致。

### Modified Capabilities

- `automated-testing`: 修正测试文档对快速轨、保真轨、Temporal 重试测试和当前已启用测试覆盖的要求。
- `temporal-workflow-orchestration`: 修正 Workflow/Activity 文档规格中的状态描述，避免引用不存在的状态或旧接口。
- `continuous-integration`: 补全 CI 规格 Purpose，并明确文档命令不得混淆 `mvn test` 与 `mvn verify`。

## Impact

- 影响文档：`README.md`、`docs/api/transfer-debug-api.md`、`docs/demo/cross-account-transfer-demo.md`、`docs/design/testing-strategy.md`、`docs/decisions/*.md`、`docs/README.md`。
- 影响规格：`openspec/specs/automated-testing/spec.md`、`openspec/specs/continuous-integration/spec.md`、`openspec/specs/temporal-workflow-orchestration/spec.md` 以及新增 `project-documentation` 规格。
- 不影响生产代码、数据库 schema、REST API、Maven 依赖和运行时配置。
```

## openspec/changes/improve-project-docs-consistency/design.md

- Source: openspec/changes/improve-project-docs-consistency/design.md
- Lines: 1-54
- SHA256: 293c23db3516062a397903d263e43e202771025c85e282c5c38d9cc30dda04cc

```md
## Context

当前文档已大体迁移到 Temporal Workflow，但仍有三个层面的不一致：

- 事实错误：旧 `/retry`、`/withdraw-result`、`TransferRetryService`、`TransferRetryScheduler`、`retryOne` 等描述残留。
- 验证口径错误：`mvn test` 被描述为“全部测试”，但它只执行快速轨；保真轨和 JaCoCo 门禁由 `mvn verify` 执行。
- 规格漂移：OpenSpec 主规格仍有 `TBD` Purpose，并出现当前状态枚举不存在的 `FROZEN`。

## Goals

- 让入口文档、设计文档、接口文档、演示文档、测试策略、ADR 和 OpenSpec 主规格表达同一套当前事实。
- 保留历史设计语境中的 “Saga” 概念，但明确当前实现由 Temporal Workflow 编排和重试。
- 给读者清晰区分快速验证、保真验证、本地演示和未来生产化限制。

## Non-Goals

- 不恢复、重写或新增 Java 测试。
- 不新增故障注入接口。
- 不修改 Temporal RetryPolicy、状态机或 REST API。
- 不整理历史 `docs/superpowers/` 归档计划中的旧内容，除非它们被当前入口文档直接引用为事实来源。

## Design Decisions

### 1. 以“当前事实文档”为修复边界

本次只修改会被读者作为当前项目说明使用的文档和 OpenSpec 主规格。历史 plan/spec 作为过程记录保留，不强制重写旧上下文。

### 2. README 使用双命令表达验证层级

README 应把 `mvn test` 标为快速轨，把 `mvn verify` 标为全量验证，并注明 Docker 前提。这样与测试策略、CI 和 OpenSpec `automated-testing` 保持一致。

### 3. 测试覆盖表述必须区分“当前已启用”和“待恢复/设计目标”

若某些场景只存在于被注释的 `TransferScenarioIntegrationTest`，文档不得把它们写作当前可执行覆盖。可以改为：

- 当前由 `TransferWorkflowImplTest` / `TransferActivitiesImplTest` 覆盖的行为。
- `TransferScenarioIntegrationTest` 待恢复为保真场景测试。

### 4. ADR 保留决策，更新实现载体

ADR-0001 可以继续描述“编排式 Saga”这一模式，但应补充当前编排载体是 Temporal Workflow。ADR-0002 的后果应从旧 scheduler/service 改为 Temporal RetryPolicy。

### 5. OpenSpec 主规格只修正契约，不引入实现新承诺

`temporal-workflow-orchestration` 中 `FROZEN` 应替换为当前可观察状态：人工模式 freeze 成功为 `WAIT_REVIEW`，自动模式 freeze 成功后 Workflow 继续推进，不暴露 `FROZEN` 中间态。

## Validation

- `openspec validate --all --strict`
- `mvn -q test -DfailIfNoTests=false`
- `mvn -q -DskipTests compile`
- 文档一致性扫描：`rg` 检查旧实现残留，包括 `TransferRetryService`、`TransferRetryScheduler`、`retryOne`、`withdraw-result`、`FROZEN`。

`mvn verify` 需要 Docker；若本机 Docker 不可用，应记录为环境限制，不把它误写成已通过。
```

## openspec/changes/improve-project-docs-consistency/tasks.md

- Source: openspec/changes/improve-project-docs-consistency/tasks.md
- Lines: 1-20
- SHA256: 7fa7f99c56d5043b38e3f651a6d62ca23898ddd0b6d7e3d2104003f91f0bd580

```md
## 1. 文档事实修正

- [ ] 1.1 修正 README 的验证命令与测试覆盖口径，区分快速轨和全量 verify。
- [ ] 1.2 修正 API 调试文档中的旧提现回调、手动重试说明，保持接口清单与 `TransferController` 一致。
- [ ] 1.3 修正 demo 文档的端口示例、测试方法名和 `CREDIT_FAILED` 演示说明。
- [ ] 1.4 修正测试策略中的旧 `retryOne`、`TransferRetryService`、`TransferScenarioIT` 口径，改为 Temporal 当前测试入口。
- [ ] 1.5 修正 ADR 中旧重试实现载体，说明 Temporal Workflow / RetryPolicy 是当前实现。

## 2. OpenSpec 规格修正

- [ ] 2.1 新增 `project-documentation` 规格，定义当前文档一致性要求。
- [ ] 2.2 修正 `automated-testing`、`continuous-integration`、`temporal-workflow-orchestration` 主规格中的占位和错误状态。
- [ ] 2.3 添加 delta spec，覆盖本次文档一致性变更。

## 3. 验证与收尾

- [ ] 3.1 运行 OpenSpec 严格校验。
- [ ] 3.2 运行快速测试和编译检查。
- [ ] 3.3 扫描旧实现残留并确认剩余命中均为历史归档或明确说明。
- [ ] 3.4 更新任务清单并准备进入下一阶段。
```

## openspec/changes/improve-project-docs-consistency/specs/automated-testing/spec.md

- Source: openspec/changes/improve-project-docs-consistency/specs/automated-testing/spec.md
- Lines: 1-19
- SHA256: 4f736a93323d8d9f20eb31e83368bde2b0e97b59221b3b1480f0c294d9129d2f

```md
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
```

## openspec/changes/improve-project-docs-consistency/specs/continuous-integration/spec.md

- Source: openspec/changes/improve-project-docs-consistency/specs/continuous-integration/spec.md
- Lines: 1-15
- SHA256: aa9c95a942d39964a098d9b93c904ec8c6cb21b29d07c8a6268b5271b8744367

```md
## MODIFIED Requirements

### Requirement: 持续集成自动执行双轨测试与门禁

CI 规格 SHALL 有明确 Purpose，并且项目文档中提到的全量验证命令 SHALL 与 CI 的 `mvn -B verify` 保持一致。

#### Scenario: CI Purpose 不再占位

- **WHEN** 读者查看 `continuous-integration` 主规格
- **THEN** `## Purpose` 提供明确说明，不保留 `TBD`

#### Scenario: 文档全量验证命令与 CI 一致

- **WHEN** README 描述提交前或全量验证
- **THEN** 它使用 `mvn verify` 语义，与 CI 的快速轨、保真轨、JaCoCo 门禁一致
```

## openspec/changes/improve-project-docs-consistency/specs/project-documentation/spec.md

- Source: openspec/changes/improve-project-docs-consistency/specs/project-documentation/spec.md
- Lines: 1-33
- SHA256: 88b92310b7bc5ba55c49e0aa282fc1888efc608b73d636b9ea9a43eb39636faf

```md
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
```

## openspec/changes/improve-project-docs-consistency/specs/temporal-workflow-orchestration/spec.md

- Source: openspec/changes/improve-project-docs-consistency/specs/temporal-workflow-orchestration/spec.md
- Lines: 1-28
- SHA256: 835e855dc63dee9129b83786000b5e7177ee6503cad44ca5b53f67eca0f45a82

```md
## MODIFIED Requirements

### Requirement: Activity 执行与自动重试

Temporal Workflow 规格 SHALL 使用当前 `TransferStatus` 中存在的状态描述 Activity 结果，不引用不存在的 `FROZEN` 状态。人工审核模式下 freeze 成功后进入 `WAIT_REVIEW`；自动模式下 freeze 成功后 Workflow 继续执行 confirmDebit 和 credit，不暴露单独冻结成功状态。

#### Scenario: freeze Activity 成功状态描述有效

- **WHEN** `freeze` Activity 成功
- **THEN** 人工审核模式的 `transfer_order.status` 更新为 `WAIT_REVIEW`，自动模式保持当前状态并继续后续 Activity，不使用 `FROZEN`

### Requirement: 删除手动重试接口

接口文档 SHALL 与该要求一致，不再把 `POST /{transferId}/retry` 描述为当前可调试接口。

#### Scenario: API 文档不列出已删除重试接口

- **WHEN** 读者查看接口清单或排查说明
- **THEN** 文档不提示调用手动重试接口，而是引导查看 Temporal Workflow 历史和 Activity 重试

### Requirement: 删除旧兼容提币接口

接口文档和演示文档 SHALL 与该要求一致，不再把 `POST /{transferId}/withdraw-result` 描述为当前流程的一部分。

#### Scenario: API 文档不列出已删除提币回调接口

- **WHEN** 读者查看站内自动转账说明
- **THEN** 文档说明 Workflow 自动推进，不需要旧版 `/withdraw-result` 回调
```

