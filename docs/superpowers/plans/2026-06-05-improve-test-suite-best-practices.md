---
change: improve-test-suite-best-practices
design-doc: docs/superpowers/specs/2026-06-05-improve-test-suite-best-practices-design.md
base-ref: 8199362801655a6c5c2baea8327a550d06f9d7ab
---

# Improve Test Suite Best Practices Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the Spring Boot transfer demo test suite to industry-grade layered coverage for HTTP contracts, Temporal orchestration, account invariants, idempotency, precision, and maintainability.

**Architecture:** Keep the existing Maven fast/full split. Use `*Test` for fast WebMvc, workflow branch, activity, and service tests; use `*IT` only for MySQL/Testcontainers, WireMock, Temporal + DB scenario, and concurrency boundaries. Add small test-source fixtures so assertions stay explicit and repeated setup stays consistent.

**Tech Stack:** Java 8, JUnit 5, AssertJ, Mockito, Spring MockMvc, Spring Data JPA test slices, WireMock, Testcontainers MySQL, Temporal `TestWorkflowEnvironment`, Maven surefire/failsafe, JaCoCo.

---

## File Map

- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferTestData.java` for transfer IDs, users, orders, create requests, review requests.
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/ApiResponseJson.java` for account-service compatible `ApiResponse` JSON in WireMock and scenario stubs.
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferWorkflowTestHarness.java` for `TestWorkflowEnvironment` lifecycle.
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerTest.java` to become a true `@WebMvcTest` + MockMvc contract test.
- Create or modify: `transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerWorkflowStartFailureTest.java` for `INIT_FAILED` persistence when Workflow start fails after order creation.
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java` by replacing the commented body with current Temporal scenario tests.
- Create: `account-service/src/test/java/com/demo/transfer/account/support/AccountTestData.java` for account IDs, balances, and asset operation requests.
- Modify: `account-service/src/test/java/com/demo/transfer/account/service/AccountAssetServiceTest.java` for fast account invariant coverage.
- Modify: `account-service/src/test/java/com/demo/transfer/account/support/AccountAssetIdempotencyIT.java` for MySQL duplicate and concurrent idempotency coverage.
- Modify: `account-service/src/test/java/com/demo/transfer/account/support/AccountAmountPrecisionIT.java` for DECIMAL scale and invalid amount boundaries.
- Modify: `account-service/src/test/java/com/demo/transfer/account/web/AccountAssetControllerTest.java` for endpoint validation and failure envelope completeness.
- Modify: `docs/design/testing-strategy.md` to document the resulting test layout and command split.
- Modify: `openspec/changes/improve-test-suite-best-practices/tasks.md` only when implementation evidence exists.

## Task 1: Transfer Test Fixtures

**Files:**
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferTestData.java`
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/ApiResponseJson.java`
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferWorkflowTestHarness.java`

- [ ] **Step 1: Add a focused transfer data helper**

Create `TransferTestData` with these methods and keep all values deterministic except generated IDs:

```java
package com.demo.transfer.transfer.support;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.web.CreateTransferRequest;
import com.demo.transfer.transfer.web.ReviewTransferRequest;
import java.math.BigDecimal;
import java.util.UUID;

public final class TransferTestData {
    private TransferTestData() {
    }

    public static String transferId(String suffix) {
        return "txn-test-" + suffix + "-" + UUID.randomUUID();
    }

    public static String userId(String suffix) {
        return "user-test-" + suffix + "-" + UUID.randomUUID();
    }

    public static CreateTransferRequest createRequest(String amount, TransferMode mode) {
        return new CreateTransferRequest(userId("transfer"), "USDT", new BigDecimal(amount),
                TransferDirection.A_TO_B, mode);
    }

    public static ReviewTransferRequest reviewRequest(String transferId, boolean approved) {
        return new ReviewTransferRequest(transferId, approved, approved ? "approved" : "rejected");
    }

    public static TransferOrder order(String transferId, TransferMode mode) {
        return TransferOrder.create(transferId, userId("order"), AccountType.ACCOUNT_A, AccountType.ACCOUNT_B,
                "USDT", new BigDecimal("10.00000000"), mode);
    }
}
```

- [ ] **Step 2: Add account response JSON helper**

Create `ApiResponseJson`:

```java
package com.demo.transfer.transfer.support;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ApiResponseJson {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApiResponseJson() {
    }

    public static String success(OperationType operationType) {
        return write(ApiResponse.ok(new AssetOperationResponse("transfer", operationType, true, "success")));
    }

    public static String failure(String code, String message) {
        return write(ApiResponse.fail(code, message));
    }

    private static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write test JSON", ex);
        }
    }
}
```

- [ ] **Step 3: Add Temporal harness helper**

Create `TransferWorkflowTestHarness`:

