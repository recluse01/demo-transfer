## Context

跨账户划转工程（Spring Boot 2.7.18 + Feign + 编排式 Saga，JDK 8，MySQL 8）现有测试基于 JUnit 5 + AssertJ + Mockito，约 9 个测试类、36 个用例，数据层仅用 H2 内存库。资金链路最终一致、对金额精度与幂等高度敏感，但 Web 层、Feign 层、重试调度器无测试，且 H2 与真实 MySQL 存在方言/精度/约束盲区。

约束（来自 `CLAUDE.md`）：JDK 8、金额用 `BigDecimal`/`DECIMAL(32,8)`、非必要不引入新框架（仅允许 test scope）、中文注释、按阶段提交。完整背景见 `docs/superpowers/specs/2026-05-30-test-suite-best-practices-design.md`。

## Goals / Non-Goals

**Goals:**
- 建立四层测试金字塔与 `*Test` / `*IT` 命名约定。
- 双轨数据层：H2 快速轨（无 Docker 可跑）+ Testcontainers 真实 MySQL 保真轨。
- 补齐 Web（MockMvc）、Feign（WireMock）、调度器、路由测试。
- 验证资金关键不变量：幂等、`DECIMAL(32,8)` 精度、`CREDIT_FAILED` 重试收敛不补偿。
- JaCoCo 适中门禁（整体 ≥70%，核心包 ≥80%）。

**Non-Goals:**
- 不真起三服务跨进程 E2E（用 WireMock 替代）。
- 不引入 Spring Cloud Contract / Pact / Seata / MQ / 对账中心。
- 不改动生产代码行为（除非测试暴露缺陷，另行讨论）。

## Decisions

**D1 · 双轨而非单轨（H2 vs Testcontainers）。**
用户要求保留无 Docker 降级路径，故采用 surefire(`*Test`,H2) + failsafe(`*IT`,Testcontainers) 双轨。准绳：任何「H2 与 MySQL 行为可能不一致」或「跨 HTTP」的断言归 `*IT`；其余归快速轨。
- 备选：全 Testcontainers（最高保真但强依赖 Docker，开发机 `mvn install` 受阻）——否决，违背降级要求。
- 备选：纯 H2（零环境依赖但保留盲区）——否决，违背保真目标。

**D2 · 保真轨 schema 用真实 DDL 而非 Hibernate 生成。**
`AbstractMySqlIntegrationTest` 的单例容器挂载真实 `docker/mysql/init/01-demo-transfer.sql` 初始化，`ddl-auto: none`。这样测试看到的精度/索引/约束与生产一致，是消除 H2 盲区的关键。
- 备选：`ddl-auto: create-drop` 由 Hibernate 建表——否决，会掩盖真实 DDL 与实体映射的差异。

**D3 · Spring Boot 2.7 用 `@DynamicPropertySource` 桥接容器。**
2.7.18 无 SB3.1 的 `@ServiceConnection`，用静态单例 `MySQLContainer` + `@DynamicPropertySource` 注入连接串；单例避免每类重启容器。`@DataJpaTest` 保真用例加 `@AutoConfigureTestDatabase(replace = NONE)` 防止被换回 H2。

**D4 · Feign 用 WireMock 桩 HTTP。**
在 transfer-service 内用 `wiremock-jre8-standalone`（JDK8 兼容）模拟 A/B 服务真实 HTTP 响应，验证 Feign 序列化/反序列化/错误解码/超时；编排单测仍可 Mock 接口。

**D5 · JaCoCo 双轨合并 + 适中门禁。**
`prepare-agent` + `prepare-agent-integration` + 合并 `report` + `check` 绑定 `verify`；整体行 ≥70%、`**/service/**` ≥80%；排除 `**/common/**`、`**/config/**`、`**/*Application*`、OpenApi。

## Risks / Trade-offs

- [无 Docker 时 `mvn verify` 失败] → 文档明确 Docker 前置；`mvn test` 始终可跑作为降级；CI 保证 Docker 在位。
- [双轨用例重复/漂移] → 以「H2 不一致或跨 HTTP 才进 `*IT`」单一准绳划分，避免同一断言两边各写一份。
- [Testcontainers 拖慢构建] → 单例容器跨类复用，仅高价值用例进保真轨。
- [门禁逼出凑数测试] → 阈值适中（70/80），排除无逻辑类，聚焦核心包。
- [真实 DDL 多库（account_a/b/transfer）初始化复杂] → 保真用例连初始化脚本中对应单库即可，基类封装连接细节。

## Migration Plan

按阶段提交，每阶段可独立验证：
1. 基础设施：根/各服务 pom（failsafe、testcontainers、wiremock、jacoco）、`AbstractMySqlIntegrationTest`、`application-test.yml`。
2. 快速轨补层：`@WebMvcTest` Controller、`AccountClientRouter` 单测、`TransferRetryScheduler` 测试。
3. 保真轨：幂等/精度/重试收敛 `*IT`、Feign WireMock `IT`、端到端编排 `IT`。
4. 门禁与文档：JaCoCo 阈值、`docs/design/testing-strategy.md`、`CLAUDE.md` 索引登记。

回滚：纯增量（test scope + 文档 + 构建插件），任一阶段可单独 revert，不影响生产代码。

## Open Questions

- 无（关键决策已在 brainstorming 阶段确认）。
