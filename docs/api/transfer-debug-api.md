# 跨账户转账接口调试文档

这份文档面向本地联调和问题排查，重点提供：

- 接口清单
- 请求参数说明
- 可直接执行的 `curl` 示例
- 常见响应样例
- 调试时需要关注的状态和数据库表

本文默认服务地址如下：

- `transfer-service`: `http://localhost:8080`
- `account-a-service`: `http://localhost:8081`
- `account-b-service`: `http://localhost:8082`

Swagger UI 入口：

- `transfer-service`: `http://localhost:8080/swagger-ui.html`
- `account-a-service`: `http://localhost:8081/swagger-ui.html`
- `account-b-service`: `http://localhost:8082/swagger-ui.html`

OpenAPI JSON：

- `transfer-service`: `http://localhost:8080/v3/api-docs`
- `account-a-service`: `http://localhost:8081/v3/api-docs`
- `account-b-service`: `http://localhost:8082/v3/api-docs`

## 1. 统一响应结构

所有接口都返回统一结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

字段说明：

| 字段 | 含义 |
| --- | --- |
| `success` | 是否处理成功。 |
| `code` | 业务结果码。 |
| `message` | 结果说明。 |
| `data` | 具体返回数据；失败时通常为 `null`。 |

常见失败码：

| code | 场景 |
| --- | --- |
| `TRANSFER_OPERATION_FAILED` | 转账服务处理失败，例如状态不合法、转账单不存在。 |
| `ACCOUNT_OPERATION_FAILED` | 账户服务处理失败，例如余额不足、账户余额记录不存在。 |

## 2. 转账服务接口

### 2.1 创建转账

- 方法：`POST`
- 路径：`/transfers`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | `string` | 是 | 用户标识。 |
| `assetCode` | `string` | 是 | 资产编码，例如 `USDT`。 |
| `amount` | `number` | 是 | 转账金额，最小值 `0.00000001`。 |
| `direction` | `string` | 是 | 转账方向：`A_TO_B`、`B_TO_A`。 |
| `mode` | `string` | 是 | 转账模式：`MANUAL_REVIEW`、`AUTO_WITHDRAW`。 |

说明：

- `MANUAL_REVIEW`：冻结成功后进入 `WAIT_REVIEW`。
- `AUTO_WITHDRAW`：站内自动完成模式，支持 `A_TO_B` 和 `B_TO_A`；冻结成功后系统会主动确认扣减并给目标账户入账。

示例：

```bash
curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"user-1",
    "assetCode":"USDT",
    "amount":100,
    "direction":"A_TO_B",
    "mode":"MANUAL_REVIEW"
  }'
```

成功响应示例：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "transferId": "0b3540d5-ec2a-4a64-a0d1-3b77fa8d9310",
    "userId": "user-1",
    "sourceAccountType": "ACCOUNT_A",
    "targetAccountType": "ACCOUNT_B",
    "assetCode": "USDT",
    "amount": 100,
    "transferMode": "MANUAL_REVIEW",
    "status": "WAIT_REVIEW",
    "lastErrorCode": null,
    "lastErrorMessage": null
  }
}
```

余额不足响应示例：

```json
{
  "success": false,
  "code": "TRANSFER_OPERATION_FAILED",
  "message": "余额不足",
  "data": null
}
```

### 2.2 人工审核

- 方法：`POST`
- 路径：`/transfers/{transferId}/review`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `approved` | `boolean` | 是 | 是否审核通过。 |
| `message` | `string` | 否 | 审核备注。 |

通过示例：

```bash
curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"人工审核通过"}'
```

驳回示例：

```bash
curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":false,"message":"人工审核驳回"}'
```

结果说明：

- `approved=true`：`WAIT_REVIEW -> DEBIT_SUCCESS -> SUCCESS`
- `approved=false`：`WAIT_REVIEW -> REJECTED`

如果状态不对，例如已经成功的单子再次审核，会返回：

```json
{
  "success": false,
  "code": "TRANSFER_OPERATION_FAILED",
  "message": "transfer status must be WAIT_REVIEW",
  "data": null
}
```

### 2.3 查询转账单

- 方法：`GET`
- 路径：`/transfers/{transferId}`

示例：

```bash
curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

典型用途：

- 查看当前转账状态。
- 排查失败码和失败信息。
- 确认人工审核、提现回调、手动重试后的状态推进结果。

## 3. 账户服务内部接口

这些接口主要由 `transfer-service` 通过 Feign 调用。联调时可以直接调用它们验证账户侧行为。

统一请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `transferId` | `string` | 是 | 转账唯一标识，也是幂等键一部分。 |
| `userId` | `string` | 是 | 用户标识。 |
| `assetCode` | `string` | 是 | 资产编码。 |
| `amount` | `number` | 是 | 操作金额。 |
| `direction` | `string` | 是 | 转账方向：`A_TO_B`、`B_TO_A`。 |

说明：

- 同一个 `transferId + operationType` 只会真实执行一次。
- 重复请求不会重复改余额，响应中的 `data.applied` 会是 `false`。

### 3.1 冻结资产

- 方法：`POST`
- 路径：`/internal/accounts/assets/freeze`

调用 A 账户服务示例：

```bash
curl -s -X POST http://localhost:8081/internal/accounts/assets/freeze \
  -H 'Content-Type: application/json' \
  -d '{
    "transferId":"debug-freeze-001",
    "userId":"user-1",
    "assetCode":"USDT",
    "amount":10,
    "direction":"A_TO_B"
  }'
```

资产效果：

- 可用余额减少
- 冻结余额增加

