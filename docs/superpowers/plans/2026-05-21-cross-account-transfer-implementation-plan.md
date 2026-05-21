# Cross Account Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking; checked items reflect implementation observed in the current repository.

**Goal:** Build a basic Spring Boot, MySQL, and Feign implementation for cross-service Account A and Account B asset transfers with manual review, automatic withdrawal, idempotent account operations, finance ledgers, and retryable Saga states.

**Architecture:** Use `transfer-service` as an orchestrated Saga coordinator. `account-a-service` and `account-b-service` expose identical internal asset operation APIs and each service commits only to its own MySQL database in local transactions.

**Tech Stack:** JDK 8, Spring Boot 2.7.x, Spring Cloud OpenFeign, Spring Data JPA, MySQL 8, Maven, JUnit 5, Mockito, H2 for service-level tests.

## Current Implementation Status

Last synced: 2026-05-21.

The implementation described by this plan is present in the repository:

- Maven multi-module structure exists for `common`, `account-service`, `account-a-service`, `account-b-service`, and `transfer-service`.
- Shared contracts, transfer/account enums, request/response DTOs, and `ApiResponse` are implemented under `common`.
- Account persistence, idempotent asset operations, finance ledger writes, and internal account APIs are implemented in `account-service`.
- Account A and Account B boot applications exist with separate service names, ports, and database configuration.
- Transfer persistence, Feign clients, client routing, Saga orchestration, manual review, automatic withdrawal result handling, retry service, scheduled retry, and transfer REST APIs are implemented in `transfer-service`.
- SQL setup files exist under `docs/sql/`, and local run instructions exist in `README.md`.
- Unit/integration tests exist for account repositories, account operations, transfer repositories, Saga flows, retry handling, and required transfer scenarios.
- A Chinese manual demo guide has been added at `docs/demo/cross-account-transfer-demo.md`; at the time of this sync it is present as an uncommitted added file.

Observed implementation notes:

- `AUTO_WITHDRAW` is intentionally restricted to `A_TO_B` in `TransferSagaService.createTransfer`.
- Retry automation handles `DEBIT_FAILED`, `CREDIT_FAILED`, and `CANCEL_FAILED`; `FREEZE_FAILED` remains manual/non-automatic in the basic version.
- Account operation idempotency currently stores successful operation results and returns a non-applied success response for duplicate `transferId + operationType`.
- Controller-level failures are converted to `ApiResponse.fail(...)`; detailed failure persistence is represented on transfer orders and step logs for Saga steps.

Verification status from this sync:

- Full test suite was re-run with `mvn -q test -DfailIfNoTests=false`.
- Manual three-service smoke testing remains a documented final verification step and was not re-run during this documentation sync.

---

## File Structure

Create a Maven multi-module project:

- `pom.xml`: root Maven parent.
- `common`: shared DTOs, enums, response envelope, and validation helpers.
- `transfer-service`: transfer APIs, transfer state machine, Feign clients, retry service, transfer database entities.
- `account-service`: reusable account asset implementation.
- `account-a-service`: boot app that depends on `account-service` and connects to Account A database.
- `account-b-service`: boot app that depends on `account-service` and connects to Account B database.

Use the same `account-service` domain implementation for Account A and Account B to avoid duplicating balance and ledger logic. Keep deployment-specific configuration in `account-a-service` and `account-b-service`.

## Task 1: Create Maven Multi-Module Skeleton

**Files:**

- Create: `pom.xml`
- Create: `common/pom.xml`
- Create: `account-service/pom.xml`
- Create: `account-a-service/pom.xml`
- Create: `account-b-service/pom.xml`
- Create: `transfer-service/pom.xml`

- [x] **Step 1: Create root Maven parent**

Create `pom.xml` with modules and dependency versions:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.demo.transfer</groupId>
    <artifactId>demo-transfer</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>common</module>
        <module>account-service</module>
        <module>account-a-service</module>
        <module>account-b-service</module>
        <module>transfer-service</module>
    </modules>

    <properties>
        <java.version>1.8</java.version>
        <spring.boot.version>2.7.18</spring.boot.version>
        <spring.cloud.version>2021.0.9</spring.cloud.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring.cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring.boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [x] **Step 2: Create module POMs**

