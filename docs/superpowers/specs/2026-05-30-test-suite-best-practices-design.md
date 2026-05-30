# 测试体系升级设计（行业最佳实践）

- 日期：2026-05-30
- 状态：已确认，待生成实施计划
- 范围：`common` / `account-service` / `account-a-service` / `account-b-service` / `transfer-service` 全模块测试

## 1. 目标与背景

现有测试栈已正（JUnit 5 + AssertJ + Mockito），但存在四类缺口：

1. **Web/Controller 层零测试**——无 MockMvc / `@WebMvcTest`。
2. **Feign 客户端层零测试**——`AccountAClient/BClient`、`AccountClientRouter` 无契约/调用验证。
3. **重试调度器** `TransferRetryScheduler` 未覆盖。
4. **环境保真度不足**——仅用 H2 内存库，与真实 MySQL 的 `DECIMAL(32,8)`、DDL、唯一索引、SQL 方言存在盲区；无覆盖率度量。

本次为**全面升级**：补齐分层 + 引入真实 MySQL 保真层 + JaCoCo 门禁 + 测试约定文档。

## 2. 核心决策（已与用户确认）

| 决策点 | 结论 |
| --- | --- |
| 升级范围 | 全面升级（补层 + Testcontainers + JaCoCo + 文档） |
| 数据层策略 | **双轨**：H2 快速轨保留为「无 Docker 降级路径」；Testcontainers 真实 MySQL 作为保真轨 |
| Feign/跨服务 | **WireMock** 在 transfer-service 内模拟 A/B 的 HTTP，不真起三服务 |
| 覆盖率门禁 | 适中：整体行覆盖 ≥70%，核心业务包 ≥80%，构建强制 |
| JDK / 框架约束 | JDK 8；Testcontainers 1.19.x（兼容 JDK8）；Spring Boot 2.7.18（无 `@ServiceConnection`） |

## 3. 测试金字塔与分层约定

| 层 | 注解/技术 | 跑在哪 | Maven 阶段 / 后缀 | 验证什么 |
| --- | --- | --- | --- | --- |
| **单元测试** | 纯 JUnit5 + Mockito，无 Spring | JVM | surefire `test` / `*Test` | 纯逻辑：状态机流转、`AccountClientRouter` 路由、金额计算 |
| **持久层切片（快）** | `@DataJpaTest`（默认 H2） | 内存 | surefire `test` / `*Test` | Repository 查询、派生方法、基本约束 |
| **Web 切片** | `@WebMvcTest` + MockMvc | 切片上下文 | surefire `test` / `*Test` | 入参校验、HTTP 状态码、`ApiResponse` 序列化、异常映射 |
| **保真集成（慢）** | `@SpringBootTest` / `@DataJpaTest(replace=NONE)` + Testcontainers MySQL + WireMock | 真实 MySQL 容器 | failsafe `verify` / `*IT` | `DECIMAL(32,8)` 精度、幂等唯一索引、并发扣减、Feign 真实收发、端到端编排与重试收敛 |

### 双轨工作机制

- **快速轨**：`mvn test` 只跑 `*Test`（surefire），全部基于 H2 / Mockito，**无需 Docker**，开发机随时可跑、秒级。
- **保真轨**：`mvn verify` 额外跑 `*IT`（maven-failsafe-plugin），基于 Testcontainers 真实 MySQL，**需要 Docker**。
- CI 在 `verify` 阶段跑全部；本地无 Docker 时 `mvn test` 仍全绿。
- 一条规则：**任何「H2 与 MySQL 行为可能不一致」或「跨 HTTP」的断言，归入 `*IT` 保真轨**；其余归快速轨。

## 4. 模块与基础设施改动

- **根 `pom.xml`**
  - `dependencyManagement` 引入 `org.testcontainers:testcontainers-bom:1.19.x`；统一管理 WireMock 版本。
  - 配置 `maven-failsafe-plugin`（绑定 `integration-test` / `verify`，匹配 `*IT`），与现有 `maven-surefire-plugin`（匹配 `*Test`）分工。
- **各服务 `pom.xml`**
  - **保留** `h2`（test scope）作为快速轨。
  - 新增 `org.testcontainers:mysql`、`org.testcontainers:junit-jupiter`（test scope）。
  - transfer-service 另加 `wiremock-jre8-standalone`（test scope，JDK8 兼容版）。
