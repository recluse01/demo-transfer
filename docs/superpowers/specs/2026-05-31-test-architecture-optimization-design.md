# 测试架构优化设计

- 日期：2026-05-31
- 状态：已确认，待生成实施计划
- 分支：`claude/v1-test`
- 范围：测试基础设施 + CI，**不改动生产代码**

## 1. 背景与目标

测试体系升级（见 [2026-05-30 测试体系升级设计](2026-05-30-test-suite-best-practices-design.md)）已落地双轨测试（H2 快速轨 + Testcontainers 保真轨）、WireMock、JaCoCo 门禁。本次针对**已落地架构**做工程化优化，让它更可持续、可协作、好维护、跑得快，但坚持 CLAUDE.md 的「简单优先、非必要不引入复杂度」。

经分析，本次推进三个用户选定方向的并集，共 5 个工作项；它们全是测试基础设施/CI 改动，互相独立，适合一个 spec。

## 2. 核心决策（已与用户确认）

| 决策点 | 结论 |
| --- | --- |
| 推进方向 | CI 闭环 + 测试工程化清理 + 本地提速（三者并集） |
| 基类去重 | **不做**——两份 `AbstractMySqlIntegrationTest` 仅差一个 `TARGET_DATABASE` 常量，引入 `common` test-jar 属过度工程；保留现状（符合简单优先） |
| `application-test.yml` 去重 | **不做**——两份相同但极小，保留按模块各一份 |
| CI 平台 | GitHub Actions（远程为 `github.com/recluse01/demo-transfer`） |
| CI 结构 | **单 job** 跑 `mvn verify`（覆盖双轨 + 门禁），非 2-job |
| OpenSpec 边界 | 先将已完成的 `test-suite-best-practices` 归档，或为本次优化新建独立 change（建议 `optimize-test-architecture-ci`），避免与已完成的测试体系升级混在同一变更里 |
| 第三梯队（PIT/并行/Pact 等） | **不做**——对基础示例属过度工程 |

## 3. 工作项设计

### 3.1 CI 闭环（GitHub Actions）

新增 `.github/workflows/ci.yml`：

- **触发**：push 到分支 + pull_request。
- **单 job**（`ubuntu-latest`，自带 Docker，Testcontainers 开箱即用）：
  1. `actions/checkout`
  2. `actions/setup-java`（**Temurin 8**）+ Maven 依赖缓存
  3. `mvn -B verify` —— 一条命令覆盖快速轨(surefire `*Test`) + 保真轨(failsafe `*IT`/Testcontainers) + JaCoCo 合并门禁
- **收尾**：用 `actions/upload-artifact` 上传 `**/target/site/jacoco/` 覆盖率报告，便于查看。
- **理由**：门禁不在 CI 自动跑等于只有一半；单 job 最简单、最贴合「让门禁在每次 push/PR 自动兑现」的目标。

### 3.2 测试日志噪音治理

`account-service` 与 `transfer-service` 各新增 `src/test/resources/logback-test.xml`，将 `org.testcontainers`、`com.github.dockerjava`、`tc` 等 logger 设为 `WARN`。

- **问题**：`MySQLContainer` 在 Spring 上下文加载前的 static 块就启动，`application-test.yml` 的日志级别管不到它，导致 IT 输出被 docker-java DEBUG 刷屏（实测约 95KB）。
- **效果**：本地与 CI 的 IT 日志显著清爽，便于排障。
- **边界**：只压制 Docker/Testcontainers 的 DEBUG 噪音，保留应用包、Spring 关键启动信息和 WARN/ERROR；验收目标是「无 docker-java DEBUG 刷屏」，不是让测试输出完全静默。

### 3.3 保真 IT 隔离硬化

- **范围澄清**：两个模块的冒烟 IT 隔离性并不相同，本工作项**只改 `transfer-service` 侧**：
  - `transfer-service` 的 `MySqlContainerSmokeIT` 断言 `orderRepository.count() == 0`，依赖「全局空表」，与 `TransferScenarioIT` 的 `@AfterEach deleteAll` 存在隐性顺序耦合，将来漏清理或开并行即脆 —— **需要硬化**。
  - `account-service` 的 `MySqlContainerSmokeIT` 查的是 DDL 播种的种子行（`findByUserIdAndAssetCode("user-1","USDT")`），本就不依赖「空表」，隔离上没有同类耦合 —— **不改代码，仅纳入下方审计确认**。
