# 站内自动转账修订 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修正站内 `AUTO_WITHDRAW` 语义，让 A 到 B、B 到 A 都能在冻结成功后由系统自动完成站内划转，并把链上提币作为后续独立能力记录在文档中。

**Architecture:** 继续复用现有 `transfer-service` 编排式 Saga 和账户服务四个幂等资产操作。`MANUAL_REVIEW` 保持先冻结、后审核；`AUTO_WITHDRAW` 改为创建后先冻结，再立即调用源账户 `confirmDebit` 和目标账户 `credit`，失败时进入现有可重试状态。

**Tech Stack:** JDK 8、Spring Boot、Spring Data JPA、OpenFeign、MySQL、JUnit 5、Mockito、AssertJ。

---

## 文件结构

- 修改 `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java`：新增和调整 Saga 单元测试，先锁定 `AUTO_WITHDRAW` 自动完成行为。
- 修改 `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java`：补充 A/B 双向自动完成和失败重试场景。
- 修改 `transfer-service/src/main/java/com/demo/transfer/transfer/service/TransferSagaService.java`：移除 `AUTO_WITHDRAW` 方向限制，冻结成功后自动推进扣减和入账。
- 修改 `common/src/main/java/com/demo/transfer/common/TransferMode.java`：把 `AUTO_WITHDRAW` 注释改成站内自动完成语义。
- 修改 `common/src/main/java/com/demo/transfer/common/TransferStatus.java`：补充 `WITHDRAW_PENDING`/`WITHDRAW_FAILED` 是兼容旧流程状态的说明。
- 修改 `README.md`：把“自动提币转账”改为“站内自动转账”，补充 B 到 A 示例，标注 `/withdraw-result` 为兼容接口。
- 修改 `docs/api/transfer-debug-api.md`：修正接口说明、示例和错误样例。
- 修改 `docs/design/service-implementation-overview.md`：同步状态机、流程和链上提币边界。
- 谨慎修改 `docs/demo/cross-account-transfer-demo.md`：该文件可能有用户本地改动，修改前必须读取当前内容，只做必要中文口径同步。

## Task 1: 写失败测试锁定站内自动完成

**Files:**
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java`
- Test: `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java`

- [ ] **Step 1: 替换旧的自动提币等待测试**

把旧测试：

```java
@Test
void autoWithdrawCreatesFrozenOrderWaitingForWithdrawResult() {
    TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

    assertThat(order.getStatus()).isEqualTo(TransferStatus.WITHDRAW_PENDING);
    verify(accountAClient).freeze(any());
}
```

替换为：

```java
@Test
void autoWithdrawAToBAutomaticallyConfirmsDebitAndCreditsTarget() {
    TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

    assertThat(order.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_A);
    assertThat(order.getTargetAccountType()).isEqualTo(AccountType.ACCOUNT_B);
    verify(accountAClient).freeze(any());
    verify(accountAClient).confirmDebit(any());
    verify(accountBClient).credit(any());
}
```

- [ ] **Step 2: 增加 B 到 A 自动完成测试**

在 `autoWithdrawAToBAutomaticallyConfirmsDebitAndCreditsTarget` 后面新增：

```java
@Test
void autoWithdrawBToAAutomaticallyConfirmsDebitAndCreditsTarget() {
    TransferOrder order = sagaService.createTransfer(request(TransferDirection.B_TO_A, TransferMode.AUTO_WITHDRAW));

    assertThat(order.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_B);
    assertThat(order.getTargetAccountType()).isEqualTo(AccountType.ACCOUNT_A);
    verify(accountBClient).freeze(any());
    verify(accountBClient).confirmDebit(any());
    verify(accountAClient).credit(any());
}
```

- [ ] **Step 3: 增加自动确认扣减失败测试**

在 B 到 A 自动完成测试后面新增：

```java
@Test
void autoWithdrawConfirmDebitFailureLeavesDebitFailed() {
    when(accountAClient.confirmDebit(any())).thenReturn(ApiResponse.fail("DEBIT_TIMEOUT", "确认扣减超时"));

    TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

    assertThat(order.getStatus()).isEqualTo(TransferStatus.DEBIT_FAILED);
    assertThat(order.getLastErrorCode()).isEqualTo("DEBIT_TIMEOUT");
    verify(accountAClient).freeze(any());
    verify(accountAClient).confirmDebit(any());
    verify(accountBClient, org.mockito.Mockito.never()).credit(any());
}
```

- [ ] **Step 4: 运行测试确认失败**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferSagaServiceTest
```

