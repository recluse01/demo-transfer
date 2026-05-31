# 测试策略

本文档是面向实践者的使用指南：如何运行测试、如何判断该往哪个轨道加用例、如何新建各类测试。设计决策的背景与"为什么"见 [设计规格](../superpowers/specs/2026-05-30-test-suite-best-practices-design.md) 和 [openspec 变更](../../openspec/changes/test-suite-best-practices/)。

---

## 1. 四层金字塔 + 双轨命名

| 层 | 技术 / 注解 | Maven 插件 | 运行命令 | 后缀约定 | 需要 Docker |
|---|---|---|---|---|---|
| **纯单元** | JUnit 5 + Mockito，无 Spring | surefire | `mvn test` | `*Test` | 否 |
| **持久层切片（快）** | `@DataJpaTest`（默认 H2）| surefire | `mvn test` | `*Test` | 否 |
| **Web 切片** | `@WebMvcTest` + MockMvc | surefire | `mvn test` | `*Test` | 否 |
| **保真集成** | `@SpringBootTest` / `@DataJpaTest(replace=NONE)` + Testcontainers MySQL + WireMock | failsafe | `mvn verify` | `*IT` | **是** |

**命名规则**：后缀 `*Test` 归 surefire 快速轨；`*IT` 归 failsafe 保真轨。两者互不重叠，由 Maven 默认 pattern 自动分流。

**分轨判断规则**：任何「H2 与 MySQL 行为可能不一致」或「跨 HTTP 调用」的断言，归入 `*IT` 保真轨；其余都留在快速轨。

> 典型例子：`AccountRepositoryTest`（H2 切片，`*Test`）验证派生查询逻辑；`AccountAmountPrecisionIT`（真实 MySQL，`*IT`）验证 `DECIMAL(32,8)` 精度回读——H2 会静默裁剪 scale，无法暴露该问题。

---

## 2. 如何运行

### 快速轨（无 Docker）

```bash
mvn test
# 或仅跑某模块
mvn test -pl account-service
```

全部 `*Test` 均基于 H2 内存库 / Mockito，无外部依赖，开发机随时可跑，通常数秒完成。

### 保真轨（需要 Docker）

```bash
# 先确认 Docker daemon 已启动
docker info

# 跑全量（快速轨 + 保真轨 + JaCoCo 门控）
mvn verify

# 仅跑某模块的 IT
mvn verify -pl transfer-service
```

Testcontainers 会自动拉取 `mysql:8.0.36` 镜像并管理容器生命周期，无需手动 `docker compose up`。首次运行需要网络下载镜像，后续使用本地缓存。

---

## 3. 保真基类（`AbstractMySqlIntegrationTest`）

`account-service` 和 `transfer-service` 各自有一个 `support/AbstractMySqlIntegrationTest`，所有 `*IT` 继承它。

**关键实现要点：**

1. **单例容器**：`MySQLContainer` 是 `static` 字段，在 JVM 内只启动一次，跨同模块所有 `*IT` 复用，由 Testcontainers Ryuk 在 JVM 退出时清理。刻意不在 `@AfterAll` 停止，避免后续测试类重启容器。

2. **真实 DDL（单一事实来源）**：容器启动时挂载 `docker/mysql/init/01-demo-transfer.sql`（仓库根目录下，与 `docker-compose` 生产启动使用同一份脚本），放入 `/docker-entrypoint-initdb.d/`，保证测试看到的 `DECIMAL(32,8)`、唯一索引、约束与生产完全一致。DDL **不复制**进测试资源目录，以防漂移。

3. **`@DynamicPropertySource` 桥接**：Spring Boot 2.7 无 `@ServiceConnection`，需手动将容器的动态 host/port 注入到 `spring.datasource.*`。`application-test.yml` 中设 `spring.jpa.hibernate.ddl-auto: none`，防止 Hibernate 覆盖真实 schema。