- **改法（仅 transfer 侧）**：冒烟 IT 改为以「按一个不存在的唯一键查询返回空 + 表可计数（不抛异常）」证明「schema 存在、可查询」，不再依赖全局空表。
- **审计**：确认所有**写库**的 IT 使用唯一业务键或事务回滚，不污染共享单例容器（account 侧写库用例及其种子查询用 `@Transactional` 回滚或唯一键，确认即可）。
- **验收重点**：transfer 侧 `MySqlContainerSmokeIT` 单独运行、在全量 `mvn verify` 中运行、以及在其他写库 IT 之后运行都不依赖表为空。

### 3.4 WireMock/JSON 夹具去重

- **问题**：`FeignClientWireMockIT` 多处手拼 `ApiResponse<AssetOperationResponse>` JSON 字符串，缺少 `TransferScenarioIT` 已有的 `successBody(...)` 之类辅助，重复且易因拼错导致反序列化失败。
- **改法**：优先在 `FeignClientWireMockIT` 类内抽取响应体 helper；只有当 `TransferScenarioIT` 与 `FeignClientWireMockIT` 都能明显减少重复时，才提升为 transfer-service 测试源码内的小工具类。坚持**模块内**、不跨模块、不引入新依赖。
- **helper 覆盖范围**：`TransferScenarioIT` 现有的 `successBody(operationType)` 只产 `success:true` 的标准 `ApiResponse` 体；`FeignClientWireMockIT` 还需要一类**业务失败体**（`success:false`、`data:null`），因此 helper 应覆盖「成功体 + 失败体」两种合法 `ApiResponse`。
- **边界**：不为了抽象而统一所有 WireMock stub。两类**故意非标准**的桩——纯文本 5xx 体（如 `"Service Unavailable"`）与 `{"error":...}`——是用来模拟反序列化失败 / 非 `ApiResponse` 响应的，**必须保持手写、不进 helper**，否则会抹掉测试意图。保留场景测试中能直接表达业务流程的局部 stub，避免测试可读性下降。

### 3.5 容器复用提速

- 两个保真基类的 `MySQLContainer` 链式加 `.withReuse(true)`。
- 开发者本地在 `~/.testcontainers.properties` 设 `testcontainers.reuse.enable=true`（**opt-in**）后，容器跨多次本地运行复用，省去每次约 10s 启动。
- **CI 零影响**：CI 不设该属性 → 仍是全新容器 + Ryuk 自动清理，行为与现在一致、无风险。
- **正确性边界**：容器复用只能是本地提速手段，不能成为测试正确性的前提。开启复用后，数据库状态可能跨 JVM 残留，且已存在的复用容器不会重新执行 `/docker-entrypoint-initdb.d` 初始化脚本；因此所有写库 IT 必须继续依赖事务回滚、唯一业务键或显式清理。
- 在测试策略文档说明本地开启方式、适用场景，以及遇到脏数据/DDL 漂移时删除 reusable container 后重跑的处理方式。

## 4. 验证标准

- `mvn test`（模拟无 Docker）全绿；`mvn verify`（Docker 在位）全绿、JaCoCo 门禁通过——行为相对现状不退化（纯增量 + 加固）。
- 推送验证分支后，GitHub Actions 上 `ci.yml` 跑通 `mvn verify`、门禁生效、JaCoCo artifact 成功上传。
- 抽查 IT 日志：无 docker-java DEBUG 刷屏，同时 WARN/ERROR 与关键失败上下文仍可见。
- `MySqlContainerSmokeIT` 不再依赖全局空表，单独运行、全量运行、置于写库 IT 之后运行均通过。
- 本地开启 Testcontainers reuse 后，写库 IT 仍不依赖残留状态；文档明确脏容器清理方式。

## 5. 交付物

- `.github/workflows/ci.yml`
- `account-service`、`transfer-service` 各一份 `src/test/resources/logback-test.xml`
- 改动：`transfer-service` 的 `MySqlContainerSmokeIT`（隔离硬化；account 侧仅审计确认、不改）、两个保真基类（`withReuse`）、`FeignClientWireMockIT`（夹具去重）
- 更新 `docs/design/testing-strategy.md`：补「CI」与「容器复用本地开启方式」两小节
- OpenSpec 交付：本优化使用独立 change 承载；若 `test-suite-best-practices` 已确认完成，应先归档再开始本变更，保持变更边界清晰。
- 按阶段提交（CI / 日志 / 隔离 / 夹具 / 复用 / 文档），中文 Conventional Commits，提交在 `claude/v1-test`

## 6. 不做（YAGNI）

- 基类去重（test-jar）、`application-test.yml` 去重。
- 变异测试（PIT）、JUnit 并行执行、Pact 契约测试、跨 IT 的 Spring 上下文缓存调优。
- 任何生产代码改动。
