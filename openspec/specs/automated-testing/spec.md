# automated-testing Specification

## Purpose
TBD: created by archiving change test-suite-best-practices. Update Purpose after archive.

## Requirements
### Requirement: 分层测试金字塔与命名约定
项目 SHALL 按四层组织测试——单元、持久层切片、Web 切片、保真集成——并以文件名后缀区分执行轨道：快速用例 MUST 以 `*Test` 命名（surefire `test` 阶段），保真集成用例 MUST 以 `*IT` 命名（failsafe `verify` 阶段）。

#### Scenario: 快速轨在无 Docker 环境可运行
- **WHEN** 开发者在未安装 Docker 的机器执行 `mvn test`
- **THEN** 所有 `*Test`（单元、H2 切片、`@WebMvcTest`）通过，构建不依赖任何容器

#### Scenario: 保真轨在 verify 阶段运行
- **WHEN** 在 Docker 在位的环境执行 `mvn verify`
- **THEN** maven-failsafe-plugin 额外执行全部 `*IT`，与 surefire 的 `*Test` 互不重叠地运行

#### Scenario: 断言归轨准绳
- **WHEN** 一条断言依赖「H2 与 MySQL 可能不一致的行为」或「跨 HTTP 调用」
- **THEN** 该用例 MUST 归入 `*IT` 保真轨；其余归快速轨

### Requirement: 双轨数据层与真实 DDL 初始化
持久层测试 SHALL 支持双轨：默认 H2 内存库用于快速反馈；保真轨 MUST 使用 Testcontainers 真实 MySQL，且容器 MUST 由真实 DDL 脚本（`docker/mysql/init/01-demo-transfer.sql`）初始化、`ddl-auto` 设为 `none`，使测试看到与生产一致的 schema。

#### Scenario: 保真轨连真实 MySQL
- **WHEN** 执行继承 `AbstractMySqlIntegrationTest` 的 `*IT`
- **THEN** 通过单例 `MySQLContainer` + `@DynamicPropertySource` 连接真实 MySQL 容器，且 `@DataJpaTest` 用例标注 `@AutoConfigureTestDatabase(replace = NONE)` 不被换回 H2

#### Scenario: 容器复用真实 DDL
- **WHEN** 保真轨容器启动
- **THEN** 其 schema 由项目真实 DDL 脚本建立（含 `DECIMAL(32,8)` 列与唯一索引），不由 Hibernate 自动生成

#### Scenario: 单例容器跨类复用
- **WHEN** 一次 `mvn verify` 运行多个保真测试类
- **THEN** MySQL 容器仅启动一次并被复用，不逐类重启

### Requirement: 金额精度不变量
针对资金金额的测试 SHALL 在真实 MySQL（`DECIMAL(32,8)`）上验证 `BigDecimal` 精度不丢失、不使用浮点。

#### Scenario: 高精度金额往返不失真
- **WHEN** 在保真轨写入并读回一个 8 位小数的金额（如 `0.00000001`）
- **THEN** 读回值与写入值精确相等（`BigDecimal` 比较），标度保持

### Requirement: 账户操作幂等性验证
测试 SHALL 验证账户操作以 `transfer_id + operation_type` 为幂等键：重复请求返回成功但 `applied=false`，且在真实唯一索引约束下不产生重复副作用。

#### Scenario: 重复操作返回 applied=false
- **WHEN** 以相同 `transfer_id + operation_type` 重复提交同一资产操作
- **THEN** 响应成功且 `applied=false`，余额/流水不被二次改动

#### Scenario: 唯一索引在真实库生效
- **WHEN** 保真轨并发或重复写入相同幂等键的操作记录
- **THEN** 数据库唯一索引保证至多一条生效记录

### Requirement: Saga 状态机与不补偿原则
测试 SHALL 覆盖转账状态机的关键流转，并验证核心原则：源账户冻结金额确认扣减后，目标入账失败 MUST NOT 触发反向补偿，而是停在 `CREDIT_FAILED` 持续重试入账直至收敛。

#### Scenario: 入账失败停在 CREDIT_FAILED 并重试收敛
- **WHEN** 源已确认扣减、目标入账首次失败、随后重试成功
- **THEN** 转账单经过 `CREDIT_FAILED` 后最终到达完成态，全程不发生源账户反向补偿

#### Scenario: 人工审核路径
- **WHEN** 转账走人工审核模式并被通过/拒绝
- **THEN** 状态机分别流转到对应后继状态

