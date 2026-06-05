## 1. Web 与契约测试

- [ ] 1.1 将 transfer-service Controller 测试规划为 `@WebMvcTest` + MockMvc，覆盖 create/review/get 的成功、校验失败和统一响应结构。
- [ ] 1.2 保留或迁移 Workflow 启动失败测试，确保 `INIT_FAILED` 持久化与 HTTP 错误响应同时被断言。
- [ ] 1.3 补齐 account-service 内部资产接口的 request validation、业务失败和成功 envelope 覆盖。

## 2. Temporal 场景与状态机测试

- [ ] 2.1 重写并启用 `TransferScenarioIntegrationTest`，基于当前 Temporal Workflow / Activity 实现覆盖人工审核通过、拒绝、自动完成。
- [ ] 2.2 覆盖 `FREEZE_FAILED`、`CREDIT_FAILED`、`INIT_FAILED` 等终态/失败态，不恢复旧 `retryOne` 或 scheduler 入口。
- [ ] 2.3 增加“源账户已扣减后目标入账失败不反向补偿”的可观察断言，证明只重试 target credit。

## 3. 账户资金不变量测试

- [ ] 3.1 扩展 account-service 快速轨测试，覆盖冻结/扣减/解冻/入账的边界金额、非法金额和状态前置条件。
- [ ] 3.2 扩展真实 MySQL 保真轨幂等测试，覆盖重复请求、唯一索引和并发同幂等键竞争。
- [ ] 3.3 保持 BigDecimal / DECIMAL(32,8) 精度测试，并补齐 scale、rounding 和零/负数拒绝边界。

## 4. 测试夹具与可维护性

- [ ] 4.1 新增 test-source helper，统一构造 `ApiResponse` JSON、WireMock stub、transfer/account 请求和唯一业务键。
- [ ] 4.2 新增或整理 Temporal test harness，确保 `TestWorkflowEnvironment` 创建、Worker 注册、关闭和异常诊断一致。
- [ ] 4.3 消除重复手写 JSON、重复 stub 和脆弱的全局状态依赖，保证测试彼此隔离。

## 5. 验证与文档同步

- [ ] 5.1 更新 `docs/design/testing-strategy.md`，让测试策略与新测试代码布局一致。
- [ ] 5.2 运行 `mvn -q test -DfailIfNoTests=false` 验证快速轨无 Docker 可运行。
- [ ] 5.3 在 Docker 可用环境运行 `mvn -q verify -DfailIfNoTests=false`，验证保真轨和 JaCoCo 门禁。
- [ ] 5.4 运行 `openspec validate --all --strict`，并记录不能执行的环境限制。
