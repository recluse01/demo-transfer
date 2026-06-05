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