```java
package com.demo.transfer.transfer.support;

import com.demo.transfer.transfer.workflow.TransferActivities;
import com.demo.transfer.transfer.workflow.TransferWorkflow;
import com.demo.transfer.transfer.workflow.TransferWorkflowImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;

public final class TransferWorkflowTestHarness implements AutoCloseable {
    private final String taskQueue;
    private final TestWorkflowEnvironment environment;

    public TransferWorkflowTestHarness(String taskQueue, TransferActivities activities) {
        this.taskQueue = taskQueue;
        this.environment = TestWorkflowEnvironment.newInstance();
        Worker worker = environment.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();
    }

    public TransferWorkflow workflow(String transferId) {
        return environment.getWorkflowClient().newWorkflowStub(
                TransferWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(transferId)
                        .setTaskQueue(taskQueue)
                        .build());
    }

    public void await(String transferId) {
        environment.getWorkflowClient().newUntypedWorkflowStub(transferId).getResult(Void.class);
    }

    @Override
    public void close() {
        environment.close();
    }
}
```

- [ ] **Step 4: Run fixture compile check**

Run:

```bash
mvn -q -pl transfer-service -DskipTests test-compile
```

Expected: test sources compile. If `test-compile` exposes Java 8 incompatibilities, fix helper code before continuing.

- [ ] **Step 5: Commit fixtures**

```bash
git add transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferTestData.java transfer-service/src/test/java/com/demo/transfer/transfer/support/ApiResponseJson.java transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferWorkflowTestHarness.java
git commit -m "test: 增加转账测试夹具"
```

## Task 2: Transfer Web Contract Tests

**Files:**
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerTest.java`
- Create or modify: `transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerWorkflowStartFailureTest.java`

- [ ] **Step 1: Replace direct controller invocation with WebMvcTest**

Refactor `TransferControllerTest` to use:

```java
@WebMvcTest(TransferController.class)
class TransferControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferOrderStateService stateService;

    @MockBean
    private WorkflowClient workflowClient;

    @MockBean
    private AccountClientRouter router;
}
```

Remove `@DataJpaTest`, repository imports, `TestWorkflowEnvironment`, and direct `controller.create(request)` calls from this class.

- [ ] **Step 2: Add create success contract test**

Add a test named `createReturnsOkEnvelopeAndStartsWorkflow`. Arrange `router.sourceType(A_TO_B)` to return `ACCOUNT_A`, `router.targetType(A_TO_B)` to return `ACCOUNT_B`, and `workflowClient.newWorkflowStub(eq(TransferWorkflow.class), any(WorkflowOptions.class))` to return a mock `TransferWorkflow`.

Assert with MockMvc:

```java
mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferTestData.createRequest("10.00000000",
                        TransferMode.MANUAL_REVIEW))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.transferId").isNotEmpty())
        .andExpect(jsonPath("$.data.status").value("CREATED"));
