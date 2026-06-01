## Why

测试体系升级（已归档 `test-suite-best-practices`）落地了双轨测试 + WireMock + JaCoCo 门禁，但门禁只在本地 `mvn verify` 兑现——没有 CI 自动执行就只有一半价值。同时已落地架构存在几处可持续性隐患：保真冒烟 IT 依赖「全局空表」的隐性顺序耦合、IT 日志被 docker-java DEBUG 刷屏、WireMock 夹具手拼 JSON 重复易错、容器每次重启慢。本次在不改任何生产代码的前提下做工程化加固。

## What Changes

- 新增 GitHub Actions CI（`.github/workflows/ci.yml`）：push 与 pull_request 触发，单 job 跑 `mvn -B verify`，覆盖快速轨 + 保真轨 + JaCoCo 合并门禁，并上传覆盖率报告 artifact。
- 保真 IT 隔离硬化：`transfer-service` 的 `MySqlContainerSmokeIT` 不再断言 `count()==0`，改为「唯一键查空 + 表可计数」证明 schema 可查询，去除与 `TransferScenarioIT` 清理顺序的隐性耦合（`account-service` 侧查种子数据、本就隔离，仅审计确认不改代码）。
- 容器复用本地提速：两个保真基类 `MySQLContainer` 加 `.withReuse(true)`，开发者本地 opt-in 后跨运行复用容器；CI 不开启该属性、行为零变化。
- 测试日志噪音治理：`account-service`、`transfer-service` 各新增 `logback-test.xml`，将 testcontainers/docker-java 等 logger 降到 `WARN`（实现细节，不改 spec 行为）。
- WireMock/JSON 夹具去重：`FeignClientWireMockIT` 抽取 success/failure 响应体 helper，模拟反序列化失败的非标准体保持手写（实现细节，不改 spec 行为）。
- 文档：`docs/design/testing-strategy.md` 补「CI」与「容器复用本地开启方式」两小节。

非目标（YAGNI）：基类去重 / `application-test.yml` 去重、变异测试（PIT）、JUnit 并行执行、Pact 契约测试、Spring 上下文缓存调优、任何生产代码改动。

## Capabilities

### New Capabilities
- `continuous-integration`: 在每次 push 与 PR 上自动执行双轨测试与 JaCoCo 覆盖率门禁，并产出可查看的覆盖率报告 artifact，使门禁持续兑现而非仅本地可选。

### Modified Capabilities
- `automated-testing`: 强化保真轨的隔离与本地复用要求——保真 IT 不得依赖共享单例容器的全局状态（空表）来通过；写库 IT 必须靠唯一业务键、事务回滚或显式清理保证隔离；容器复用仅为本地 opt-in 提速手段，不得成为测试正确性的前提。

## Impact

- 新增：`.github/workflows/ci.yml`、两份 `src/test/resources/logback-test.xml`。
- 改动（仅测试代码与基类）：`transfer-service/.../MySqlContainerSmokeIT`、两个 `AbstractMySqlIntegrationTest`（`withReuse`）、`FeignClientWireMockIT`（夹具去重）。
- 文档：`docs/design/testing-strategy.md`。
- 不触及任何生产代码、不新增运行时依赖、不改 Maven 构建插件配置（除非门禁/失败安全行为需要，原则上零改动）。
- 风险面低：纯增量 + 加固，验证标准是「相对现状不退化」。
