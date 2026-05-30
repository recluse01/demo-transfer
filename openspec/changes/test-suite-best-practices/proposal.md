## Why

现有测试栈虽正（JUnit 5 + AssertJ + Mockito），但存在四类缺口：Web/Controller 层零测试、Feign 客户端层零测试、重试调度器未覆盖、仅用 H2 导致与真实 MySQL 的 `DECIMAL(32,8)`、DDL、唯一索引、SQL 方言存在保真盲区，且无覆盖率度量。跨账户划转是金额敏感、最终一致的核心链路，测试缺口直接威胁资金正确性。

## What Changes

- 确立四层测试金字塔（单元 / 持久层切片 / Web 切片 / 保真集成）与命名约定。
- **双轨数据层**：保留 H2 快速轨（`*Test`，surefire，无需 Docker）；新增 Testcontainers 真实 MySQL 保真轨（`*IT`，failsafe，需 Docker），保真轨容器复用真实 DDL 脚本初始化。
- 补齐缺失层次：`@WebMvcTest` 覆盖 Controller、WireMock 覆盖 Feign 跨服务调用、`TransferRetryScheduler` 调度测试、`AccountClientRouter` 路由单测。
- 强化资金关键断言：幂等唯一索引（`transfer_id + operation_type` 返回 `applied=false`）、`DECIMAL(32,8)` 精度、`CREDIT_FAILED` 持续重试收敛（不反向补偿）。
- 引入 JaCoCo 覆盖率门禁：整体行覆盖 ≥70%、核心业务包 ≥80%，构建强制；排除 DTO/config/启动类。
- 新增测试策略文档并登记进 `CLAUDE.md` 文档索引。

## Capabilities

### New Capabilities
- `automated-testing`: 项目自动化测试体系的契约——分层金字塔、双轨数据层（H2 快速轨 + Testcontainers 保真轨）、Web/Feign/调度测试约定、资金关键不变量验证、JaCoCo 覆盖率门禁。

### Modified Capabilities
<!-- 无既有 spec，无需求级修改 -->

## Impact

- **构建**：根 `pom.xml` 引入 `testcontainers-bom` 与 `maven-failsafe-plugin`、`jacoco-maven-plugin`；各服务 `pom.xml` 保留 `h2`、新增 `testcontainers:mysql`/`junit-jupiter`，transfer-service 加 `wiremock-jre8-standalone`（均 test scope）。
- **测试代码**：`account-service`、`transfer-service` 现有测试拆分双轨；新增 Controller/Feign/Scheduler/Router 测试与共享基类 `AbstractMySqlIntegrationTest`。
- **CI/本地**：`mvn test` 无 Docker 可跑；`mvn verify` 需 Docker（跑保真轨 + 门禁）。
- **依赖约束**：仅限 test scope 依赖，不引入 Seata/MQ/Spring Cloud Contract/Pact 等生产或重型框架。
- **文档**：新增 `docs/design/testing-strategy.md`，更新 `CLAUDE.md` 文档索引。