Expected: FAIL。至少应看到旧实现仍返回 `WITHDRAW_PENDING`，并且 `AUTO_WITHDRAW + B_TO_A` 触发 “only supports A_TO_B”。

## Task 2: 实现站内 AUTO_WITHDRAW 自动完成

**Files:**
- Modify: `transfer-service/src/main/java/com/demo/transfer/transfer/service/TransferSagaService.java`
- Test: `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java`

- [ ] **Step 1: 移除方向限制**

删除 `createTransfer` 开头这段代码：

```java
// 基础版自动提现只支持 A 到 B，避免进入未实现分支。
if (TransferMode.AUTO_WITHDRAW == request.getMode() && TransferDirection.A_TO_B != request.getDirection()) {
    throw new IllegalArgumentException("AUTO_WITHDRAW only supports A_TO_B in the basic version");
}
```

- [ ] **Step 2: 调整冻结成功后的分支**

把冻结成功分支：

```java
if (response.isSuccess()) {
    // 冻结成功后，根据模式进入人工审核或等待提现结果两个分支。
    order.markStatus(TransferMode.MANUAL_REVIEW == request.getMode() ? TransferStatus.WAIT_REVIEW
            : TransferStatus.WITHDRAW_PENDING);
    log(order, "FREEZE", "SUCCESS", null);
    LOGGER.info("转账冻结成功，transferId={}, userId={}, sourceAccount={}, nextStatus={}",
            order.getTransferId(), order.getUserId(), source, order.getStatus());
    return orderRepository.saveAndFlush(order);
}
```

改为：

```java
if (response.isSuccess()) {
    log(order, "FREEZE", "SUCCESS", null);
    if (TransferMode.MANUAL_REVIEW == request.getMode()) {
        order.markStatus(TransferStatus.WAIT_REVIEW);
        LOGGER.info("转账冻结成功，等待人工审核，transferId={}, userId={}, sourceAccount={}, nextStatus={}",
                order.getTransferId(), order.getUserId(), source, order.getStatus());
        return orderRepository.saveAndFlush(order);
    }
    LOGGER.info("站内自动转账冻结成功，开始自动确认扣减，transferId={}, userId={}, sourceAccount={}",
            order.getTransferId(), order.getUserId(), source);
    orderRepository.saveAndFlush(order);
    return approve(order);
}
```

- [ ] **Step 3: 更新 `handleWithdrawResult` 注释**

把方法注释附近的旧语义改成兼容语义：

```java
// 该接口保留用于兼容仍停留在 WITHDRAW_PENDING 的历史流程；新建站内自动转账通常不会再进入该状态。
```

- [ ] **Step 4: 运行 Saga 测试确认通过**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferSagaServiceTest
```

Expected: PASS。

- [ ] **Step 5: 提交 Saga 行为修正**

Run:

```bash
git add transfer-service/src/main/java/com/demo/transfer/transfer/service/TransferSagaService.java transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java
git commit -m "fix: 修正站内自动转账流程"
```

## Task 3: 补充集成场景测试

**Files:**
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java`
- Test: `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java`

- [ ] **Step 1: 替换旧自动提币成功测试**

把旧测试：

```java
@Test
void scenarioThreeAutoWithdrawSuccessCompletesTransfer() {
    TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

    TransferOrder result = sagaService.handleWithdrawResult(order.getTransferId(), true, "withdraw success");

    assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    verify(accountAClient).confirmDebit(any());
    verify(accountBClient).credit(any());
}
```