### Requirement: Web 层契约测试
每个 Controller SHALL 有 `@WebMvcTest` + MockMvc 测试，覆盖入参校验失败、成功路径、HTTP 状态码与 `ApiResponse` 结构。

#### Scenario: 入参校验失败返回错误响应
- **WHEN** 向 Controller 端点提交不合法请求体（违反 `@Valid` 约束）
- **THEN** 返回对应错误状态码与统一 `ApiResponse` 错误结构，不触达 service

#### Scenario: 成功路径返回统一响应结构
- **WHEN** 提交合法请求
- **THEN** 返回 `2xx` 与符合 `ApiResponse<T>` 约定的成功响应

### Requirement: Feign 跨服务调用测试
transfer-service 调用 A/B 账户服务的 Feign 客户端 SHALL 用 WireMock 桩 HTTP 验证，覆盖序列化/反序列化、错误解码与超时；`AccountClientRouter` 的方向到客户端路由 SHALL 有覆盖全分支的单元测试。

#### Scenario: Feign 真实 HTTP 收发
- **WHEN** WireMock 模拟 A/B 服务返回成功响应
- **THEN** Feign 客户端正确序列化请求并反序列化响应为领域对象

#### Scenario: 下游错误被正确解码
- **WHEN** WireMock 模拟下游返回错误状态码
- **THEN** Feign 调用按约定抛出/映射为可处理的错误

#### Scenario: 路由分支全覆盖
- **WHEN** 给定每一种 `TransferDirection`
- **THEN** `AccountClientRouter` 解析出正确的源/目标 `AccountType` 与对应 `AccountOperationsClient`

### Requirement: 失败重试调度测试
`TransferRetryScheduler` SHALL 有测试验证其扫描失败步骤并触发重试的行为，且不依赖真实定时触发。

#### Scenario: 调度方法触发失败单重试
- **WHEN** 存在处于失败态的转账单并直接调用调度方法
- **THEN** 失败步骤被重新发起，不依赖等待 `@Scheduled` 的真实间隔

### Requirement: 覆盖率门禁
构建 SHALL 通过 JaCoCo 合并双轨覆盖率并在 `verify` 阶段强制门禁：整体行覆盖 ≥70%，核心业务包（`**/service/**`）行覆盖 ≥80%；DTO（`**/common/**`）、`**/config/**`、`**/*Application*`、OpenApi 配置 MUST 排除在度量之外。

#### Scenario: 覆盖率不达标使构建失败
- **WHEN** 执行 `mvn verify` 且整体或核心包覆盖率低于阈值
- **THEN** jacoco `check` 使构建失败并报告未达标的规则

#### Scenario: 无逻辑类不计入分母
- **WHEN** 生成覆盖率报告
- **THEN** DTO、config、启动类、OpenApi 配置不参与覆盖率统计

### Requirement: 保真轨隔离不变量与本地容器复用
保真轨测试 MUST NOT 依赖共享单例 `MySQLContainer` 的全局状态（如「表为空」）来通过断言：冒烟 IT SHALL 以「按不存在的唯一键查询返回空 + 表可计数不抛异常」证明 schema 存在且可查询；所有写库 IT MUST 通过唯一业务键、事务回滚或显式清理保证彼此隔离，不污染共享容器。容器复用（`withReuse`）SHALL 仅作为开发者本地 opt-in 的提速手段，MUST NOT 成为测试正确性的前提——已存在的复用容器不会重新执行初始化脚本，故测试不得依赖容器初始状态或跨 JVM 残留数据。

#### Scenario: 冒烟 IT 不依赖全局空表
- **WHEN** 保真冒烟 IT 单独运行、在全量 `mvn verify` 中运行、或置于其他写库 IT 之后运行
- **THEN** 它均通过，不依赖目标表为空——通过「唯一键查空 + 表可计数」验证 schema 可查询

#### Scenario: 写库 IT 彼此隔离
- **WHEN** 多个写库 IT 在同一 JVM 内共享单例容器顺序执行
- **THEN** 每个用例靠唯一业务键、事务回滚或显式清理保证不受其他用例残留数据影响

#### Scenario: 本地容器复用不影响正确性
- **WHEN** 开发者在 `~/.testcontainers.properties` 开启 `testcontainers.reuse.enable=true` 并跨多次运行复用容器
- **THEN** 写库 IT 仍全部通过、不依赖残留状态；CI 因不设该属性而始终使用全新容器，行为不变
