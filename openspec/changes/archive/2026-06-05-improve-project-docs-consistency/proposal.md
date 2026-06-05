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