Create `common/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo.transfer</groupId>
        <artifactId>demo-transfer</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>common</artifactId>

    <dependencies>
        <dependency>
            <groupId>javax.validation</groupId>
            <artifactId>validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

Create `account-service/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.demo.transfer</groupId>
        <artifactId>demo-transfer</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>account-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.demo.transfer</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Create `account-a-service/pom.xml` and `account-b-service/pom.xml` with dependencies on `account-service`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, and `mysql:mysql-connector-java`.

Create `transfer-service/pom.xml` with dependencies on `common`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-openfeign`, `spring-boot-starter-validation`, `mysql:mysql-connector-java`, `spring-boot-starter-test`, and H2 test dependency.

- [x] **Step 3: Verify the empty skeleton builds**

Run:

```bash
mvn -q test
```

Expected: Maven succeeds after source directories are created or reports no tests to run for modules without tests.

- [x] **Step 4: Commit skeleton**

```bash
git add pom.xml common account-service account-a-service account-b-service transfer-service
git commit -m "chore: create transfer service project skeleton"
```

## Task 2: Define Shared Contract Types

**Files:**

- Create: `common/src/main/java/com/demo/transfer/common/ApiResponse.java`
- Create: `common/src/main/java/com/demo/transfer/common/AccountType.java`
- Create: `common/src/main/java/com/demo/transfer/common/TransferDirection.java`
- Create: `common/src/main/java/com/demo/transfer/common/TransferMode.java`
- Create: `common/src/main/java/com/demo/transfer/common/TransferStatus.java`
- Create: `common/src/main/java/com/demo/transfer/common/OperationType.java`
- Create: `common/src/main/java/com/demo/transfer/common/AssetOperationRequest.java`
- Create: `common/src/main/java/com/demo/transfer/common/AssetOperationResponse.java`

- [x] **Step 1: Add enums**

Implement these exact values:

```java
public enum AccountType {
    ACCOUNT_A, ACCOUNT_B
}

public enum TransferDirection {
    A_TO_B, B_TO_A
}

public enum TransferMode {
    MANUAL_REVIEW, AUTO_WITHDRAW
}

public enum TransferStatus {
    CREATED,
    FREEZE_FAILED,
    WAIT_REVIEW,
    WITHDRAW_PENDING,
    WITHDRAW_FAILED,
    CANCEL_FAILED,
    REJECTED,
    DEBIT_FAILED,
    DEBIT_SUCCESS,
    CREDIT_FAILED,
    SUCCESS
}

