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

### 3.3 保真 IT 隔离硬化

- **问题**：`transfer-service` 的 `MySqlContainerSmokeIT` 断言 `orderRepository.count() == 0`，依赖「全局空表」，与 `TransferScenarioIT` 的 `@AfterEach deleteAll` 存在隐性顺序耦合，将来漏清理或开并行即脆。
- **改法**：冒烟 IT 改为以「按一个不存在的唯一键查询返回空 + 表可计数（不抛异常）」证明「schema 存在、可查询」，不再依赖全局空表。
- **审计**：确认所有**写库**的 IT 使用唯一业务键或事务回滚，不污染共享单例容器（account 侧写库用例已用 `@Transactional` 回滚或唯一键，确认即可）。

### 3.4 WireMock/JSON 夹具去重

- **问题**：`FeignClientWireMockIT` 多处手拼 `ApiResponse<AssetOperationResponse>` JSON 字符串，缺少 `TransferScenarioIT` 已有的 `successBody(...)` 之类辅助，重复且易因拼错导致反序列化失败。
- **改法**：在 transfer-service 测试内抽取共享的 stub / 响应体辅助方法（**模块内**，不跨模块、不引入新依赖）。`TransferScenarioIT` 已有的 `successBody` 模式可作参照；评估是否值得提升为两个 IT 共用的小工具类（仅当真正减少重复时）。

### 3.5 容器复用提速

- 两个保真基类的 `MySQLContainer` 链式加 `.withReuse(true)`。
- 开发者本地在 `~/.testcontainers.properties` 设 `testcontainers.reuse.enable=true`（**opt-in**）后，容器跨多次本地运行复用，省去每次约 10s 启动。
- **CI 零影响**：CI 不设该属性 → 仍是全新容器 + Ryuk 自动清理，行为与现在一致、无风险。
- 在测试策略文档说明本地开启方式。

## 4. 验证标准

- `mvn test`（模拟无 Docker）全绿；`mvn verify`（Docker 在位）全绿、JaCoCo 门禁通过——行为相对现状不退化（纯增量 + 加固）。
- 推送验证分支后，GitHub Actions 上 `ci.yml` 跑通 `mvn verify`、门禁生效、JaCoCo artifact 成功上传。
- 抽查 IT 日志：无 docker-java DEBUG 刷屏。
- `MySqlContainerSmokeIT` 不再依赖全局空表，单独运行与全量运行均通过。

## 5. 交付物

- `.github/workflows/ci.yml`
- `account-service`、`transfer-service` 各一份 `src/test/resources/logback-test.xml`
- 改动：两个 `MySqlContainerSmokeIT`（隔离硬化）、两个保真基类（`withReuse`）、`FeignClientWireMockIT`（夹具去重）
- 更新 `docs/design/testing-strategy.md`：补「CI」与「容器复用本地开启方式」两小节
- 按阶段提交（CI / 日志 / 隔离 / 夹具 / 复用 / 文档），中文 Conventional Commits，提交在 `claude/v1-test`

## 6. 不做（YAGNI）

- 基类去重（test-jar）、`application-test.yml` 去重。
- 变异测试（PIT）、JUnit 并行执行、Pact 契约测试、跨 IT 的 Spring 上下文缓存调优。
- 任何生产代码改动。