4. **以 root 连接**：初始化脚本以 root 创建多个库（`account_a`、`account_b`、`transfer`），测试也以 root 连接，以获得跨库访问权限。`account-service` 的 IT 连接 `account_a`；`transfer-service` 的 IT 连接 `transfer`。

5. **无 `@SpringBootApplication` 的库模块**：`account-service` 是纯库模块，没有启动类。其 `@WebMvcTest` 和 `@DataJpaTest` 切片测试通过一个测试专用的 `AccountServiceTestApplication` 提供应用锚点。

---

## 4. `entityManager.clear()` 精度验证模式

验证 `DECIMAL(32,8)` 精度时，必须先清空 JPA 一级缓存，否则读到的是内存中的字面量 `BigDecimal`，断言变成空洞的自我比较。

```java
// 1. 写入
balanceRepository.saveAndFlush(balance);

// 2. 清空一级缓存，强制下一次查询从 MySQL 真实回读
entityManager.clear();

// 3. 读回并断言 scale
AccountBalance loaded = balanceRepository.findByUserIdAndAssetCode(userId, "USDT").orElseThrow();
assertThat(loaded.getAvailableAmount().scale()).isEqualTo(8);
```

> 注意：如果在同一个事务内写入后直接 `clear()`（未 `flush`），脏更新会丢失。模式是 `saveAndFlush` → `entityManager.clear()`，或者先 `entityManager.flush()` 再 `entityManager.clear()`。

---

## 5. WireMock for Feign

`transfer-service` 的 IT 不真正启动 A/B 账户服务，而是在同进程内启动两个 `WireMockServer`（动态端口），桩代两个账户服务的 HTTP 端点。

```java
// 启动（@BeforeAll）
wireMockA = new WireMockServer(WireMockConfiguration.options().dynamicPort());
wireMockB = new WireMockServer(WireMockConfiguration.options().dynamicPort());
wireMockA.start();  wireMockB.start();

// 将 Feign URL 指向 WireMock（@DynamicPropertySource）
registry.add("account.a.url", () -> "http://localhost:" + wireMockA.port());
registry.add("account.b.url", () -> "http://localhost:" + wireMockB.port());
```

**业务失败的桩代方式**：业务失败返回 HTTP 200 + `{"success":false,...}`（与真实账户 controller 行为一致），而**不是** HTTP 4xx/5xx。HTTP 5xx 才抛 `FeignException`。

```java
// 业务失败（余额不足等）
wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/freeze"))
        .willReturn(aResponse().withStatus(200).withBody(
                "{\"success\":false,\"code\":\"ACCOUNT_OPERATION_FAILED\",...}")));

// 下游 5xx → FeignException
wireMockA.stubFor(post(urlEqualTo("/...")).willReturn(aResponse().withStatus(500)));
```

**readTimeout 通过 `@DynamicPropertySource` 注入（重要细节）**：

```java
registry.add("feign.client.config.default.readTimeout", () -> "300");
```

不要写在 `application-test.yml` 中。原因：spring-cloud-openfeign 3.1.9 的 `FeignClientProperties` 以 `@ConfigurationProperties("feign.client")` 绑定，若写在 yml 的 test profile 中，因 `FeignClientProperties` 早期绑定时机可能导致 config map 为空，超时不生效。`@DynamicPropertySource` 以最高优先级注入 Environment，可靠触发绑定。

---

## 6. 核心原则测试（`TransferScenarioIT#creditFailedRetryConvergesToSuccessWithoutAnyCompensation`）

> **核心原则**：源账户扣减一旦确认，目标账户入账失败不做反向补偿，停在 `CREDIT_FAILED` 持续重试入账。

本测试用 WireMock Scenario（状态机）模拟「第一次入账失败、第二次成功」，并在两个关键节点断言 `cancel-freeze` 被调用 **零次**：