替换为：

```java
@Test
void scenarioThreeAutoWithdrawAToBCompletesTransferWithoutCallback() {
    TransferOrder result = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

    assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    verify(accountAClient).freeze(any());
    verify(accountAClient).confirmDebit(any());
    verify(accountBClient).credit(any());
}
```

- [ ] **Step 2: 新增 B 到 A 自动完成集成测试**

在 A 到 B 自动完成测试后新增：

```java
@Test
void scenarioThreeAutoWithdrawBToACompletesTransferWithoutCallback() {
    TransferOrder result = sagaService.createTransfer(request(TransferDirection.B_TO_A, TransferMode.AUTO_WITHDRAW));

    assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    verify(accountBClient).freeze(any());
    verify(accountBClient).confirmDebit(any());
    verify(accountAClient).credit(any());
}
```

- [ ] **Step 3: 保留旧回调失败兼容测试**

把旧的 `scenarioThreeAutoWithdrawFailureCancelsFreeze` 改名为：

```java
@Test
void legacyWithdrawResultFailureCancelsPendingFreeze() {
    when(accountAClient.confirmDebit(any())).thenReturn(ApiResponse.fail("LEGACY_PENDING", "兼容旧流程"));
    TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));
    order.markStatus(TransferStatus.WITHDRAW_PENDING);

    TransferOrder result = sagaService.handleWithdrawResult(order.getTransferId(), false, "withdraw failed");

    assertThat(result.getStatus()).isEqualTo(TransferStatus.REJECTED);
    verify(accountAClient).cancelFreeze(any());
    verify(accountBClient, never()).credit(any());
}
```

说明：先让自动流程停在 `DEBIT_FAILED`，再手动把状态改为 `WITHDRAW_PENDING`，用于覆盖兼容接口的失败分支。

- [ ] **Step 4: 新增自动入账失败和重试测试**

在 `targetCreditFailureLeavesCreditFailedAndRetryOnlyCreditsTarget` 前新增：

```java
@Test
void autoWithdrawTargetCreditFailureLeavesCreditFailedAndRetryOnlyCreditsTarget() {
    when(accountBClient.credit(any()))
            .thenReturn(ApiResponse.fail("TIMEOUT", "credit timeout"))
            .thenReturn(ok(OperationType.CREDIT));

    TransferOrder failed = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));
    TransferOrder retried = retryService.retryOne(failed.getTransferId());

    assertThat(failed.getStatus()).isEqualTo(TransferStatus.CREDIT_FAILED);
    assertThat(retried.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    verify(accountAClient).confirmDebit(any());
    verify(accountBClient, org.mockito.Mockito.times(2)).credit(any());
}
```

- [ ] **Step 5: 运行集成场景测试**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferScenarioIntegrationTest
```

Expected: PASS。

- [ ] **Step 6: 提交集成测试**

Run:

```bash
git add transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java
git commit -m "test: 覆盖双向站内自动转账"
```

## Task 4: 更新公共枚举注释和中文文档

**Files:**
- Modify: `common/src/main/java/com/demo/transfer/common/TransferMode.java`
- Modify: `common/src/main/java/com/demo/transfer/common/TransferStatus.java`
- Modify: `README.md`
- Modify: `docs/api/transfer-debug-api.md`
- Modify: `docs/design/service-implementation-overview.md`
- Modify: `docs/demo/cross-account-transfer-demo.md`

- [ ] **Step 1: 更新 `TransferMode` 注释**

把 `AUTO_WITHDRAW` 注释改为：

```java
/** 冻结成功后由系统自动完成站内划转。 */
AUTO_WITHDRAW
```

- [ ] **Step 2: 更新 `TransferStatus` 注释**

把 `WITHDRAW_PENDING` 和 `WITHDRAW_FAILED` 注释改为：

```java
/** 兼容旧自动提现流程：冻结成功，等待自动提现结果。 */
WITHDRAW_PENDING,
/** 兼容旧自动提现流程：自动提现失败。 */
WITHDRAW_FAILED,
```

- [ ] **Step 3: 更新 README 示例**

把 “创建 A -> B 自动提币转账” 改为 “创建 A -> B 站内自动转账”，并新增：

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"B_TO_A","mode":"AUTO_WITHDRAW"}'
```