- **共享测试基类** `AbstractMySqlIntegrationTest`（各模块 `src/test/java`，供 `*IT` 继承）
  - 单例容器模式：`static MySQLContainer` 只启动一次，跨测试类复用，显著提速。
  - `@DynamicPropertySource` 注入连接串（Spring Boot 2.7 无 `@ServiceConnection`，手动桥接）。
  - 容器用**真实 DDL** 初始化（`docker/mysql/init/01-demo-transfer.sql`，单一事实来源，**不复制进 test resources**，避免漂移），确保看到与生产一致的 `DECIMAL(32,8)`、索引、约束。
- **测试资源**
  - `src/test/resources/application-test.yml`：保真轨 `ddl-auto: none`（schema 由 DDL 脚本建），降日志噪音；快速轨保持 H2 默认 `create-drop`。

## 5. 要新增/重构的测试清单

### account-service
- 重构 `AccountAssetServiceTest`、`AccountOperationIntegrationTest`、`AccountRepositoryTest`：拆分为快速轨（H2，保留）+ 保真轨 `*IT`（关键精度/幂等用例）。
- 新增 `AccountAssetControllerTest`（`@WebMvcTest`）：`freeze` / `confirm-debit` / `cancel-freeze` / `credit` 四端点的校验失败、成功、`ApiResponse` 结构。
- 新增 `AccountAssetIdempotencyIT`：在真实唯一索引（`transfer_id + operation_type`）下验证重复请求返回 `applied=false`；`DECIMAL(32,8)` 精度边界。

### transfer-service
- 重构 `TransferSagaServiceTest`、`TransferRetryServiceTest`、`TransferRepositoryTest`：状态机/编排纯逻辑留快速轨；落库与重试收敛断言归 `*IT`。
- 新增 `TransferControllerTest`（`@WebMvcTest`）：`create` / `review` / `withdraw-result` / `retry` / `get` 五端点。
- 新增 `AccountClientRouterTest`（纯单元）：`TransferDirection` ↔ 源/目标 `AccountType` ↔ `AccountOperationsClient` 路由全分支。
- 新增 `FeignClientWireMockIT`：WireMock 桩 A/B 服务，验证 Feign 序列化/反序列化/错误解码/超时。
- 新增 `TransferScenarioIT`（`@SpringBootTest` + WireMock + Testcontainers）：完整编排——正常完成、人工审核通过/拒绝、**`CREDIT_FAILED` 持续重试收敛（核心原则：不反向补偿）**。
- 新增 `TransferRetrySchedulerTest`：验证 `@Scheduled` 方法扫描失败单并触发重试（直接调用方法 + mock，不依赖真实定时）。

### account-a-service / account-b-service
- 现有 App 上下文冒烟测试保留；如依赖外部库连接，归 `*IT` 或用 `@MockBean` 隔离，确保快速轨可跑。

## 6. JaCoCo 覆盖率门禁

- `jacoco-maven-plugin`：`prepare-agent`（unit）+ `prepare-agent-integration`（IT）+ 合并 `report` + `check`，绑定 `verify`。
- **阈值**：整体行覆盖 ≥70%；核心业务包（`**/service/**`、状态机相关）行覆盖 ≥80%。
- **排除**：`**/common/**` DTO、`**/config/**`、`**/*Application*`、OpenApi 配置（无逻辑、拉低分母）。
- 门禁失败即 `mvn verify` 红。

## 7. 验证标准与交付物

- `mvn test` 无 Docker 全绿（快速轨）。
- `mvn verify` 在 Docker 在位时全绿，含 JaCoCo 门禁通过（保真轨 + 门禁）。
- 每个核心生产类至少一条「能复现其失败」的针对性用例（幂等、精度、不补偿原则、路由分支）。
- 新增 `docs/design/testing-strategy.md`（或并入测试章节）：金字塔分层、`*Test`/`*IT` 约定、双轨如何跑、Docker 前置；并在 `CLAUDE.md` 文档索引登记。
- 提交按阶段拆分（基础设施 pom/基类 → 各层测试 → JaCoCo → 文档），遵循中文 Conventional Commits。

## 8. 不做（YAGNI）

- 不引入 Spring Cloud Contract、Seata、消息队列、对账中心。
- 不做真起三服务的跨进程 E2E（用 WireMock 替代）。
- 不做契约消费者驱动测试（Pact）等重型框架。
