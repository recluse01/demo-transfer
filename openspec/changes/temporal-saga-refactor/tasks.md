## 1. 基础设施准备

- [ ] 1.1 docker-compose.yml 新增 Temporal Server 服务（auto-setup 镜像，MySQL 后端，依赖现有 mysql 服务）
- [ ] 1.2 docker-compose.yml 新增 Temporal UI 服务（端口 8088）
- [ ] 1.3 docker/mysql/init 新增 Temporal 初始化 SQL（创建 temporal / temporal_visibility 数据库）
- [ ] 1.4 transfer-service/pom.xml 新增 `io.temporal:temporal-sdk` 和 `io.temporal:temporal-spring-boot-starter` 依赖
- [ ] 1.5 本地启动验证：`docker compose up -d` 后访问 `http://localhost:8088` 确认 Temporal UI 正常

## 2. F2 修复：幂等检查调序（account-service）

- [ ] 2.1 修改 `AccountAssetService.apply`：将 `findByUserIdAndAssetCodeForUpdate` 移到 `findByTransferIdAndOperationType` 之前
- [ ] 2.2 更新 `AccountAssetService` 的方法注释，反映新执行顺序
- [ ] 2.3 运行 `AccountAssetServiceTest` 和 `AccountOperationIntegrationTest`，确认全部通过

## 3. Temporal Activity 实现（transfer-service）

- [ ] 3.1 新增 `TransferActivities` 接口，声明 `freeze`、`confirmDebit`、`credit`、`cancelFreeze` 四个 `@ActivityMethod`
- [ ] 3.2 新增 `TransferActivitiesImpl`，注入 `AccountClientRouter` 和 `TransferOrderRepository`
- [ ] 3.3 实现 `freeze` Activity：调用 Feign → 结果写入 `transfer_order`（独立 `@Transactional`，Feign 调用在事务外）
- [ ] 3.4 实现 `confirmDebit` Activity：同上模式
- [ ] 3.5 实现 `credit` Activity：同上模式；失败时更新状态为 `CREDIT_FAILED` 并抛出异常（不反向补偿）
- [ ] 3.6 实现 `cancelFreeze` Activity：同上模式；成功后更新状态为 `REJECTED`
- [ ] 3.7 为每个 Activity 编写单元测试（mock Feign client，验证 DB 状态更新和异常抛出行为）

## 4. Temporal Workflow 实现（transfer-service）

- [ ] 4.1 新增 `TransferWorkflow` 接口，声明 `@WorkflowMethod execute`、`@SignalMethod review`
- [ ] 4.2 新增 `TransferWorkflowImpl`，配置 `ActivityOptions`（startToCloseTimeout=30s，RetryPolicy：初始 2s，退避系数 2，最大间隔 5min，最大尝试 10 次）
- [ ] 4.3 实现 `execute` 方法主流程：freeze → MANUAL_REVIEW 分支等待 Signal / AUTO_WITHDRAW 直接继续 → confirmDebit → credit
- [ ] 4.4 实现 `review` Signal 方法：存储 `ReviewDecision`，唤醒 `Workflow.await`
- [ ] 4.5 MANUAL_REVIEW 驳回分支：收到 Signal `approved=false` 后调用 `cancelFreeze` Activity
- [ ] 4.6 使用 `TestWorkflowEnvironment` 编写 Workflow 单元测试：AUTO_WITHDRAW 成功、MANUAL_REVIEW 审核通过、MANUAL_REVIEW 审核驳回、Activity 失败自动重试

## 5. Temporal Worker 注册（transfer-service）

- [ ] 5.1 新增 Temporal Worker Spring Bean 配置类，注册 `TransferWorkflowImpl` 和 `TransferActivitiesImpl` 到 Task Queue `transfer-queue`
- [ ] 5.2 配置 `application.yml`：新增 Temporal Server 地址、namespace、Task Queue 配置项（支持环境变量覆盖）
- [ ] 5.3 本地启动 transfer-service，验证 Worker 成功连接 Temporal Server（Temporal UI 中可见 Worker 注册）

## 6. Controller 改造（transfer-service）

- [ ] 6.1 修改 `TransferController.create`：从调用 `sagaService.createTransfer` 改为先持久化 `transfer_order(CREATED)`，再通过 `WorkflowClient` 启动 Temporal Workflow，返回 `transferId`
- [ ] 6.2 修改 `TransferController.review`：从调用 `sagaService.review` 改为通过 `WorkflowClient` 向对应 Workflow 发送 `review` Signal
- [ ] 6.3 移除 `TransferController` 中的 `withdrawResult` 和 `retry` 接口方法，返回 404
- [ ] 6.4 验证 `GET /transfers/{id}` 无需修改（仍查 `transfer_order` 表）

## 7. 清理旧代码

- [ ] 7.1 删除 `TransferSagaService`
- [ ] 7.2 删除 `TransferRetryService`
- [ ] 7.3 删除 `TransferRetryScheduler`
- [ ] 7.4 精简 `TransferStatus` 枚举：移除 `FREEZE_FAILED`、`DEBIT_FAILED`、`CREDIT_FAILED`、`CANCEL_FAILED`、`WITHDRAW_PENDING`、`WITHDRAW_FAILED`，保留 `CREATED`、`FROZEN`（新增，替代原冻结成功中间状态）、`WAIT_REVIEW`、`DEBIT_SUCCESS`、`REJECTED`、`SUCCESS`
- [ ] 7.5 更新 `transfer_order` 表的 status 枚举列注释（如有 DDL 注释）
- [ ] 7.6 检查并移除不再使用的 import 和 bean 注入

## 8. 测试迁移与回归验证

- [ ] 8.1 将 `TransferSagaServiceTest` 重写为基于 `TestWorkflowEnvironment` 的 Workflow 测试
- [ ] 8.2 将 `TransferRetryServiceTest` 的重试场景合并入 Workflow 测试（验证 Activity 失败后重试行为）
- [ ] 8.3 将 `TransferScenarioIntegrationTest` 迁移为 Temporal 测试环境下的端到端场景测试
- [ ] 8.4 运行全量测试 `mvn -q test -DfailIfNoTests=false`，确认全部通过
- [ ] 8.5 端对端手动验证：本地启动三服务 + Temporal，执行 AUTO_WITHDRAW 和 MANUAL_REVIEW 场景，在 Temporal UI 中确认工作流执行历史符合预期

## 9. 文档更新

- [ ] 9.1 更新 `README.md`：新增 Temporal Server 启动步骤，更新示例请求（移除 retry / withdraw-result 接口）
- [ ] 9.2 更新 `docs/design/service-implementation-overview.md`：反映新架构（Workflow 替代状态机，状态数量精简）
- [ ] 9.3 更新 `docs/design/README.md` 设计文档索引
