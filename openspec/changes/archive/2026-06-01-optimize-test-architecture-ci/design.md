## Context

测试体系升级已落地双轨测试（H2 快速轨 + Testcontainers 保真轨）、WireMock、JaCoCo 门禁（见已归档的 `test-suite-best-practices` 与设计文档 `docs/superpowers/specs/2026-05-31-test-architecture-optimization-design.md`）。本次对已落地架构做工程化加固，约束是 CLAUDE.md 的「简单优先、非必要不引入复杂度」：JDK 8、Spring Boot 2.7.18、Maven 多模块，远程为 `github.com/recluse01/demo-transfer`。本设计的单一事实来源是上述设计文档，已与用户确认并修正了 3.3/3.4/交付物三处精度问题。

## Goals / Non-Goals

**Goals:**
- 让 JaCoCo 门禁在每次 push/PR 自动兑现（CI 闭环）。
- 去除保真冒烟 IT 对「全局空表」的隐性顺序耦合，使其更健壮、可并行。
- 本地容器复用提速，且不削弱测试正确性。
- 治理 IT 日志噪音、去重 WireMock 夹具，提升可维护性。
- 行为相对现状不退化（纯增量 + 加固）。

**Non-Goals:**
- 基类去重（test-jar）、`application-test.yml` 去重——两份仅差一个常量/极小，引入共享属过度工程。
- 变异测试（PIT）、JUnit 并行执行、Pact 契约测试、Spring 上下文缓存调优。
- 任何生产代码改动；不新增运行时依赖。

## Decisions

### 决策 1：CI 用单 job 跑 `mvn -B verify`，而非 2-job
`ubuntu-latest` runner 自带 Docker，Testcontainers 开箱即用，一条 `mvn -B verify` 即覆盖快速轨 + 保真轨 + 门禁。
- **为什么不拆 2-job**（快速轨/保真轨分离）：拆分需在 job 间传递构建产物、重复配置缓存，收益（更细粒度并行）对一个基础示例不值得；单 job 最简单、最贴合「门禁每次自动兑现」的目标。
- 用 `actions/setup-java` 装 **Temurin 8**（对齐编译目标 `1.8`）+ Maven 依赖缓存；收尾用 `actions/upload-artifact` 上传 `**/target/site/jacoco/`。

### 决策 2：隔离硬化只改 transfer 侧 SmokeIT
- `transfer-service` 的 `MySqlContainerSmokeIT` 当前断言 `orderRepository.count()==0`，依赖全局空表，与 `TransferScenarioIT` 的 `@AfterEach deleteAll` 隐性顺序耦合 → 改为「按不存在唯一键查空 + `count()` 不抛异常」，证明 schema 可查询而不依赖表为空。
- `account-service` 的 `MySqlContainerSmokeIT` 查的是 DDL 播种的种子行（`user-1/USDT`），本就不依赖空表 → **不改代码**，仅纳入审计确认。
- **为什么不统一两侧**：account 侧用种子数据查询已是良好隔离，强行对齐反而降低其表达力。

### 决策 3：`withReuse(true)` + 本地 opt-in，CI 零影响
两个保真基类的 `MySQLContainer` 链式加 `.withReuse(true)`。复用只在开发者本地 `~/.testcontainers.properties` 设 `testcontainers.reuse.enable=true` 后生效；CI 不设该属性 → 仍全新容器 + Ryuk 清理。
- **正确性边界**：复用容器不会重新执行 `/docker-entrypoint-initdb.d`，且数据可能跨 JVM 残留，故所有写库 IT 必须继续靠事务回滚/唯一键/显式清理。这正是 spec 新增的「保真轨隔离不变量」要保障的。

### 决策 4：日志治理用 `logback-test.xml` 而非 `application-test.yml`
`MySQLContainer` 在 Spring 上下文加载前的 static 块即启动，`application-test.yml` 的日志级别管不到它。各模块 `src/test/resources/logback-test.xml` 把 `org.testcontainers`、`com.github.dockerjava`、`tc` 等 logger 设为 `WARN`，保留应用包/Spring 关键启动信息与 WARN/ERROR。验收目标是「无 docker-java DEBUG 刷屏」，非完全静默。

### 决策 5：WireMock 夹具去重，模块内、不过度抽象
优先在 `FeignClientWireMockIT` 类内抽取响应体 helper，覆盖「成功体 + 业务失败体（`success:false`/`data:null`）」两种合法 `ApiResponse`。只有当 `TransferScenarioIT` 与 `FeignClientWireMockIT` 都能明显减少重复时，才提升为 transfer-service 测试源码内的小工具类——**模块内、不跨模块、不引入新依赖**。
- 模拟反序列化失败的非标准体（纯文本 `"Service Unavailable"`、`{"error":...}`）**保持手写、不进 helper**，否则抹掉测试意图。

## Risks / Trade-offs

- [CI 上 `mvn verify` 因 Testcontainers 拉镜像偏慢] → 接受；Maven 依赖缓存 + runner 自带 Docker 已是最简方案，不引入镜像缓存复杂度。
- [`withReuse(true)` 在不支持 reuse 的环境打 WARN 日志] → 与决策 4 的日志治理协同，且属 WARN 级、不刷屏，可接受。
- [本地开启 reuse 后出现脏数据/DDL 漂移] → 在 `testing-strategy.md` 说明「删除 reusable container 后重跑」的处理方式；隔离不变量保证正常用例不受影响。
- [夹具去重过度抽象降低可读性] → 决策 5 明确边界：保留能直接表达业务流程的局部 stub，非标准错误体不统一。

## Migration Plan

按阶段提交（中文 Conventional Commits，分支 `claude/v1-test`）：CI → 日志 → 隔离 → 夹具 → 复用 → 文档。每阶段独立、互不依赖，可单独回滚。验证：本地 `mvn test`（模拟无 Docker，仅 `*Test`）与 `mvn verify`（Docker 在位）全绿；推送后 GitHub Actions 跑通 `mvn verify`、门禁生效、JaCoCo artifact 上传成功。

## Open Questions

- 无。设计文档已确认，5 个工作项边界清晰。
