# 跨账户划转工程 · 协作指南

本文件由 Claude Code 与 Codex 等 agent 共用（`CLAUDE.md` 是指向本文件的软链接）。修改其一即修改两者。

## 1. 项目概览

基于 Spring Boot + Feign + 编排式 Saga 的跨服务、跨库账户划转基础示例。

| 模块 | 职责 |
| --- | --- |
| `common` | 共享枚举、请求 DTO、统一响应结构。 |
| `account-service` | 账户资产核心实现：余额、冻结、扣减、解冻、入账、流水、幂等。 |
| `account-a-service` | A 账户启动应用，复用 `account-service`，连接 `account_a` 库。 |
| `account-b-service` | B 账户启动应用，复用 `account-service`，连接 `account_b` 库。 |
| `transfer-service` | 转账入口、状态机、Feign 调用、人工审核、站内自动完成、失败重试。 |

技术栈：JDK 8（`source/target 1.8`）、Spring Boot 2.7.18、Spring Cloud 2021.0.9、MySQL 8、Maven 多模块。

## 2. 常用命令

```bash
mvn -q -DskipTests install              # 安装本地模块依赖（改动公共模块后需重跑）
mvn -q test -DfailIfNoTests=false       # 全量测试
docker compose up -d mysql              # 启动并初始化 MySQL
docker compose exec -T mysql ...        # 容器内执行 SQL（务必带 -T）
```

分别在 3 个终端启动服务（端口 `transfer` 8080 / `account-a` 8081 / `account-b` 8082）：

```bash
cd account-a-service && mvn spring-boot:run
cd account-b-service && mvn spring-boot:run
cd transfer-service  && mvn spring-boot:run
```

启动命令、演示请求、数据库初始化的完整说明见 `README.md`。

## 3. 架构要点

- `transfer-service` 编排 Saga 流程；A 库只由 `account-a-service` 改，B 库只由 `account-b-service` 改，跨库一致性靠转账单状态 + 幂等 + 失败重试收敛（最终一致，非强一致）。
- 账户操作幂等键为 `transfer_id + operation_type`；重复请求返回成功但 `applied=false`。
- **核心原则：源账户冻结金额一旦确认扣减，目标入账失败不做反向补偿，停在 `CREDIT_FAILED` 持续重试入账。**
- A/B 服务刻意共用同一份 `account-service`，避免两边资产逻辑漂移。
- 状态机、时序图、表结构、失败恢复等细节见 `docs/design/service-implementation-overview.md`；以上各决策「为什么」的单一来源是 ADR（`docs/decisions/`）。

## 4. 关键编码约定

- 金额一律用 `BigDecimal` 与 MySQL `DECIMAL(32,8)`，**禁用浮点数**。
- 仅使用 Java 8 语法（编译目标 `1.8`）。
- 文档与代码注释使用中文。
- 非必要不引入新框架（Seata / XA / 消息队列 / 对账中心）；要引入先讨论。

## 5. 提交与工作流

- 每完成一个工作阶段，只提交该阶段的改动。
- 提交信息遵循 Conventional Commits、使用中文，规范见 `docs/conventional-commits.md`。
- 采用 OpenSpec spec-driven 工作流，配置见 `openspec/`。

## 6. 通用行为准则

1. **先想后写**：显式说明假设；有多种理解先列出、不擅自定夺；有更简方案就提出；不清楚就停下发问，别掩盖困惑。
2. **简单优先**：写能解决问题的最小实现，不做未要求的特性、抽象与配置项；能 50 行别写 200 行。
3. **外科手术式修改**：只改必须改的，匹配现有风格；不顺手重构、不美化无关代码；只清理自己改动产生的孤儿引用，发现既有死代码先提示、别删。
4. **目标驱动验证**：把任务转成可验证的成功标准（如「先写复现 bug 的测试再修」），循环到验证通过。

判断准绳：每一行改动都应能直接追溯到用户的需求。

## 7. 文档索引

- [文档中心](docs/README.md)：全局导航入口（组件架构图 + 阅读路径），先看这里。
- [README](README.md)：构建、启动、演示请求、数据库初始化、验证。
- [服务实现总览](docs/design/service-implementation-overview.md)：时序图、状态机、幂等、失败恢复。
- [架构决策记录（ADR）](docs/decisions/README.md)：关键设计「为什么」的单一来源。
- [术语表 / 概念索引](docs/concepts/glossary.md)
- [接口调试文档](docs/api/transfer-debug-api.md) · [演示文档](docs/demo/cross-account-transfer-demo.md)
- [测试策略](docs/design/testing-strategy.md)：测试金字塔、双轨命名约定、保真基类、WireMock、JaCoCo 门控、新增测试速查。
