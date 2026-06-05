## Context

`TransferController.create()` 当前执行两步写操作：先调 `stateService.createOrder()` 将订单落库（独立事务），再调 `WorkflowClient.start()` 向 Temporal 发起 Workflow。这两步之间没有原子性保证，中间崩溃会留下 `status=CREATED` 的孤儿订单，与"Workflow 正常运行中"的 `CREATED` 订单在 DB 层面无法区分。

现有终态（`FREEZE_FAILED`、`REJECTED`、`CREDIT_FAILED`）均属 Workflow 运行阶段的失败，Workflow 启动前的失败没有对应终态，是状态机的一个缺口。

## Goals / Non-Goals

**Goals:**
- 新增 `INIT_FAILED` 枚举值，填补 Workflow 启动失败的终态缺口
- 保证 Workflow 启动失败后订单状态可识别、可运营排查
- 不改变正常路径的行为

**Non-Goals:**
- 不实现自动重试或自动补偿（`INIT_FAILED` 是终态，不驱动任何后续流程）
- 不解决 DB 写成功 + Workflow 启动成功 + 返回前崩溃的问题（属于不同层级）
- 不引入 Outbox 模式等复杂架构

## Decisions

### D1：在 Controller 层捕获异常，而非在 Service 层

`WorkflowClient.start()` 调用目前直接在 `TransferController.create()` 里，捕获逻辑放在同一位置最直接，无需抽象。若未来 Workflow 启动逻辑下沉到 Service 层，`markInitFailed` 调用也随之下沉。

**备选**：新建 `WorkflowStartService` 封装启动 + 失败标记——对此规模的变更是过度设计。

### D2：`markInitFailed` 放在 catch 块中，失败后重新抛出异常

```
stateService.createOrder(order);
try {
    WorkflowClient.start(workflow::execute, transferId, mode);
} catch (Exception e) {
    stateService.markInitFailed(transferId, e.getMessage());
    throw new RuntimeException("Workflow 启动失败: " + e.getMessage(), e);
}
```

重新抛出确保 `execute()` 的外层 catch 仍能捕获并返回 `ApiResponse.fail()`，对调用方语义不变。

**备选**：吞掉异常，返回 `INIT_FAILED` 的成功响应——违反"调用方应感知失败"的约定。

### D3：`markInitFailed` 与其他 `markXxx` 方法保持一致风格

新增方法签名：
```java
public void markInitFailed(String transferId, String message)
```
内部调用 `order.markFailure(TransferStatus.INIT_FAILED, "WORKFLOW_START_FAILED", message)`，与 `markFreezeFailed` 等保持对称。

## Risks / Trade-offs

- **`markInitFailed` 本身也可能失败**：如果在 catch 块里调用 `markInitFailed` 时 DB 不可用，订单仍会是孤儿 `CREATED`。这是可接受的极端情况，属于基础设施故障，不在本次范围内处理。→ 无额外缓解措施，依赖监控告警。

- **`INIT_FAILED` 无自动恢复**：运营侧需要人工介入（或未来补充重试工具）才能推进失败订单。→ 当前阶段接受，终态本身已是进步（孤儿可识别）。

## Migration Plan

纯增量变更，无 DDL，无 API breaking change，直接部署即可。
