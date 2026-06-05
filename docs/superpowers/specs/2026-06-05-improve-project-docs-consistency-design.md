---
comet_change: improve-project-docs-consistency
role: technical-design
canonical_spec: openspec
archived-with: 2026-06-05-improve-project-docs-consistency
status: final
---

# 项目文档一致性修复设计

## 目标

让当前入口文档、接口调试文档、演示文档、测试策略、ADR 和 OpenSpec 主规格都描述同一套实现事实：跨账户划转由 Temporal Workflow 编排，入账失败由 Temporal RetryPolicy 和 Activity 重试收敛，快速测试与全量验证是两条不同执行轨道。

本变更只修正文档和规格，不修改 Java 代码、REST API、数据库 schema、Maven 依赖或 CI 执行策略。

## 推荐方案

采用“当前事实文档边界 + 最小修正 + 残留扫描”的方案。

1. 以当前代码和已启用测试为事实来源，修正 README、API 文档、demo、测试策略和 ADR 中旧 `TransferRetryService`、`TransferRetryScheduler`、`retryOne`、`withdraw-result`、`/{transferId}/retry` 等实现残留。
2. 保留 ADR 和设计文档中的 Saga 概念，但明确当前 Saga 编排载体是 Temporal Workflow，不再暗示旧手写调度器仍存在。
3. 将 `mvn test` 写作快速轨，将 `mvn verify` 写作包含 `*IT`、Testcontainers MySQL 和 JaCoCo 门禁的全量轨道，避免用“全部测试”描述快速命令。
4. 在 OpenSpec 中新增 `project-documentation` 主规格，并修正 `automated-testing`、`continuous-integration`、`temporal-workflow-orchestration` 主规格中的占位 Purpose、错误状态名和旧接口描述。

## 取舍

不选择重写整套文档信息架构。当前问题是事实漂移，不是文档结构失效；大规模重排会放大 diff，并增加读者导航变化。

不恢复或重写旧场景测试。测试恢复属于独立能力变更，本次只要求文档不得把未启用或被注释的测试描述为当前覆盖。

不清理历史归档计划和旧 Superpowers 过程文档中的所有旧词。历史文件是过程记录，除非被当前入口文档作为事实来源引用，否则只在残留扫描中说明其历史属性。

## 文档修改边界

当前事实文档包括：

- `README.md`
- `docs/README.md`
- `docs/api/transfer-debug-api.md`
- `docs/demo/cross-account-transfer-demo.md`
- `docs/design/testing-strategy.md`
- `docs/decisions/*.md`

OpenSpec 修改包括：

- 新增 `openspec/specs/project-documentation/spec.md`
- 修正 `openspec/specs/automated-testing/spec.md`
- 修正 `openspec/specs/continuous-integration/spec.md`
- 修正 `openspec/specs/temporal-workflow-orchestration/spec.md`
- 保持本 change 下 delta spec 与主规格同步，归档时由 OpenSpec 流程合并

## 验证策略

必须运行：

- `openspec validate --all --strict`
- `mvn -q test -DfailIfNoTests=false`
- `mvn -q -DskipTests compile`

必须扫描旧实现残留：

- `TransferRetryService`
- `TransferRetryScheduler`
- `retryOne`
- `withdraw-result`
- `/{transferId}/retry`
- `FROZEN`

残留允许出现在历史归档、旧计划或明确作为“已删除旧实现”的说明中；不得出现在当前接口清单、当前测试覆盖说明或当前状态模型描述中。

`mvn verify` 需要 Docker。若本机 Docker 不可用，只记录环境限制，不把它报告为已通过。
