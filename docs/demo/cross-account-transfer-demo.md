# 跨账户资产划转演示文档

本文用于演示基础版跨服务、跨库资产划转能力。演示覆盖：

- A 账户转 B 账户，人工审核通过
- A 账户转 B 账户，人工审核驳回
- B 账户转 A 账户，人工审核通过
- A 账户转 B 账户，站内自动转账成功
- B 账户转 A 账户，站内自动转账成功
- 余额不足导致冻结失败
- 幂等验证
- 目标账户入账失败后的重试行为

演示口径：

- `MANUAL_REVIEW` 表示站内转账需要人工审核。
- `AUTO_WITHDRAW` 在本文中表示站内自动转账：资产仍在系统内部，只是在 A/B 账户之间划转。
- 链上提币不是本次基础版演示内容，后续应独立建模，不复用站内目标账户入账流程。

## 1. 演示拓扑

```text
用户请求
  |
  v
transfer-service :8080
  |-- Feign --> account-a-service :8081 --> account_a MySQL
  |
  |-- Feign --> account-b-service :8082 --> account_b MySQL
  |
  +-----------> transfer MySQL
```

基础版采用编排式 Saga：

- `transfer-service` 保存转账单状态。
- `account-a-service` 只操作 A 账户库。
- `account-b-service` 只操作 B 账户库。
- 每次资产变动都会写一条 `finance_ledger`。
- 账户服务用 `transfer_id + operation_type` 保证幂等。

## 2. 演示准备

### 2.1 启动 MySQL 中间件

项目提供了 `docker-compose.yml`，用于启动演示所需的 MySQL 8。

```bash
docker compose up -d mysql
```

查看容器状态：

```bash
docker compose ps
```

预期：

- `demo-transfer-mysql` 状态为 `healthy`。
- 本机 `3306` 端口可以连接 MySQL。
- 默认 root 密码是 `root`。

如果本机 `3306` 已被占用，可以换一个宿主机端口，例如 `3306`：

```bash
MYSQL_PORT=3306 docker compose up -d mysql
```

同时启动 Java 服务前需要指定数据库连接：

```bash
export TRANSFER_DB_URL='jdbc:mysql://localhost:3306/transfer?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
export ACCOUNT_A_DB_URL='jdbc:mysql://localhost:3306/account_a?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
export ACCOUNT_B_DB_URL='jdbc:mysql://localhost:3306/account_b?useSSL=false&serverTimezone=UTC&characterEncoding=utf8'
```

### 2.2 自动初始化说明

第一次启动 MySQL 容器时，Docker 会执行：

```text
docker/mysql/init/01-demo-transfer.sql
```

该脚本会自动完成：

- 创建 `transfer`、`account_a`、`account_b` 三个数据库。
- 创建转账单、步骤日志、账户余额、幂等操作记录、财务流水表。
- 给 `user-1` 初始化 A 账户 `1000 USDT`，B 账户 `500 USDT`。

连接 MySQL 检查：

```bash
docker compose exec mysql mysql -uroot -proot -e "SHOW DATABASES;"
```

如果需要彻底重建 MySQL 数据和重新执行初始化脚本：

```bash
docker compose down -v
docker compose up -d mysql
```

### 2.3 手动创建数据库

如果不用 Docker Compose，也可以手动创建数据库。

```sql
CREATE DATABASE IF NOT EXISTS transfer DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS account_a DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS account_b DEFAULT CHARACTER SET utf8mb4;
```

### 2.4 手动初始化表

```bash
mysql -uroot -proot transfer < docs/sql/transfer_schema.sql
mysql -uroot -proot account_a < docs/sql/account_schema.sql
mysql -uroot -proot account_b < docs/sql/account_schema.sql
```

### 2.5 重置演示余额

为了让每个场景互不影响，可以在每次演示前重置数据。

使用 Docker Compose 启动的 MySQL 时，可以直接执行：

```bash
docker compose exec mysql mysql -uroot -proot <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE transfer.transfer_step_log;
TRUNCATE TABLE transfer.transfer_order;

TRUNCATE TABLE account_a.finance_ledger;
TRUNCATE TABLE account_a.asset_operation;
TRUNCATE TABLE account_a.account_balance;

TRUNCATE TABLE account_b.finance_ledger;
TRUNCATE TABLE account_b.asset_operation;
TRUNCATE TABLE account_b.account_balance;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO account_a.account_balance
    (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES
    ('user-1', 'USDT', 1000.00000000, 0.00000000, 0, NOW(), NOW());

INSERT INTO account_b.account_balance
    (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES
    ('user-1', 'USDT', 500.00000000, 0.00000000, 0, NOW(), NOW());
SQL
```

### 2.6 启动服务

三个终端分别启动：