```

Verify `stateService.createOrder(any(TransferOrder.class))` and `workflowClient.newWorkflowStub(eq(TransferWorkflow.class), any(WorkflowOptions.class))`.

- [ ] **Step 3: Add create validation contract test**

Send a request with blank `userId` and amount `0`. Assert 400 and:

```java
verifyNoInteractions(stateService, workflowClient);
```

This proves invalid HTTP input stops before business orchestration.

- [ ] **Step 4: Add review signal contract test**

Mock `workflowClient.newWorkflowStub(TransferWorkflow.class, transferId)` to return a mock workflow. Mock `stateService.loadOrder(transferId)` to return `TransferTestData.order(transferId, TransferMode.MANUAL_REVIEW)`.

Perform:

```java
mockMvc.perform(post("/transfers/{transferId}/review", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TransferTestData.reviewRequest(transferId, true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.transferId").value(transferId));
```

Verify `workflow.review(argThat(decision -> decision.isApproved()))`.

- [ ] **Step 5: Add get contract test**

Mock `stateService.loadOrder(transferId)` and assert `GET /transfers/{transferId}` returns `success=true`, `code=OK`, and the expected `transferId`.

- [ ] **Step 6: Add Workflow start failure persistence test outside WebMvc**

Create `TransferControllerWorkflowStartFailureTest` using the current `@DataJpaTest` style from the old controller test. Keep only the scenario that creates an order, makes `workflowClient.newWorkflowStub(eq(TransferWorkflow.class), any(WorkflowOptions.class))` throw, calls `controller.create(request)`, and asserts the repository row is `INIT_FAILED` with `WORKFLOW_START_FAILED`.

Expected assertion core:

```java
TransferOrder order = orderRepository.findAll().get(0);
assertThat(order.getStatus()).isEqualTo(TransferStatus.INIT_FAILED);
assertThat(order.getLastErrorCode()).isEqualTo("WORKFLOW_START_FAILED");
assertThat(order.getLastErrorMessage()).contains("workflow发送失败");
```

- [ ] **Step 7: Run targeted transfer web tests**

```bash
mvn -q -pl transfer-service -Dtest=TransferControllerTest,TransferControllerWorkflowStartFailureTest test
```

Expected: both test classes pass.

- [ ] **Step 8: Commit transfer web tests**

```bash
git add transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerTest.java transfer-service/src/test/java/com/demo/transfer/transfer/web/TransferControllerWorkflowStartFailureTest.java
git commit -m "test: 完善转账接口契约测试"
```

## Task 3: Temporal Scenario Integration Tests

**Files:**
- Modify: `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java`
- Reuse: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferTestData.java`
- Reuse: `transfer-service/src/test/java/com/demo/transfer/transfer/support/TransferWorkflowTestHarness.java`

- [ ] **Step 1: Replace the commented class with active current-code tests**

Use `@DataJpaTest`, `@ContextConfiguration`, `@Transactional(propagation = Propagation.NOT_SUPPORTED)`, real `TransferActivitiesImpl`, real `TransferOrderStateService`, real `TransferOrderRepository`, and mocked `AccountAClient` / `AccountBClient`.

The class-level setup should mirror the current commented intent but use unique IDs and `TransferWorkflowTestHarness`.

- [ ] **Step 2: Add happy-path scenarios**

Add these tests with explicit setup and terminal assertions:

- `manualReviewApprovalCompletesTransfer`: create a `MANUAL_REVIEW` order, stub freeze/confirmDebit/credit success, start Workflow, send approved review signal, wait for completion, reload the order, assert `SUCCESS`.
- `manualReviewRejectionCancelsFreezeAndMarksRejected`: create a `MANUAL_REVIEW` order, stub freeze/cancelFreeze success, start Workflow, send rejected review signal, wait for completion, reload the order, assert `REJECTED`, verify confirmDebit and credit were never called.
- `autoWithdrawCompletesWithoutReview`: create an `AUTO_WITHDRAW` order, stub freeze/confirmDebit/credit success, execute Workflow, reload the order, assert `SUCCESS`, verify no review signal is required.

Each test must create a persisted order through `stateService.createOrder(order)`, start the workflow through the harness, wait for completion, then reload from repository and assert the terminal status.

- [ ] **Step 3: Add failure scenarios**

Add `freezeFailureMarksFreezeFailedAndStopsBeforeDebit`: make source client `freeze` return `ApiResponse.fail("INSUFFICIENT_BALANCE", "余额不足")`; run an auto-withdraw workflow; assert the reloaded order is `FREEZE_FAILED`; verify source `confirmDebit`, target `credit`, and source `cancelFreeze` are never called.

Add `creditFailureLeavesCreditFailedWithoutCancelFreeze`: make source `freeze` and `confirmDebit` succeed, make target client `credit` fail; run an auto-withdraw workflow until it reports failure after retries; assert the reloaded order is `CREDIT_FAILED`; verify source `confirmDebit` is called at least once and source `cancelFreeze` is never called.

- [ ] **Step 4: Run targeted scenario test**

```bash
mvn -q -pl transfer-service -Dtest=TransferScenarioIntegrationTest test
```

Expected: active scenario test class passes. If Temporal retry makes a failure scenario slow, reduce only test-local retry options through the harness or test Workflow options without changing production RetryPolicy.

- [ ] **Step 5: Commit scenario tests**

```bash
git add transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java
git commit -m "test: 恢复 Temporal 转账场景测试"
```

## Task 4: Account Fixtures and Invariant Tests

**Files:**
- Create: `account-service/src/test/java/com/demo/transfer/account/support/AccountTestData.java`
- Modify: `account-service/src/test/java/com/demo/transfer/account/service/AccountAssetServiceTest.java`
- Modify: `account-service/src/test/java/com/demo/transfer/account/web/AccountAssetControllerTest.java`

- [ ] **Step 1: Add account test data helper**

Create:

```java
package com.demo.transfer.account.support;

import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.TransferDirection;
import java.math.BigDecimal;
import java.util.UUID;

public final class AccountTestData {
    private AccountTestData() {
    }

    public static String transferId(String suffix) {
        return "acct-test-" + suffix + "-" + UUID.randomUUID();
    }

    public static String userId(String suffix) {
        return "acct-user-" + suffix + "-" + UUID.randomUUID();
    }

    public static AssetOperationRequest request(String transferId, String userId, String amount) {
        return new AssetOperationRequest(transferId, userId, "USDT", new BigDecimal(amount), TransferDirection.A_TO_B);
    }
}
```

- [ ] **Step 2: Expand fast account service invariants**

In `AccountAssetServiceTest`, add or keep tests that explicitly cover:

- Freeze rejects insufficient available balance.
- Confirm debit rejects when no freeze exists.
- Cancel freeze restores available balance and clears frozen balance.
- Credit increases target available balance exactly by request amount.
- Duplicate idempotency key returns `applied=false` and does not duplicate ledger effect.

Use existing repositories and service setup in the file. Do not introduce `@SpringBootTest`.

- [ ] **Step 3: Complete account web endpoint coverage**

In `AccountAssetControllerTest`, ensure all four endpoints have:

- one success envelope assertion,
- one validation rejection assertion where invalid input does not touch the matching service method,
- one business failure envelope assertion where service throws and HTTP remains 200 with `success=false`.

Use parameterized helper methods if they reduce duplication without hiding endpoint names from the test.

- [ ] **Step 4: Run targeted account fast tests**

```bash
mvn -q -pl account-service -Dtest=AccountAssetServiceTest,AccountAssetControllerTest test
```

Expected: targeted fast tests pass without Docker.

- [ ] **Step 5: Commit account fast tests**

```bash
git add account-service/src/test/java/com/demo/transfer/account/support/AccountTestData.java account-service/src/test/java/com/demo/transfer/account/service/AccountAssetServiceTest.java account-service/src/test/java/com/demo/transfer/account/web/AccountAssetControllerTest.java
git commit -m "test: 补强账户快速轨资金不变量"
```

## Task 5: MySQL Idempotency, Concurrency, and Precision Tests

**Files:**
- Modify: `account-service/src/test/java/com/demo/transfer/account/support/AccountAssetIdempotencyIT.java`
- Modify: `account-service/src/test/java/com/demo/transfer/account/support/AccountAmountPrecisionIT.java`

- [ ] **Step 1: Add duplicate idempotency proof on real MySQL**

In `AccountAssetIdempotencyIT`, add a test that calls the same operation twice with the same `transferId` and operation type, then asserts:

- first response has `applied=true`,
- second response has `applied=false`,
- account balances changed once,
- operation log count for the idempotency key is 1.

- [ ] **Step 2: Add concurrent same-key proof on real MySQL**

Use `ExecutorService`, `CountDownLatch`, and a timeout. Start at least 8 workers calling the same `freeze` request. Assert exactly one response has `applied=true`, all successful duplicate responses have `applied=false`, and database balances changed once.

Do not assert which worker wins.

- [ ] **Step 3: Expand precision and invalid amount boundaries**

In `AccountAmountPrecisionIT`, assert `10.12345678` remains scale-compatible through freeze/debit/credit flows. Add zero and negative amount rejection tests if not already covered in fast tests against the service boundary.

- [ ] **Step 4: Run targeted full-track account tests**

```bash
mvn -q -pl account-service -Dtest=AccountAssetIdempotencyIT,AccountAmountPrecisionIT test
```

Expected: tests pass when Docker/Testcontainers MySQL is available. If Docker is unavailable, record the exact environment failure and continue only after the user provides a Docker-capable environment.

- [ ] **Step 5: Commit account full-track tests**

```bash
git add account-service/src/test/java/com/demo/transfer/account/support/AccountAssetIdempotencyIT.java account-service/src/test/java/com/demo/transfer/account/support/AccountAmountPrecisionIT.java
git commit -m "test: 验证账户幂等并发与金额精度"
```

## Task 6: Documentation, OpenSpec Tasks, and Verification

**Files:**
- Modify: `docs/design/testing-strategy.md`
- Modify: `openspec/changes/improve-test-suite-best-practices/tasks.md`

- [ ] **Step 1: Update testing strategy documentation**

Document the final layout:

- WebMvc contract tests for transfer/account controllers.
- Workflow branch tests and Activity state tests.
- Temporal scenario integration tests.
- MySQL idempotency/concurrency/precision `*IT` tests.
- `mvn test` as fast track, `mvn verify` as Docker-backed full track with JaCoCo.

- [ ] **Step 2: Run fast track**

```bash
mvn -q test -DfailIfNoTests=false
```

Expected: all `*Test` classes pass without Docker.

- [ ] **Step 3: Run full track when Docker is available**

```bash
mvn -q verify -DfailIfNoTests=false
```

Expected: `*Test`, `*IT`, Testcontainers MySQL, and JaCoCo checks pass.

- [ ] **Step 4: Run OpenSpec validation**

```bash
openspec validate --all --strict
```

Expected: all specs and active changes validate.

- [ ] **Step 5: Mark implementation tasks complete only after evidence exists**

Update `openspec/changes/improve-test-suite-best-practices/tasks.md` by changing each implemented checkbox from `- [ ]` to `- [x]`. Do not mark tasks complete for tests or commands that were not actually run.

- [ ] **Step 6: Commit verification docs and task state**

```bash
git add docs/design/testing-strategy.md openspec/changes/improve-test-suite-best-practices/tasks.md
git commit -m "docs: 记录测试体系升级验证"
```
