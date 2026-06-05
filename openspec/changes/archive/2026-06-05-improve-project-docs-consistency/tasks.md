## 1. 文档事实修正

- [x] 1.1 修正 README 的验证命令与测试覆盖口径，区分快速轨和全量 verify。
- [x] 1.2 修正 API 调试文档中的旧提现回调、手动重试说明，保持接口清单与 `TransferController` 一致。
- [x] 1.3 修正 demo 文档的端口示例、测试方法名和 `CREDIT_FAILED` 演示说明。
- [x] 1.4 修正测试策略中的旧 `retryOne`、`TransferRetryService`、`TransferScenarioIT` 口径，改为 Temporal 当前测试入口。
- [x] 1.5 修正 ADR 中旧重试实现载体，说明 Temporal Workflow / RetryPolicy 是当前实现。

## 2. OpenSpec 规格修正

- [x] 2.1 新增 `project-documentation` 规格，定义当前文档一致性要求。
- [x] 2.2 修正 `automated-testing`、`continuous-integration`、`temporal-workflow-orchestration` 主规格中的占位和错误状态。
- [x] 2.3 添加 delta spec，覆盖本次文档一致性变更。

## 3. 验证与收尾

- [x] 3.1 运行 OpenSpec 严格校验。
- [x] 3.2 运行快速测试和编译检查。
- [x] 3.3 扫描旧实现残留并确认剩余命中均为历史归档或明确说明。
- [x] 3.4 更新任务清单并准备进入下一阶段。