### 3.2 确认扣减冻结金额

- 方法：`POST`
- 路径：`/internal/accounts/assets/confirm-debit`

```bash
curl -s -X POST http://localhost:8081/internal/accounts/assets/confirm-debit \
  -H 'Content-Type: application/json' \
  -d '{
    "transferId":"debug-freeze-001",
    "userId":"user-1",
    "assetCode":"USDT",
    "amount":10,
    "direction":"A_TO_B"
  }'
```

资产效果：

- 冻结余额减少
- 不会恢复到可用余额

### 3.3 取消冻结

- 方法：`POST`
- 路径：`/internal/accounts/assets/cancel-freeze`

```bash
curl -s -X POST http://localhost:8081/internal/accounts/assets/cancel-freeze \
  -H 'Content-Type: application/json' \
  -d '{
    "transferId":"debug-freeze-002",
    "userId":"user-1",
    "assetCode":"USDT",
    "amount":10,
    "direction":"A_TO_B"
  }'
```

资产效果：

- 冻结余额减少
- 可用余额恢复

### 3.4 入账

- 方法：`POST`
- 路径：`/internal/accounts/assets/credit`

调用 B 账户服务示例：

```bash
curl -s -X POST http://localhost:8082/internal/accounts/assets/credit \
  -H 'Content-Type: application/json' \
  -d '{
    "transferId":"debug-credit-001",
    "userId":"user-1",
    "assetCode":"USDT",
    "amount":10,
    "direction":"A_TO_B"
  }'
```

资产效果：

- 可用余额增加

### 3.5 账户接口典型响应

成功且实际执行：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "transferId": "debug-credit-001",
    "operationType": "CREDIT",
    "applied": true,
    "message": "CREDIT success"
  }
}
```

重复调用命中幂等：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "transferId": "debug-credit-001",
    "operationType": "CREDIT",
    "applied": false,
    "message": "CREDIT success"
  }
}
```

余额不足：

```json
{
  "success": false,
  "code": "ACCOUNT_OPERATION_FAILED",
  "message": "insufficient available balance",
  "data": null
}
```

## 4. 推荐调试流程

### 4.1 人工审核转账

1. 创建转账
2. 记录返回的 `transferId`
3. 查询转账单，确认状态为 `WAIT_REVIEW`
4. 审核通过或驳回
5. 再次查询转账单和数据库余额

可直接执行：

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":100,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID

curl -s -X POST http://localhost:8080/transfers/$TRANSFER_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"调试审核通过"}'

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

### 4.2 站内自动转账

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":50,"direction":"A_TO_B","mode":"AUTO_WITHDRAW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

B 到 A 同样支持站内自动完成：

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":50,"direction":"B_TO_A","mode":"AUTO_WITHDRAW"}' \
  | jq -r '.data.transferId')

curl -s http://localhost:8080/transfers/$TRANSFER_ID
```

## 5. 调试时重点关注的状态

| 状态 | 含义 | 调试动作 |
| --- | --- | --- |
| `CREATED` | 转账单已创建，Workflow 已启动。 | 若长时间不推进，检查 Temporal Worker 和 UI。 |
| `INIT_FAILED` | Workflow 启动失败。 | 查看 `last_error_message`，确认 Temporal Server 是否可达。 |
| `FREEZE_FAILED` | 源账户冻结失败。 | 查看账户余额与 `last_error_message`。 |
| `WAIT_REVIEW` | 已冻结，等待审核。 | 查源账户冻结余额是否增加。 |
| `DEBIT_SUCCESS` | 源账户确认扣减成功，目标账户入账进行中。 | 若长时间不推进，检查 Workflow 历史。 |
| `CREDIT_FAILED` | 目标账户入账失败。 | 查目标账户余额、流水和 Workflow 重试历史。 |
| `REJECTED` | 已拒绝且冻结已取消。 | 查源账户可用余额是否恢复。 |
| `SUCCESS` | 整笔转账完成。 | 查源账户扣减和目标账户入账是否都落账。 |

## 6. 排查 SQL

查询转账单：

```sql
SELECT transfer_id, user_id, source_account_type, target_account_type,
       asset_code, amount, transfer_mode, status, last_error_code, last_error_message
FROM transfer_order
ORDER BY id DESC;
```

查询步骤日志：

```sql
SELECT transfer_id, step_name, step_status, error_message, created_at
FROM transfer_step_log
ORDER BY id DESC;
```

查询账户余额：

```sql
SELECT user_id, asset_code, available_amount, frozen_amount, version
FROM account_balance;
```

查询账户流水：

```sql
SELECT transfer_id, operation_type, available_delta, frozen_delta,
       available_after, frozen_after, created_at
FROM finance_ledger
ORDER BY id DESC;
```

查询账户幂等记录：

```sql
SELECT transfer_id, operation_type, status, response_code, response_message, created_at
FROM asset_operation
ORDER BY id DESC;
```

## 7. 常见问题

### 7.1 为什么创建转账后直接失败

常见原因：

- 余额不足
- 账户余额初始化数据不存在

优先看：

- 接口响应中的 `message`
- `transfer_order.last_error_code`
- `transfer_order.last_error_message`

### 7.2 为什么状态长时间没变

当前版本由 Temporal Workflow 推进流程并处理重试。先查询转账单状态，再到 Temporal UI 查看对应 Workflow 的历史事件、Activity 重试和失败原因。

### 7.3 为什么重复调用账户接口没有再次扣款

这是预期行为。账户服务按 `transferId + operationType` 做幂等保护。重复调用时：

- 接口返回成功
- `data.applied=false`
- 余额不会再次变化
