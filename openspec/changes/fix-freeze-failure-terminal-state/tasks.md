## 1. 枚举与状态服务

- [x] 1.1 在 `common/TransferStatus.java` 中新增 `FREEZE_FAILED` 枚举值，并更新枚举 JavaDoc 注释中的状态转移图（`CREATED → FREEZE_FAILED` 分支）
- [x] 1.2 在 `TransferOrderStateService` 中新增 `markFreezeFailed(String transferId, String code, String message)` 方法，参考 `markCreditFailed` 实现

## 2. Activity 修改

- [x] 2.1 在 `TransferActivitiesImpl.freeze()` 中，将 `!response.isSuccess()` 分支改为：先调用 `stateService.markFreezeFailed(transferId, response.getCode(), response.getMessage())`，再抛 `ApplicationFailure.newNonRetryableFailure(message, errorType)`
- [x] 2.2 确认 Feign 网络异常路径不受影响（Feign 抛出的异常不经过 `!isSuccess()` 分支，仍由 Temporal 可重试地处理）

## 3. 单元测试

- [x] 3.1 在 `TransferActivitiesImplTest` 中补充测试：账户服务返回 `fail("ACCOUNT_OPERATION_FAILED", ...)` 时，`markFreezeFailed` 被调用，且抛出 `ApplicationFailure`（非重试性）
- [x] 3.2 在 `TransferWorkflowImplTest` 中补充测试：`freeze` Activity 抛 `ApplicationFailure.newNonRetryableFailure()` 时，Workflow 立即终止，不重试，`cancelFreeze` / `confirmDebit` / `credit` 均未被调用

## 4. 回归验证

- [x] 4.1 运行 `TransferActivitiesImplTest` 全量测试，确认全部通过
- [x] 4.2 运行 `TransferWorkflowImplTest` 全量测试，确认全部通过
- [ ] 4.3 运行 `mvn -q test -DfailIfNoTests=false`，确认全量测试通过

## 5. 文档更新

- [ ] 5.1 更新 `openspec/changes/temporal-saga-refactor/design.md`：在 Open Questions 处回答该问题，并新增 D7 决策（业务终态失败应立即反映到 DB，以 `ApplicationFailure.newNonRetryableFailure` 区分业务失败与瞬时失败）