public enum OperationType {
    FREEZE,
    CONFIRM_DEBIT,
    CANCEL_FREEZE,
    CREDIT
}
```

- [x] **Step 2: Add response envelope**

Implement `ApiResponse<T>` with fields `success`, `code`, `message`, and `data`, plus static factories:

```java
public static <T> ApiResponse<T> ok(T data)
public static <T> ApiResponse<T> fail(String code, String message)
```

- [x] **Step 3: Add asset operation DTOs**

`AssetOperationRequest` fields:

```java
@NotBlank private String transferId;
@NotBlank private String userId;
@NotBlank private String assetCode;
@NotNull @DecimalMin("0.00000001") private BigDecimal amount;
@NotNull private TransferDirection direction;
```

`AssetOperationResponse` fields:

```java
private String transferId;
private OperationType operationType;
private boolean applied;
private String message;
```

- [x] **Step 4: Run common module tests**

Run:

```bash
mvn -q -pl common test
```

Expected: compile succeeds.

- [x] **Step 5: Commit shared contract**

```bash
git add common
git commit -m "feat: define shared transfer contracts"
```

## Task 3: Implement Account Database Model

**Files:**

- Create: `account-service/src/main/java/com/demo/transfer/account/domain/AccountBalance.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/domain/AssetOperation.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/domain/FinanceLedger.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/repository/AccountBalanceRepository.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/repository/AssetOperationRepository.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/repository/FinanceLedgerRepository.java`

- [x] **Step 1: Write repository tests first**

Create `account-service/src/test/java/com/demo/transfer/account/repository/AccountRepositoryTest.java`.

Test cases:

- Can save and reload `AccountBalance`.
- Unique key prevents duplicate `transferId + operationType` in `AssetOperation`.
- `findByUserIdAndAssetCodeForUpdate` can load the balance row inside a transaction.

- [x] **Step 2: Run failing repository test**

Run:

```bash
mvn -q -pl account-service test -Dtest=AccountRepositoryTest
```

Expected: fail because entities and repositories do not exist.

- [x] **Step 3: Create JPA entities**

Implement:

- `AccountBalance`: `id`, `userId`, `assetCode`, `availableAmount`, `frozenAmount`, `version`, `createdAt`, `updatedAt`; unique key on `user_id, asset_code`.
- `AssetOperation`: `id`, `transferId`, `operationType`, `userId`, `assetCode`, `amount`, `status`, `responseCode`, `responseMessage`, `createdAt`, `updatedAt`; unique key on `transfer_id, operation_type`.
- `FinanceLedger`: `id`, `transferId`, `userId`, `assetCode`, `operationType`, `availableDelta`, `frozenDelta`, `availableAfter`, `frozenAfter`, `createdAt`.

Use `BigDecimal` for all amount fields and initialize zero balances with `BigDecimal.ZERO`.

- [x] **Step 4: Create repositories**

`AccountBalanceRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select b from AccountBalance b where b.userId = :userId and b.assetCode = :assetCode")
Optional<AccountBalance> findByUserIdAndAssetCodeForUpdate(String userId, String assetCode);
```

`AssetOperationRepository`:

```java
Optional<AssetOperation> findByTransferIdAndOperationType(String transferId, OperationType operationType);
```

`FinanceLedgerRepository`:

```java
long countByTransferIdAndOperationType(String transferId, OperationType operationType);
```

- [x] **Step 5: Run repository tests**

Run:

```bash
mvn -q -pl account-service test -Dtest=AccountRepositoryTest
```

Expected: pass.

- [x] **Step 6: Commit account model**

```bash
git add account-service
git commit -m "feat: add account asset persistence model"
```

## Task 4: Implement Idempotent Account Asset Operations

**Files:**

- Create: `account-service/src/main/java/com/demo/transfer/account/service/AccountAssetService.java`
- Create: `account-service/src/main/java/com/demo/transfer/account/web/AccountAssetController.java`
- Create: `account-service/src/test/java/com/demo/transfer/account/service/AccountAssetServiceTest.java`

- [x] **Step 1: Write failing account service tests**

Test these behaviors:

- `freeze` decreases available amount and increases frozen amount.
- `freeze` fails when available amount is insufficient.
- duplicate `freeze` with the same `transferId` does not change balance or create duplicate ledger rows.
- `confirmDebit` decreases frozen amount.
- `cancelFreeze` decreases frozen amount and increases available amount.
- `credit` increases available amount.

- [x] **Step 2: Run failing account service tests**

Run:

```bash
mvn -q -pl account-service test -Dtest=AccountAssetServiceTest
```

Expected: fail because `AccountAssetService` does not exist.

- [x] **Step 3: Implement `AccountAssetService`**

Implement public methods:

```java
AssetOperationResponse freeze(AssetOperationRequest request)
AssetOperationResponse confirmDebit(AssetOperationRequest request)
AssetOperationResponse cancelFreeze(AssetOperationRequest request)
AssetOperationResponse credit(AssetOperationRequest request)
```

Each method must:

- Check `asset_operation` for the existing `transferId + operationType`.
- Return success without balance changes if a successful operation already exists.
- Lock the `account_balance` row.
- Validate available or frozen balance for debit operations.
- Mutate balance and write one `finance_ledger` row inside the same transaction.
- Save the `asset_operation` row as `SUCCESS`.

- [x] **Step 4: Implement internal REST controller**

Expose:

```text
POST /internal/accounts/assets/freeze
POST /internal/accounts/assets/confirm-debit
POST /internal/accounts/assets/cancel-freeze
POST /internal/accounts/assets/credit
```

Each endpoint accepts `AssetOperationRequest` and returns `ApiResponse<AssetOperationResponse>`.

- [x] **Step 5: Run account service tests**

Run:

```bash
mvn -q -pl account-service test
```

Expected: pass.

- [x] **Step 6: Commit idempotent account operations**

```bash
git add account-service
git commit -m "feat: implement idempotent account operations"
```

## Task 5: Create Account A And Account B Boot Apps

**Files:**

- Create: `account-a-service/src/main/java/com/demo/transfer/accounta/AccountAApplication.java`
- Create: `account-a-service/src/main/resources/application.yml`
- Create: `account-b-service/src/main/java/com/demo/transfer/accountb/AccountBApplication.java`
- Create: `account-b-service/src/main/resources/application.yml`

- [x] **Step 1: Add boot application classes**

`AccountAApplication`:

```java
@SpringBootApplication(scanBasePackages = "com.demo.transfer")
public class AccountAApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountAApplication.class, args);
    }
}
```

`AccountBApplication` uses the same structure with class name `AccountBApplication`.

- [x] **Step 2: Add service configuration**

`account-a-service/src/main/resources/application.yml`:

```yaml
server:
  port: 8081
