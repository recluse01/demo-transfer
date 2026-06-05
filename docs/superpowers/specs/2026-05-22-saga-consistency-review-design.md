# Saga 一致性架构评审与修复设计

## 背景

本文档记录对 `demo-transfer` 项目的事务一致性与 Saga 设计的系统性评审结果，并给出各问题的修复方向。

评审时间：2026-05-22  
评审范围：`TransferSagaService`、`AccountAssetService`、`TransferRetryService`、`TransferRetryScheduler`、状态机。

---

## 发现汇总

| 编号 | 问题 | 优先级 | 影响范围 |
|------|------|--------|----------|
| F1 | Feign 调用在本地事务内，存在孤立冻结风险 | **高** | 资产一致性 |
| F2 | 幂等检查在行锁之外，依赖 DB 约束兜底 | **高** | 幂等正确性 |
| F3 | DB 连接在 Feign 调用期间持续占用 | 中 | 连接池稳定性 |
| F4 | 调度器并发引入乐观锁噪音，日志难区分 | 中 | 可观测性 |
| F5 | `AccountBalance` 同时持有乐观锁与悲观锁 | 低 | 无实际风险 |
| F6 | 状态机缺少统一的跳转守卫 | 低 | 未来可维护性 |

---

## F1：Feign 调用在本地事务内，存在孤立冻结风险

### 问题描述

`TransferSagaService.createTransfer` 被 `@Transactional` 包裹，但方法内包含对账户服务的 Feign 调用：

```
@Transactional createTransfer:
  orderRepository.save(order)          ← DB 写，在事务中
  router.client(source).freeze(...)    ← Feign 调用，不在本地事务中
  orderRepository.saveAndFlush(order)  ← DB 写，在事务中
```

**风险场景**：Feign `freeze` 请求成功（账户资产已冻结），但随后本地 DB 提交失败（DB 宕机、连接超时等），整个事务回滚。结果：账户服务的冻结已落地，但 `transfer_order` 根本不存在。这笔冻结资产无对应转账单可追踪，也无法通过重试恢复（`transferId` 是每次 `createTransfer` 生成的新 UUID，不会重用）。

`review`、`handleWithdrawResult` 有相同的结构性问题：Feign 调用与状态更新共享 `@Transactional` 边界。

### 修复方向

将 `createTransfer` 拆分为两个独立事务：

1. **事务 T1（先提交）**：创建 `transfer_order`，状态 `CREATED`，持久化成功后提交。
2. **事务 T2（T1 提交后）**：执行 Feign `freeze`，根据结果更新状态。

这样即使 T2 失败，T1 已提交的 `transfer_order`（`CREATED` 状态）可以被扫描到并由重试机制重新推进。

对 `review`、`handleWithdrawResult` 同理：状态读取与 Feign 调用分离，不能让 Feign 调用处于一个会持有 DB 写锁的长事务中。

**注意**：T1 和 T2 之间存在短暂窗口（T1 提交、T2 尚未开始），此时应用崩溃会留下 `CREATED` 状态的孤儿单。这需要重试调度器同时扫描 `CREATED` 状态，并尝试继续推进（幂等保证不会重复冻结）。

---

## F2：幂等检查在行锁之外，依赖 DB 约束兜底

### 问题描述

`AccountAssetService.apply` 的执行顺序：

```java
// Step 1: 无锁读，幂等检查
AssetOperation existing = operationRepository.findByTransferIdAndOperationType(...);
if (existing != null) { return applied=false; }

// Step 2: 获取 FOR UPDATE 行锁
AccountBalance balance = balanceRepository.findByUserIdAndAssetCodeForUpdate(...);

// Step 3-5: 修改余额、写流水、写幂等记录
```

**并发场景**（同一 `transferId + operationType` 两个并发请求）：

- 请求 A、B 均在 Step 1 看到 `existing == null`（幂等检查通过）
- A 获取行锁，B 等待
- A 完成 Step 3–5，提交，释放锁
- B 获取行锁，**重新执行 mutate**（余额被再次修改），在 Step 5 写 `asset_operation` 时触发唯一约束异常，事务回滚

净效果在余额层面是正确的（B 的修改被回滚），但 B 抛出的 `DataIntegrityViolationException` 会以 HTTP 500 返回给 `transfer-service`，后者将状态标记为 `FAILED` 并触发重试——而实际操作已经成功。这产生了一次虚假失败和一次冗余重试。

### 修复方向

将幂等检查移到 `FOR UPDATE` 之后：

```java
// 先加锁
AccountBalance balance = balanceRepository.findByUserIdAndAssetCodeForUpdate(...);

// 再做幂等检查（此时已串行化）
AssetOperation existing = operationRepository.findByTransferIdAndOperationType(...);
if (existing != null) { return applied=false; }

// 然后修改余额、写流水、写幂等记录
```

加锁后再检查幂等，并发请求会被串行化，第二个请求在加锁后会看到 `existing != null`，直接返回 `applied=false`，不会触发约束异常，也不会向上游产生误报。

---

## F3：DB 连接在 Feign 调用期间持续占用

