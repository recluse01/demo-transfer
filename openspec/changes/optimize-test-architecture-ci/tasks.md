## 1. CI 闭环（GitHub Actions）

- [ ] 1.1 新增 `.github/workflows/ci.yml`：push 到分支 + pull_request 触发，单 job 跑在 `ubuntu-latest`
- [ ] 1.2 job 步骤：`actions/checkout` → `actions/setup-java`（Temurin 8 + Maven 依赖缓存）→ `mvn -B verify`
- [ ] 1.3 收尾步骤：`actions/upload-artifact` 上传 `**/target/site/jacoco/` 覆盖率报告（`if: always()` 便于失败时也可查看）
- [ ] 1.4 推送验证分支，确认 GitHub Actions 跑通 `mvn verify`、门禁生效、artifact 上传成功
- [ ] 1.5 提交：`ci(test): 新增 GitHub Actions 单 job 跑 mvn verify`

## 2. 测试日志噪音治理

- [ ] 2.1 新增 `account-service/src/test/resources/logback-test.xml`，将 `org.testcontainers`、`com.github.dockerjava`、`tc` 等 logger 设为 `WARN`，保留应用包与 Spring 关键启动信息
- [ ] 2.2 新增 `transfer-service/src/test/resources/logback-test.xml`（同上）
- [ ] 2.3 本地跑保真轨抽查：无 docker-java DEBUG 刷屏，WARN/ERROR 与关键失败上下文仍可见
- [ ] 2.4 提交：`test(account,transfer): 压制 testcontainers/docker-java 日志噪音`

## 3. 保真 IT 隔离硬化（仅 transfer 侧）

- [ ] 3.1 改 `transfer-service` 的 `MySqlContainerSmokeIT`：`count()==0` 断言改为「按不存在唯一键查询返回空 + `count()` 不抛异常」证明 schema 可查询，去除对全局空表的依赖
- [ ] 3.2 审计确认：account 侧写库用例及种子查询、transfer 侧写库 IT 均用唯一业务键或事务回滚，不污染共享单例容器（account 侧 SmokeIT 不改代码）
- [ ] 3.3 验收：transfer 侧 SmokeIT 单独运行、全量 `mvn verify`、置于其他写库 IT 之后运行均通过
- [ ] 3.4 提交：`test(transfer): 冒烟 IT 隔离硬化，不再依赖全局空表`

## 4. WireMock/JSON 夹具去重

- [ ] 4.1 在 `FeignClientWireMockIT` 类内抽取响应体 helper，覆盖「成功体 + 业务失败体（`success:false`/`data:null`）」两种合法 `ApiResponse`
- [ ] 4.2 保持模拟反序列化失败的非标准体（纯文本 `"Service Unavailable"`、`{"error":...}`）手写，不进 helper
- [ ] 4.3 评估是否提升为 transfer-service 测试源码内小工具类：仅当 `TransferScenarioIT` 与 `FeignClientWireMockIT` 都能明显减少重复时才做，否则保留类内 helper（模块内、不跨模块、不引入新依赖）
- [ ] 4.4 提交：`test(transfer): WireMock 响应体夹具去重`

## 5. 容器复用提速

- [ ] 5.1 两个保真基类 `AbstractMySqlIntegrationTest` 的 `MySQLContainer` 链式加 `.withReuse(true)`
- [ ] 5.2 本地开启 `testcontainers.reuse.enable=true` 后验证：写库 IT 仍全部通过、不依赖残留状态
- [ ] 5.3 验证 CI（不设该属性）行为不变：仍全新容器 + Ryuk 清理
- [ ] 5.4 提交：`test(account,transfer): 保真基类容器复用本地提速`

## 6. 文档更新

- [ ] 6.1 更新 `docs/design/testing-strategy.md`：补「CI」小节（触发、单 job、Temurin 8、门禁、artifact）
- [ ] 6.2 补「容器复用本地开启方式」小节：opt-in 配置、适用场景、脏数据/DDL 漂移时删除 reusable container 后重跑的处理方式
- [ ] 6.3 提交：`docs(test): 补充 CI 与容器复用说明`

## 7. 收尾验证

- [ ] 7.1 `mvn test`（模拟无 Docker，仅 `*Test`）全绿
- [ ] 7.2 `mvn verify`（Docker 在位）全绿、JaCoCo 门禁通过、行为相对现状不退化
- [ ] 7.3 确认所有改动均在测试基础设施/CI/文档范围内，未触及任何生产代码