spring:
  application:
    name: account-a-service
  datasource:
    url: jdbc:mysql://localhost:3306/account_a?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

`account-b-service/src/main/resources/application.yml` uses port `8082`, name `account-b-service`, and database `account_b`.

- [x] **Step 3: Verify boot modules compile**

Run:

```bash
mvn -q -pl account-a-service,account-b-service test
```

Expected: compile succeeds.

- [x] **Step 4: Commit account boot apps**

```bash
git add account-a-service account-b-service
git commit -m "feat: add account service applications"
```

## Task 6: Implement Transfer Persistence And Feign Clients

**Files:**

- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/domain/TransferOrder.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/domain/TransferStepLog.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/repository/TransferOrderRepository.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/repository/TransferStepLogRepository.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/client/AccountAClient.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/client/AccountBClient.java`

- [x] **Step 1: Write transfer persistence tests**

Create `transfer-service/src/test/java/com/demo/transfer/transfer/repository/TransferRepositoryTest.java`.

Test:

- Can save and find a transfer by `transferId`.
- Unique key prevents duplicate `transferId`.
- Can append step logs for a transfer.

- [x] **Step 2: Run failing transfer persistence tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferRepositoryTest
```

Expected: fail because transfer entities do not exist.

- [x] **Step 3: Implement transfer entities and repositories**

`TransferOrder` fields:

```java
id, transferId, userId, sourceAccountType, targetAccountType, assetCode, amount,
transferMode, status, lastErrorCode, lastErrorMessage, version, createdAt, updatedAt
```

`TransferStepLog` fields:

```java
id, transferId, stepName, stepStatus, requestBody, responseBody, errorMessage, createdAt
```

Create repository methods:

```java
Optional<TransferOrder> findByTransferId(String transferId);
List<TransferOrder> findTop100ByStatusInOrderByUpdatedAtAsc(Collection<TransferStatus> statuses);
```

- [x] **Step 4: Add Feign clients**

`AccountAClient` uses URL property `${account.a.url}`.

`AccountBClient` uses URL property `${account.b.url}`.

Both clients expose:

```java
ApiResponse<AssetOperationResponse> freeze(AssetOperationRequest request);
ApiResponse<AssetOperationResponse> confirmDebit(AssetOperationRequest request);
ApiResponse<AssetOperationResponse> cancelFreeze(AssetOperationRequest request);
ApiResponse<AssetOperationResponse> credit(AssetOperationRequest request);
```

- [x] **Step 5: Run transfer persistence tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferRepositoryTest
```

Expected: pass.

- [x] **Step 6: Commit transfer persistence and clients**

```bash
git add transfer-service
git commit -m "feat: add transfer persistence and account clients"
```

## Task 7: Implement Transfer Saga Service

**Files:**

- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/service/TransferSagaService.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/service/AccountClientRouter.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/web/TransferController.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/web/CreateTransferRequest.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/web/ReviewTransferRequest.java`
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferSagaServiceTest.java`

- [x] **Step 1: Write failing Saga tests**

Test:

- A to B manual transfer creates order, calls Account A freeze, and moves to `WAIT_REVIEW`.
- B to A manual transfer calls Account B freeze and moves to `WAIT_REVIEW`.
- Approval after `WAIT_REVIEW` calls source confirm debit, target credit, and moves to `SUCCESS`.
- Rejection after `WAIT_REVIEW` calls source cancel-freeze and moves to `REJECTED`.
- A to B auto withdrawal creates order, freezes Account A, and moves to `WITHDRAW_PENDING`.

- [x] **Step 2: Run failing Saga tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferSagaServiceTest
```