```bash
mvn -q -pl account-a-service spring-boot:run
```

```bash
mvn -q -pl account-b-service spring-boot:run
```

```bash
mvn -q -pl transfer-service spring-boot:run
```

### 2.7 查询辅助 SQL

查询转账单：

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT transfer_id, user_id, source_account_type, target_account_type,
       amount, transfer_mode, status, last_error_code, last_error_message
FROM transfer.transfer_order
ORDER BY id DESC;"
```

查询步骤日志：

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT transfer_id, step_name, step_status, error_message, created_at
FROM transfer.transfer_step_log
ORDER BY id;"
```

查询 A 账户余额和流水：

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT user_id, asset_code, available_amount, frozen_amount
FROM account_a.account_balance;"
```

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT transfer_id, operation_type, available_delta, frozen_delta,
       available_after, frozen_after
FROM account_a.finance_ledger
ORDER BY id;"
```

查询 B 账户余额和流水：

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT user_id, asset_code, available_amount, frozen_amount
FROM account_b.account_balance;"
```

```bash
docker compose exec mysql mysql -uroot -proot -e "
SELECT transfer_id, operation_type, available_delta, frozen_delta,
       available_after, frozen_after
FROM account_b.finance_ledger
ORDER BY id;"
```

### 2.8 命令辅助说明

下面的场景会使用 `jq` 从创建转账响应中提取 `transferId`：

```bash
TRANSFER_ID=$(curl ... | jq -r '.data.transferId')
```

如果本机没有安装 `jq`，也可以直接从接口响应里复制 `data.transferId`，再手动执行：

```bash
export TRANSFER_ID='复制到这里'
```

建议每个场景开始前先执行一次“重置演示余额”，这样预期余额更容易对齐。

## 3. 场景一：A 转 B，人工审核通过

### 3.1 创建转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":100,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

后续命令使用 `$TRANSFER_ID` 表示本次转账单号。

预期：

- 转账单状态为 `WAIT_REVIEW`。
- A 账户：`available_amount` 减少 100，`frozen_amount` 增加 100。
- A 账户流水新增 `FREEZE`。
- B 账户余额不变。

### 3.2 审核通过

```bash
curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"人工审核通过"}'
```

预期：

- 转账单状态为 `SUCCESS`。
- A 账户：冻结金额减少 100。
- B 账户：可用金额增加 100。
- A 账户流水新增 `CONFIRM_DEBIT`。
- B 账户流水新增 `CREDIT`。

最终余额示例：

```text
account_a: available=900, frozen=0
account_b: available=600, frozen=0
```

## 4. 场景二：A 转 B，人工审核驳回

### 4.1 创建转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":80,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

预期：

- 转账单状态为 `WAIT_REVIEW`。
- A 账户：`available_amount` 减少 80，`frozen_amount` 增加 80。

### 4.2 审核驳回

```bash
curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":false,"message":"资料不完整，驳回"}'
```

预期：

- 转账单状态为 `REJECTED`。
- A 账户：可用金额恢复，冻结金额减少。
- A 账户流水新增 `CANCEL_FREEZE`。
- B 账户余额不变。

## 5. 场景三：B 转 A，人工审核通过

### 5.1 创建转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":50,"direction":"B_TO_A","mode":"MANUAL_REVIEW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

预期：

- 转账单状态为 `WAIT_REVIEW`。
- B 账户：`available_amount` 减少 50，`frozen_amount` 增加 50。
- B 账户流水新增 `FREEZE`。

### 5.2 审核通过

```bash
curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"B 转 A 审核通过"}'
```

预期：

- 转账单状态为 `SUCCESS`。
- B 账户流水新增 `CONFIRM_DEBIT`。
- A 账户流水新增 `CREDIT`。

最终余额示例：

```text
account_a: available=1050, frozen=0
account_b: available=450, frozen=0
```

## 6. 场景四：A 转 B，站内自动转账成功

### 6.1 创建站内自动转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":30,"direction":"A_TO_B","mode":"AUTO_WITHDRAW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

预期：

- 转账单状态为 `SUCCESS`。
- A 账户完成冻结并最终扣减冻结金额。
- B 账户可用金额增加。
- 新建站内自动转账不需要调用 `/withdraw-result`。

最终余额示例：

```text
account_a: available=970, frozen=0
account_b: available=530, frozen=0
```

## 7. 场景五：B 转 A，站内自动转账成功

### 7.1 创建站内自动转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":30,"direction":"B_TO_A","mode":"AUTO_WITHDRAW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

预期：

- 转账单状态为 `SUCCESS`。
- B 账户完成冻结并最终扣减冻结金额。
- A 账户可用金额增加。
- 这仍然是站内转账，不涉及链上交易。

最终余额示例：

```text
account_a: available=1030, frozen=0
account_b: available=470, frozen=0
```

## 8. 场景六：余额不足，冻结失败

当前示例初始化 A 账户可用余额为 1000。创建一笔超过可用余额的转账：

```bash
curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":999999,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}'
```

预期：

- 转账单状态为 `FREEZE_FAILED`。
- `last_error_code` 为 `ACCOUNT_OPERATION_FAILED`。
- A/B 账户余额不变。
- 不会进入 `WAIT_REVIEW`。

## 9. 场景七：账户操作幂等验证

账户服务内部接口以 `transferId + operationType` 做幂等。可以直接调用账户 A 服务模拟重复冻结。

### 9.1 第一次冻结

```bash
curl -s -X POST http://localhost:8081/internal/accounts/assets/freeze \
  -H 'Content-Type: application/json' \
  -d '{"transferId":"demo-idempotent-1","userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B"}'