### 问题描述

`createTransfer`、`review`、`handleWithdrawResult` 均被 `@Transactional` 包裹，方法内包含 Feign 调用。Spring 的 `@Transactional` 在方法开始时从连接池获取连接，方法结束（或事务提交）时归还。因此：

- Feign 调用期间（可能有 10s+ 超时），DB 连接一直被持有
- 高并发或账户服务响应慢时，连接池成为瓶颈

`TransferRetryService.retryOne` 被 `@Transactional` 标注，内部调用 `sagaService.approve`，后者包含两次 Feign 调用（`confirmDebit` + `credit`），整个重试期间 DB 连接被持占。

### 修复方向

将每次 Feign 调用和对应的状态更新提取为独立的、短暂的事务单元（与 F1 的拆分方案一致）。典型模式：

1. 事务 T_read：读取当前状态
2. 执行 Feign 调用（事务外）
3. 事务 T_write：根据 Feign 结果更新状态

这样 DB 连接仅在读写阶段持有，Feign 网络 IO 期间完全释放。

---

## F4：调度器并发产生乐观锁噪音

### 问题描述

多实例部署时，每个实例的 `TransferRetryScheduler` 都会扫描 `DEBIT_FAILED` 等失败状态订单并重试。两个实例可能同时对同一笔转账调用 Feign（均幂等），但最终只有一个实例能成功提交 `transfer_order` 的状态更新（另一个抛出 `OptimisticLockException`，被第 34 行的 `catch (RuntimeException)` 吞掉并记录为 `WARN`）。

**结果**：乐观锁冲突（正常、无损的并发保护）与真实业务失败（账户余额不足、Feign 超时等）混在同一 `WARN` 日志中，运维无法区分。

### 修复方向

在 `retryFailedSteps` 的 catch 块中区分异常类型：

- `OptimisticLockException` / `ObjectOptimisticLockingFailureException`：记录 `DEBUG`，说明是并发竞争，非真实失败
- 其他 `RuntimeException`：保留 `WARN` 级别

可进一步为重试调度引入"错开扫描"机制（如数据库行级锁或实例标签），减少无效竞争，但这属于生产化改造，不在当前基础版范围内。

---

## F5：`AccountBalance` 同时持有乐观锁与悲观锁（冗余）

### 问题描述

`AccountBalance` 有 `@Version` 乐观锁字段，`AccountBalanceRepository` 的余额修改查询使用 `PESSIMISTIC_WRITE`（`FOR UPDATE`）。悲观锁已保证同一事务周期内只有一个写入者，乐观锁在这个上下文中实际不会触发冲突，是冗余的。

### 修复方向

保留 `@Version` 字段（DB schema 不需要变动），但不需要专门依赖它作为并发控制机制。如果未来引入不加悲观锁的读改写路径，再重新评估乐观锁的必要性。**此项不需要代码修改**。

---

## F6：状态机缺少统一的跳转守卫

### 问题描述

`requireStatus` 检查只在 `review` 和 `handleWithdrawResult` 中使用。`approve`、`creditTarget`、`cancel` 是 package-private 方法，无前置状态断言，依赖调用方（`retryOne` 的状态路由逻辑）保证正确性。当前调用路径安全，但若后续新增调用方或 Saga 分支，容易出现状态机非预期跳转。

### 修复方向

在 `approve`、`creditTarget`、`cancel` 方法开头加上合法的前置状态集合断言，例如：

```java
// approve 的合法前置状态
requireStatusIn(order, EnumSet.of(WAIT_REVIEW, DEBIT_FAILED));
```

这将状态机的转换规则从"调用方的隐式约定"变为"方法内的显式断言"，降低维护成本。

---

## 修复优先级建议

**第一阶段（高优）**：修复 F1 和 F2，这两项涉及资产安全和幂等正确性。

- F2 改动最小，只调整 `AccountAssetService.apply` 中幂等检查的位置，风险低，建议优先实施。
- F1 需要拆分事务，涉及 `TransferSagaService` 的结构性调整，同时需要扩展重试调度器以处理 `CREATED` 状态，建议单独一个 PR。

**第二阶段（中优）**：F3 与 F1 的修复方案高度重合（拆分事务后自然解决），F4 可在日志层面独立修复。

**第三阶段（低优）**：F6 建议在下一次功能开发中顺带完成，F5 不需要修改。

---

## 当前版本一致性保证边界

修复前，当前版本的保证是：

- **账户库内**：单库本地事务 + 幂等记录保证余额最终一致（主路径正确）
- **跨服务**：Saga 状态机 + 调度重试保证最终推进（在无 DB 崩溃的前提下）
- **并发**：`FOR UPDATE` 防止余额并发修改；乐观锁防止 `transfer_order` 并发覆盖

**修复后**增加的保证：

- 即使 DB 在 Feign 调用后崩溃，`transfer_order` 仍可通过重试调度继续推进（F1）
- 幂等命中不会产生误报失败，减少无效重试（F2）
- DB 连接不被 Feign 网络 IO 占用（F3）