Expected: fail because Saga service does not exist.

- [x] **Step 3: Implement account client router**

`AccountClientRouter` chooses the source or target Feign client by `AccountType`.

Rules:

- `A_TO_B`: source is `ACCOUNT_A`, target is `ACCOUNT_B`.
- `B_TO_A`: source is `ACCOUNT_B`, target is `ACCOUNT_A`.
- `AUTO_WITHDRAW` is only allowed for `A_TO_B` in the basic version.

- [x] **Step 4: Implement create transfer flow**

`TransferSagaService.createTransfer(CreateTransferRequest request)`:

- Generate a unique `transferId`.
- Save `TransferOrder` with status `CREATED`.
- Call source account `freeze`.
- On success, update status to `WAIT_REVIEW` for `MANUAL_REVIEW`.
- On success, update status to `WITHDRAW_PENDING` for `AUTO_WITHDRAW`.
- On failure, update status to `FREEZE_FAILED` with error details.

- [x] **Step 5: Implement review flow**

`TransferSagaService.review(ReviewTransferRequest request)`:

- Load transfer by `transferId`.
- Only allow review when status is `WAIT_REVIEW`.
- If approved, call `confirmDebit` on source, then `credit` on target.
- If rejected, call `cancelFreeze` on source.
- Persist state after every successful remote step.

- [x] **Step 6: Implement automatic withdrawal result flow**

`TransferSagaService.handleWithdrawResult(String transferId, boolean success, String message)`:

- Only allow processing when status is `WITHDRAW_PENDING`.
- If success, call the same approve path: source confirm debit then target credit.
- If failure, set `WITHDRAW_FAILED`, call source cancel-freeze, then set `REJECTED`.

- [x] **Step 7: Implement REST endpoints**

Expose:

```text
POST /transfers
POST /transfers/{transferId}/review
POST /transfers/{transferId}/withdraw-result
GET /transfers/{transferId}
```

- [x] **Step 8: Run Saga tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferSagaServiceTest
```

Expected: pass.

- [x] **Step 9: Commit Saga service**

```bash
git add transfer-service
git commit -m "feat: implement transfer saga orchestration"
```

## Task 8: Implement Retry For Failed Transfer Steps

**Files:**

- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/service/TransferRetryService.java`
- Create: `transfer-service/src/main/java/com/demo/transfer/transfer/schedule/TransferRetryScheduler.java`
- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/service/TransferRetryServiceTest.java`

- [x] **Step 1: Write failing retry tests**

Test:

- `DEBIT_FAILED` retries source confirm debit and then target credit.
- `CREDIT_FAILED` retries only target credit.
- `CANCEL_FAILED` retries only source cancel-freeze.
- `FREEZE_FAILED` does not automatically retry in the basic version.

- [x] **Step 2: Run failing retry tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferRetryServiceTest
```

Expected: fail because retry service does not exist.

- [x] **Step 3: Implement retry service**

`TransferRetryService.retryOne(String transferId)`:

- Load transfer order.
- For `DEBIT_FAILED`, call source confirm debit; if it succeeds, set `DEBIT_SUCCESS`, then call target credit.
- For `CREDIT_FAILED`, call target credit only.
- For `CANCEL_FAILED`, call source cancel-freeze only.
- For other states, return without remote calls.

- [x] **Step 4: Implement scheduler**

`TransferRetryScheduler`:

- Runs every 30 seconds.
- Queries at most 100 transfers with states `DEBIT_FAILED`, `CREDIT_FAILED`, and `CANCEL_FAILED`.
- Calls `retryOne` for each transfer.
- Logs errors and continues processing the next transfer.

- [x] **Step 5: Add manual retry endpoint**

Add:

```text
POST /transfers/{transferId}/retry
```

Return the latest transfer state.

- [x] **Step 6: Run retry tests**

Run:

```bash
mvn -q -pl transfer-service test -Dtest=TransferRetryServiceTest
```