```

预期：

- 响应 `success=true`。
- `data.applied=true`。
- A 账户可用减少 10，冻结增加 10。
- A 账户流水新增一条 `FREEZE`。

### 9.2 重复冻结

```bash
curl -s -X POST http://localhost:8081/internal/accounts/assets/freeze \
  -H 'Content-Type: application/json' \
  -d '{"transferId":"demo-idempotent-1","userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B"}'
```

预期：

- 响应 `success=true`。
- `data.applied=false`。
- A 账户余额不再变化。
- A 账户不会新增第二条 `FREEZE` 流水。

验证 SQL：

```sql
SELECT transfer_id, operation_type, COUNT(*)
FROM account_a.finance_ledger
WHERE transfer_id = 'demo-idempotent-1'
GROUP BY transfer_id, operation_type;
```

预期结果只有一条：

```text
demo-idempotent-1 | FREEZE | 1
```

## 10. 场景八：目标账户入账失败后重试

这个场景用于说明 Saga 的“源账户已扣冻结、目标账户入账失败，后续只重试目标入账”的行为。

### 10.1 自动化测试演示方式

当前基础版没有提供“强制让账户 B 入账失败”的管理接口。最稳定的演示方式是运行集成测试：

```bash
mvn -q -pl transfer-service -am test \
  -Dtest=TransferScenarioIntegrationTest#targetCreditFailureLeavesCreditFailedAndRetryOnlyCreditsTarget \
  -DfailIfNoTests=false
```

该测试模拟：

1. A 账户冻结成功。
2. 审核通过。
3. A 账户确认扣减成功。
4. B 账户第一次入账返回失败。
5. 转账单进入 `CREDIT_FAILED`。
6. 执行重试。
7. 重试只调用 B 账户 `credit`，不会再次调用 A 账户 `confirmDebit`。
8. B 入账成功后转账单变为 `SUCCESS`。

也可以运行站内自动转账失败重试测试，演示自动模式下同样复用 `CREDIT_FAILED` 重试能力：

```bash
mvn -q -pl transfer-service -am test \
  -Dtest=TransferScenarioIntegrationTest#autoWithdrawTargetCreditFailureLeavesCreditFailedAndRetryOnlyCreditsTarget \
  -DfailIfNoTests=false
```

### 10.2 人工演示思路

如果需要通过真实服务手工演示，可以临时增加一个测试开关，让 `account-b-service` 在指定 `transferId` 的 `credit` 操作上返回失败。基础版目前未内置该开关，所以不建议用停掉 B 服务的方式演示：

- Feign 网络异常属于调用异常路径。
- 当前基础版的可重试状态演示主要由集成测试覆盖。
- 后续可以单独增加“演示故障注入开关”，例如 `demo.failCreditTransferIds`。

## 11. 演示顺序建议

推荐现场演示顺序：

1. 展示三服务和三库。
2. 重置数据。
3. 演示 A 转 B 人工审核通过。
4. 演示 A 转 B 人工审核驳回。
5. 演示 B 转 A 人工审核通过。
6. 演示 A 转 B 站内自动转账成功。
7. 演示 B 转 A 站内自动转账成功。
8. 演示余额不足冻结失败。
9. 演示账户接口幂等。
10. 用集成测试演示 `CREDIT_FAILED -> retry -> SUCCESS`。

## 12. 关键讲解点

- 为什么不用分布式事务：基础版先用 Saga 状态机和幂等重试达到最终一致。
- 为什么冻结在源账户：审核和站内自动完成期间锁定资金，避免用户重复使用。
- 为什么每步都写流水：资产审计和问题排查需要完整轨迹。
- 为什么要幂等：Feign 超时、重试、人工重复点击都不能造成重复扣款或重复入账。
- 为什么 `CREDIT_FAILED` 只重试目标入账：源账户冻结资产已经确认扣减，反向补偿会引入新的资金风险，基础版选择重试收敛。
