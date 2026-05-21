# 跨账户划转示例工程

这是一个基于 Spring Boot、Feign、MySQL 和编排式 Saga 的跨服务账户划转基础示例。

## 服务说明

- `transfer-service`：转账入口服务，也是 Saga 流程编排者。
- `account-a-service`：账户 A 资产服务，连接 `account_a` 数据库。
- `account-b-service`：账户 B 资产服务，连接 `account_b` 数据库。
- `account-service`：账户资产共享实现，被 A/B 两个服务复用。
- `common`：公共 DTO、枚举和响应结构。

## 文档入口

- [服务实现总览](docs/design/service-implementation-overview.md)
- [设计文档索引](docs/design/README.md)
- [接口调试文档](docs/api/transfer-debug-api.md)
- [演示文档](docs/demo/cross-account-transfer-demo.md)

## 运行要求

- JDK 8 兼容运行环境
- Maven 3.8+
- MySQL 8

项目源码按 `source/target 1.8` 编译；本地验证使用 Maven 完成，也兼容较新的 JDK 运行。

## 数据库初始化

创建 3 个数据库：

```sql
CREATE DATABASE transfer DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE account_a DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE account_b DEFAULT CHARACTER SET utf8mb4;
```

执行表结构脚本：

```bash
mysql -uroot -proot transfer < docs/sql/transfer_schema.sql
mysql -uroot -proot account_a < docs/sql/account_schema.sql
mysql -uroot -proot account_b < docs/sql/account_schema.sql
```

如果要先做手工联调，可以分别在两个账户库插入一条初始余额：

```sql
INSERT INTO account_balance (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES ('user-1', 'USDT', 1000.00000000, 0.00000000, 0, NOW(), NOW());
```

如果使用项目自带的 Docker Compose，也可以直接启动 MySQL 并执行初始化脚本：

```bash
docker compose up -d mysql
```

## 本地启动

如果数据库账号密码不是 `root/root`，先设置环境变量：

```bash
export TRANSFER_DB_USERNAME=root
export TRANSFER_DB_PASSWORD=root
export ACCOUNT_A_DB_USERNAME=root
export ACCOUNT_A_DB_PASSWORD=root
export ACCOUNT_B_DB_USERNAME=root
export ACCOUNT_B_DB_PASSWORD=root
```

如果数据库连接地址不是默认本地 MySQL，也可以显式指定：

```bash
export TRANSFER_DB_URL='jdbc:mysql://localhost:3306/transfer?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
export ACCOUNT_A_DB_URL='jdbc:mysql://localhost:3306/account_a?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
export ACCOUNT_B_DB_URL='jdbc:mysql://localhost:3306/account_b?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
```

建议先安装本地模块依赖：

```bash
mvn -q -DskipTests install
```

分别在 3 个终端启动服务：

```bash
cd account-a-service && mvn spring-boot:run
```

```bash
cd account-b-service && mvn spring-boot:run
```

```bash
cd transfer-service && mvn spring-boot:run
```

默认端口：

- `transfer-service`: `8080`
- `account-a-service`: `8081`
- `account-b-service`: `8082`

## Swagger / OpenAPI

启动后可直接访问 Swagger UI：

- `transfer-service`: `http://localhost:8080/swagger-ui.html`
- `account-a-service`: `http://localhost:8081/swagger-ui.html`
- `account-b-service`: `http://localhost:8082/swagger-ui.html`

对应 OpenAPI JSON：

- `transfer-service`: `http://localhost:8080/v3/api-docs`
- `account-a-service`: `http://localhost:8081/v3/api-docs`
- `account-b-service`: `http://localhost:8082/v3/api-docs`

## 示例请求

创建 A -> B 人工审核转账：

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}'
```

创建 B -> A 人工审核转账：

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"B_TO_A","mode":"MANUAL_REVIEW"}'
```

创建 A -> B 自动提币转账：

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B","mode":"AUTO_WITHDRAW"}'
```

人工审核通过：

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"审核通过"}'
```

人工审核驳回：

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":false,"message":"审核驳回"}'
```

提交自动提币结果：

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/withdraw-result \
  -H 'Content-Type: application/json' \
  -d '{"success":true,"message":"提币成功"}'
```

重试失败步骤：

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/retry
```

查询转账单：

```bash
curl http://localhost:8080/transfers/{transferId}
```

## 验证

运行全部测试：

```bash
mvn -q test -DfailIfNoTests=false
```

当前测试覆盖：

- 账户冻结、确认扣减、取消冻结、入账，以及重复冻结的幂等处理
- A -> B 人工审核通过与驳回
- B -> A 人工审核通过
- A -> B 自动提币成功与失败
- 源账户已扣减后目标入账失败，以及从 `CREDIT_FAILED` 状态重试