Expected: pass.

- [x] **Step 7: Commit retry implementation**

```bash
git add transfer-service
git commit -m "feat: add retryable transfer step handling"
```

## Task 9: Add Integration Tests For Required Scenarios

**Files:**

- Create: `transfer-service/src/test/java/com/demo/transfer/transfer/integration/TransferScenarioIntegrationTest.java`
- Create: `account-service/src/test/java/com/demo/transfer/account/integration/AccountOperationIntegrationTest.java`

- [x] **Step 1: Add account integration tests**

Test with H2:

- Freeze writes one ledger row.
- Duplicate freeze keeps the same balance and ledger count.
- Confirm debit after freeze leaves available reduced and frozen zero.
- Cancel freeze after freeze restores available and frozen zero.
- Credit writes one ledger row and increases available.

- [x] **Step 2: Add transfer scenario tests**

Mock Account A and Account B clients and verify:

- Scenario 1 approval: A freeze, A confirm debit, B credit, final `SUCCESS`.
- Scenario 1 rejection: A freeze, A cancel-freeze, final `REJECTED`.
- Scenario 2 approval: B freeze, B confirm debit, A credit, final `SUCCESS`.
- Scenario 3 auto withdrawal success: A freeze, A confirm debit, B credit, final `SUCCESS`.
- Scenario 3 auto withdrawal failure: A freeze, A cancel-freeze, final `REJECTED`.
- Target credit failure after source debit leaves final state `CREDIT_FAILED`.
- Retry from `CREDIT_FAILED` calls only target credit.

- [x] **Step 3: Run all tests**

Run:

```bash
mvn -q test
```

Expected: all tests pass.

- [x] **Step 4: Commit integration tests**

```bash
git add account-service transfer-service
git commit -m "test: cover cross-account transfer scenarios"
```

## Task 10: Add SQL And Local Run Documentation

**Files:**

- Create: `docs/sql/account_schema.sql`
- Create: `docs/sql/transfer_schema.sql`
- Create: `README.md`

- [x] **Step 1: Add transfer schema SQL**

`docs/sql/transfer_schema.sql` must create `transfer_order` and `transfer_step_log` with unique key on `transfer_order.transfer_id`.

- [x] **Step 2: Add account schema SQL**

`docs/sql/account_schema.sql` must create `account_balance`, `asset_operation`, and `finance_ledger` with:

- unique key on `account_balance(user_id, asset_code)`.
- unique key on `asset_operation(transfer_id, operation_type)`.

- [x] **Step 3: Add README local run instructions**

Document:

- Required JDK 8, Maven, MySQL.
- Create databases: `transfer`, `account_a`, `account_b`.
- Start services:

```bash
mvn -q -pl account-a-service spring-boot:run
mvn -q -pl account-b-service spring-boot:run
mvn -q -pl transfer-service spring-boot:run
```

- Example request for A to B manual review.
- Example request for B to A manual review.
- Example request for A to B automatic withdrawal.
- Example review approval, review rejection, withdraw result, and retry requests.

- [x] **Step 4: Run final verification**

Run:

```bash
mvn -q test
```

Expected: all tests pass.

- [x] **Step 5: Commit docs**

```bash
git add README.md docs
git commit -m "docs: add transfer setup and schema documentation"
```

## Final Verification

- [x] Run full test suite:

```bash
mvn -q test
```

- [ ] Run a local manual smoke test with three services:

```bash
mvn -q -pl account-a-service spring-boot:run
mvn -q -pl account-b-service spring-boot:run
mvn -q -pl transfer-service spring-boot:run
```

- [x] Confirm all acceptance scenarios from `docs/superpowers/specs/2026-05-21-cross-account-transfer-requirements.md` are covered by automated tests.

## Implementation Defaults

- Use orchestrated Saga owned by `transfer-service`.
- Use synchronous Feign for the basic version.
- Use idempotency key `transferId + operationType`.
- Use MySQL local transactions in account services.
- Use pessimistic row lock for balance mutation.
- Use `BigDecimal` for all amount calculations.
- Use `AUTO_WITHDRAW` only for A to B in the basic version.
- Do not introduce message queue or distributed transaction middleware in this version.