1. 审核通过触发扣减后，入账失败 → 数据库持久化 `CREDIT_FAILED`，此时断言零次补偿；
2. 调用 `retryService.retryOne(...)` 重试入账成功 → `SUCCESS`，再次断言零次补偿。

---

## 7. JaCoCo 覆盖率门控

`mvn verify` 时执行五步：

| 步骤 | JaCoCo Goal | 产物 |
|---|---|---|
| 1 | `prepare-agent` | 为 surefire 注入 javaagent，写 `jacoco.exec` |
| 2 | `prepare-agent-integration` | 为 failsafe 注入 javaagent，写 `jacoco-it.exec` |
| 3 | `merge` | 合并为 `jacoco-merged.exec` |
| 4 | `report` | HTML/XML 报告（`target/site/jacoco`） |
| 5 | `check` | 对合并数据执行门限，不达标则构建失败 |

**门限规则：**

- `BUNDLE`（整体）行覆盖 ≥ **70%**
- `PACKAGE`（`**.service` 和 `**.service.*`）行覆盖 ≥ **80%**

> 注意：`PACKAGE` 元素使用点分隔包名（如 `com.demo.transfer.account.service`），glob 的 `**` 不跨 `.`，故模式为 `**.service`（匹配 service 包本身）和 `**.service.*`（匹配其子包）。

**排除项（不计入覆盖率分母）：**

- `**/common/**`：共享 DTO、枚举
- `**/config/**`：Spring 配置类（含 `OpenApiConfig`）
- `**/*Application*`：Spring Boot 启动类

**报告位置**：每模块独立生成 `target/site/jacoco/`，无跨模块聚合报告。

**注意——薄模块空洞**：`common`、`account-a-service`、`account-b-service` 的所有类均被排除规则覆盖，这些模块**始终通过**门控——即使新增了没有测试的类，只要该类落在排除路径下，门控也不会报错。如果将来向这些模块添加含业务逻辑的类，请确认该类不被排除规则静默豁免。

---

## 8. 如何新建测试（速查表）

### 新建快速轨单元测试

```
模块/src/test/java/.../service/XxxServiceTest.java
```

- 继承：无
- 注解：无（纯 JUnit 5）或 `@ExtendWith(MockitoExtension.class)`
- 启动：`mvn test -pl <模块>`

### 新建 Web 切片测试

```
模块/src/test/java/.../web/XxxControllerTest.java
```

- 注解：`@WebMvcTest(XxxController.class)`
- account-service 是无启动类的库模块，依赖测试专用 `AccountServiceTestApplication`（`@SpringBootApplication`）作为切片锚点——同包层级下 `@WebMvcTest` 会自动探测到它，无需显式 `@ContextConfiguration`（如需也可显式指定）

### 新建持久层切片测试（快速轨）

```
模块/src/test/java/.../repository/XxxRepositoryTest.java
```

- 注解：`@DataJpaTest`（使用默认 H2）
- 不需要继承 `AbstractMySqlIntegrationTest`

### 新建保真集成测试

```
模块/src/test/java/.../integration/XxxIT.java
  或
模块/src/test/java/.../support/XxxIT.java
```

- **继承** `support/AbstractMySqlIntegrationTest`
- 注解：`@DataJpaTest @AutoConfigureTestDatabase(replace = NONE)` 或 `@SpringBootTest`
- 若需 WireMock：`@BeforeAll` 启动，`@DynamicPropertySource` 注入 URL，`@AfterAll` 停止，`@BeforeEach` reset
- 确认 Docker daemon 已启动，然后 `mvn verify -pl <模块>`

---

## 9. 相关文档

- [设计规格（"为什么"）](../superpowers/specs/2026-05-30-test-suite-best-practices-design.md)
- [openspec 变更](../../openspec/changes/test-suite-best-practices/proposal.md)
- [服务实现总览](service-implementation-overview.md)
- [架构决策记录](../decisions/README.md)
