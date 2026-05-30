## 1. 构建基础设施

- [x] 1.1 根 `pom.xml` 的 `dependencyManagement` 引入 `org.testcontainers:testcontainers-bom:1.19.8` 并统一管理 WireMock 版本（`2.35.2`）
- [x] 1.2 根 `pom.xml` 配置 `maven-failsafe-plugin`（绑定 `integration-test`/`verify`，默认匹配 `**/*IT.java`），`maven-surefire-plugin` 默认仅跑 `*Test` 并排除 `*IT`
- [x] 1.3 `account-service`/`transfer-service` 的 `pom.xml`：保留 `h2`，新增 `org.testcontainers:mysql`、`org.testcontainers:junit-jupiter`（test scope）；account-service 补 `mysql-connector`（test scope，保真轨驱动）
- [x] 1.4 `transfer-service` 的 `pom.xml` 新增 `wiremock-jre8-standalone`（test scope，JDK8 兼容）
- [x] 1.5 各模块新增 `src/test/resources/application-test.yml`（`test` profile 专属：保真轨 `ddl-auto: none`、降日志噪音；不影响 H2 快速轨）
- [x] 1.6 验证：`mvn -DskipTests install` 成功（EXIT=0），依赖解析到目标版本；`mvn clean test` 快速轨 36 用例全绿

## 2. 保真轨基础设施

- [x] 2.1 在 `account-service`（连 `account_a`）与 `transfer-service`（连 `transfer`）各建基类 `AbstractMySqlIntegrationTest`：静态单例 `MySQLContainer`（mysql:8.0.36）+ `@DynamicPropertySource` 注入连接串，以 root 访问多库
- [x] 2.2 基类容器挂载真实 DDL `docker/mysql/init/01-demo-transfer.sql` 至 `/docker-entrypoint-initdb.d`（与 compose 一致，单一事实来源）；`application-test.yml` 设 `ddl-auto: none`
- [x] 2.3 两模块各写最小冒烟 IT（account 读 account_a 种子余额并校验 DECIMAL 精度；transfer 校验 transfer_order 表可查询）；`mvn verify` 双轨全绿（account: 11+1IT；transfer: 23+1IT）

## 3. 快速轨补层（无需 Docker）

- [ ] 3.1 `AccountAssetControllerTest`（`@WebMvcTest`）：`freeze`/`confirm-debit`/`cancel-freeze`/`credit` 四端点的校验失败、成功、`ApiResponse` 结构
- [ ] 3.2 `TransferControllerTest`（`@WebMvcTest`）：`create`/`review`/`withdraw-result`/`retry`/`get` 五端点
- [ ] 3.3 `AccountClientRouterTest`（纯单元）：每种 `TransferDirection` 到源/目标 `AccountType` 与 `AccountOperationsClient` 的全分支路由
- [ ] 3.4 `TransferRetrySchedulerTest`：直接调用调度方法触发失败单重试（不依赖真实 `@Scheduled` 间隔）
- [ ] 3.5 复核现有 `*Test`（Saga/Retry/Repository 的纯逻辑部分）保持 H2 快速轨，无 Docker 可跑
- [ ] 3.6 验证：`mvn -q test`（模拟无 Docker）全绿

## 4. 保真轨用例（需 Docker）

- [ ] 4.1 `AccountAssetIdempotencyIT`：真实唯一索引下 `transfer_id + operation_type` 重复请求返回 `applied=false`，无二次副作用
- [ ] 4.2 `AccountAmountPrecisionIT`：`DECIMAL(32,8)` 高精度金额（如 `0.00000001`）写入读回精确相等、标度保持
- [ ] 4.3 `FeignClientWireMockIT`：WireMock 桩 A/B 服务，验证 Feign 序列化/反序列化、下游错误解码、超时
- [ ] 4.4 `TransferScenarioIT`（`@SpringBootTest` + WireMock + Testcontainers）：正常完成、人工审核通过/拒绝、`CREDIT_FAILED` 持续重试收敛且不反向补偿
- [ ] 4.5 将现有 Repository/集成测试中依赖真实库语义的断言迁入对应 `*IT`
- [ ] 4.6 验证：`mvn -q verify`（Docker 在位）保真轨全绿

## 5. 覆盖率门禁

- [ ] 5.1 根 `pom.xml` 配置 `jacoco-maven-plugin`：`prepare-agent` + `prepare-agent-integration` + 合并 `report` + `check` 绑定 `verify`
- [ ] 5.2 配置门禁规则：整体行覆盖 ≥70%、`**/service/**` ≥80%
- [ ] 5.3 配置排除：`**/common/**`、`**/config/**`、`**/*Application*`、OpenApi 配置
- [ ] 5.4 验证：`mvn -q verify` 门禁通过；故意降阈值反证门禁能使构建失败后还原

## 6. 文档与收尾

- [ ] 6.1 新增 `docs/design/testing-strategy.md`：金字塔分层、`*Test`/`*IT` 约定、双轨如何跑、Docker 前置
- [ ] 6.2 在 `CLAUDE.md` 第 7 节文档索引登记测试策略文档
- [ ] 6.3 按阶段（1→2→3→4→5→6）拆分提交，遵循中文 Conventional Commits
- [ ] 6.4 最终验证：`mvn test` 无 Docker 全绿 且 `mvn verify` 有 Docker 全绿含门禁