把 “提交自动提币结果” 改为 “提交兼容旧流程的自动提币结果”，并说明新建站内自动转账通常不需要调用该接口。

- [ ] **Step 4: 更新接口调试文档**

在 `docs/api/transfer-debug-api.md` 中修改创建转账说明：

```markdown
- `MANUAL_REVIEW`：冻结成功后进入 `WAIT_REVIEW`。
- `AUTO_WITHDRAW`：站内自动完成模式，支持 `A_TO_B` 和 `B_TO_A`；冻结成功后系统会主动确认扣减并给目标账户入账。
```

删除或替换错误样例中 “AUTO_WITHDRAW only supports A_TO_B in the basic version”。

把 `/withdraw-result` 标题改成：

```markdown
### 2.3 兼容旧流程的提现结果回调
```

- [ ] **Step 5: 更新设计总览**

在 `docs/design/service-implementation-overview.md` 中把 “基础版只允许 `A_TO_B + AUTO_WITHDRAW`” 改为：

```markdown
站内 `AUTO_WITHDRAW` 支持 `A_TO_B` 和 `B_TO_A`。它表示系统自动完成站内划转，不表示链上提币。
```

把自动流程图改为：

```text
POST /transfers
  -> source.freeze()
  -> source.confirmDebit()
  -> target.credit()
  -> transfer_order = SUCCESS
```

- [ ] **Step 6: 谨慎更新演示文档**

先运行：

```bash
git diff -- docs/demo/cross-account-transfer-demo.md
```

如果该文件存在用户未提交改动，只修改自动转账相关段落，不重排整篇文档。需要把 “自动提币” 改为 “站内自动转账”，并新增 B 到 A 自动完成演示。

- [ ] **Step 7: 搜索旧口径**

Run:

```bash
rg -n "only supports A_TO_B|只支持 `A_TO_B`|自动提币转账|等待提现结果|WITHDRAW_PENDING" README.md docs common transfer-service
```

Expected: 只允许在兼容旧流程说明、状态枚举或历史计划/spec 中出现 `WITHDRAW_PENDING`；不应再有站内自动转账只支持 A 到 B 的描述。

- [ ] **Step 8: 提交文档和注释**

Run:

```bash
git add common/src/main/java/com/demo/transfer/common/TransferMode.java common/src/main/java/com/demo/transfer/common/TransferStatus.java README.md docs/api/transfer-debug-api.md docs/design/service-implementation-overview.md docs/demo/cross-account-transfer-demo.md
git commit -m "docs: 统一站内自动转账中文口径"
```

## Task 5: 最终验证

**Files:**
- Verify only.

- [ ] **Step 1: 运行全部测试**

Run:

```bash
mvn -q test -DfailIfNoTests=false
```

Expected: PASS。

- [ ] **Step 2: 检查中文文档口径**

Run:

```bash
rg -n "AUTO_WITHDRAW only supports|only supports A_TO_B|automatic withdrawal transfer|Create A to B automatic withdrawal|Submit automatic withdrawal result" README.md docs
```

Expected: no matches。

- [ ] **Step 3: 检查工作区**

Run:

```bash
git status --short
```

Expected: no output。

## 自检结果

- Spec 覆盖：本计划覆盖双向 `AUTO_WITHDRAW`、自动完成、失败重试、兼容旧 `/withdraw-result`、链上提币边界文档和中文口径同步。
- 清晰度检查：计划中没有未完成项或含糊描述。
- 类型一致性：沿用现有 `TransferMode.AUTO_WITHDRAW`、`TransferDirection.A_TO_B/B_TO_A`、`TransferStatus` 和 `TransferSagaService` 方法，不新增未定义类型。
