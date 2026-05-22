## Context

`demo-transfer` 是一个基于 Spring Boot + Feign + MySQL 的跨账户划转服务，当前用手写编排式 Saga（`TransferSagaService`）管理 11 个状态的状态机。已识别三项核心缺陷：

- **F1**：Feign 调用嵌套在 `@Transactional` 内，DB 提交失败时产生孤立冻结资产
- **F2**：`AccountAssetService` 幂等检查在行锁前执行，并发场景依赖 DB 约束兜底产生误报
- **F3**：DB 连接在 Feign 网络 IO 期间持续占用，高并发下连接池成为瓶颈

技术栈：Spring Boot 2.7、Java 8、Spring Cloud OpenFeign、MySQL 8。

## Goals / Non-Goals

**Goals:**
- 用 Temporal Workflow 替换手写状态机和定时重试，消除 F1/F3
- 修正幂等检查顺序（F2），消除并发误报
- 保留 `transfer_order` 表作为查询投影，对下游系统（对账、报表）无感
- 现有 REST API 行为语义不变（`/transfers`、`/{id}/review`）
- 测试可用 `TestWorkflowEnvironment` 独立验证，无需真实 Temporal Server

**Non-Goals:**
- 不替换账户服务（account-a/b-service）的内部实现
- 不引入 CQRS / Event Sourcing
- 不实现链上提币
- 不做多租户或分片
- 不升级 Java 版本或 Spring Boot 版本

## Decisions

### D1：选择 Temporal 而非 Outbox 模式

**决策**：引入 Temporal 工作流引擎。

**备选方案**：
- *Outbox 模式*：仍用 MySQL，新增 `outbox_event` 表 + Relay 轮询。改动量中等，延迟+100~500ms，解决 F1/F3 但保留手写状态机。
- *MQ 驱动*：引入 RocketMQ/Kafka，运维成本高，与当前技术栈差异大。

**理由**：Temporal 将执行历史持久化，Worker 崩溃重启后工作流自动恢复，彻底消除孤立状态；内置指数退避重试替代手写调度器；Signal 机制优雅处理人工审核等待；编排代码降至约 50 行，可维护性大幅提升。

### D2：保留 transfer_order 表作为查询投影

**决策**：`transfer_order` 表继续存在，由 Activities 作为副作用更新状态字段。

**备选方案**：
- *完全依赖 Temporal Query API*：`GET /{id}` 直接查询 Temporal，不维护本地 DB 状态。

**理由**：保留本地查询表对对账、监控、报表系统无侵入；若 Temporal Server 暂时不可用，历史记录仍可查询；与当前 `GET /transfers/{id}` 接口语义完全一致。

### D3：Activity 粒度为单个账户操作

**决策**：每个 Activity 对应一个账户操作（`freeze`、`confirmDebit`、`credit`、`cancelFreeze`），不合并。

**理由**：单操作粒度与账户服务的幂等键（`transferId + operationType`）精确对应，重试安全性最高；Activity 失败信息在 Temporal UI 中清晰可见，便于排障。

### D4：Activity 内部使用独立短事务

**决策**：每个 Activity 方法内的 DB 操作（更新 `transfer_order` 状态）使用独立 `@Transactional`，Feign 调用不在事务内。

**理由**：直接解决 F1 和 F3——DB 连接仅在读写阶段持有，Feign 网络 IO 期间完全释放；即使 Feign 成功但 DB 更新失败，Temporal 会重试 Activity，幂等保证重试安全。

### D5：Temporal Server 后端使用独立 MySQL 库

**决策**：Temporal Server 使用 docker-compose 中现有 MySQL 实例的独立数据库（`temporal`、`temporal_visibility`），不与业务库混用。

**理由**：降低部署复杂度（复用已有 MySQL），同时保持业务库和 Temporal 元数据库逻辑隔离。

### D6：旧兼容接口随重构一并移除

**决策**：`POST /{id}/withdraw-result`（WITHDRAW_PENDING 兼容接口）和 `POST /{id}/retry`（手动重试）在本次重构中移除。

**理由**：Temporal 内置重试使手动重试接口失去意义；WITHDRAW_PENDING 是遗留状态，无新订单会进入该状态；延迟移除只会增加维护负担。这是 BREAKING 变更，已在 proposal 中标注。

## Risks / Trade-offs

- **[新基础设施依赖] Temporal Server 成为关键路径** → 使用 Docker healthcheck 保证启动顺序；生产环境使用 Temporal Cloud 或高可用集群；Temporal 本身对 MySQL 宕机有短暂容忍（历史缓存）
- **[工作流代码不可随意修改] Temporal 要求 Workflow 代码具备确定性** → Workflow 内禁止调用 `new Date()`、`Math.random()`、直接 IO；所有非确定性操作必须放在 Activity 中；新增工作流版本时使用 `Workflow.getVersion()` 做版本化迁移
- **[在途订单迁移] 重构上线时可能有处于 WAIT_REVIEW 的存量订单** → 迁移策略：保留旧 `TransferSagaService` 代码直至所有存量订单终结（feature flag 路由）；或手动将存量订单迁移至 Temporal 工作流
- **[Java 8 兼容性] Temporal Java SDK 1.25+ 支持 Java 8** → 已确认，无风险

## Migration Plan

1. **准备阶段**：新增 Temporal 依赖，配置 docker-compose，确认 Temporal Server 启动正常
2. **并行实现**：新增 Workflow + Activity 实现；旧 SagaService 暂时保留
3. **feature flag 切换**：通过配置项将新请求路由至 Temporal Workflow；存量 WAIT_REVIEW 订单仍走旧路径
4. **存量清零**：等所有存量订单达到终态（SUCCESS / REJECTED）
5. **清理**：删除旧 SagaService、RetryService、RetryScheduler；精简 TransferStatus 枚举

**回滚**：feature flag 切回旧路径；Temporal 工作流可通过 `terminateWorkflow` API 强制终止

## Open Questions

- Temporal Worker 是嵌入 `transfer-service` 进程还是独立部署？（当前推荐：嵌入，减少运维复杂度；若流量大再拆分）
- 生产环境是否采用 Temporal Cloud 替代自建 Temporal Server？（不影响代码，仅配置变更）
- `transfer_order` 的 `status` 字段在 Activity 失败重试期间是否需要体现"重试中"状态？（当前方案：不需要，查询方感知不到 Temporal 内部重试细节）
