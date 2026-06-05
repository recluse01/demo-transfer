## ADDED Requirements

### Requirement: Transfer Web Contract Tests
transfer-service SHALL test its public transfer HTTP API through `@WebMvcTest` + MockMvc, not only by directly invoking controller methods.

#### Scenario: Create transfer request validation is enforced at HTTP boundary
- **WHEN** a client submits an invalid `POST /transfers` JSON body
- **THEN** the test asserts HTTP response status, `ApiResponse` error shape, and that no Workflow is started

#### Scenario: Create transfer workflow startup failure is exposed consistently
- **WHEN** order persistence succeeds but Temporal Workflow startup fails
- **THEN** the test asserts `INIT_FAILED` is persisted and the HTTP response uses `TRANSFER_OPERATION_FAILED`

#### Scenario: Review and get endpoints are covered as HTTP contracts
- **WHEN** tests exercise `POST /transfers/{transferId}/review` and `GET /transfers/{transferId}`
- **THEN** they assert path binding, JSON shape, service/workflow interactions, and success/failure envelope behavior

### Requirement: Temporal Scenario Integration Tests
transfer-service SHALL have enabled scenario tests for current Temporal Workflow behavior, covering manual review, auto mode, terminal failures, and the no-reverse-compensation invariant.

#### Scenario: Manual review approval and rejection are executable
- **WHEN** a manual-review transfer is approved or rejected in the Temporal test environment
- **THEN** enabled tests assert the final persisted status is respectively `SUCCESS` or `REJECTED`

#### Scenario: Auto mode completes without review signal
- **WHEN** an `AUTO_WITHDRAW` transfer runs in the Temporal test environment
- **THEN** enabled tests assert Workflow executes freeze, confirmDebit, and credit without waiting for review

#### Scenario: Credit failure does not compensate source account
- **WHEN** source debit has succeeded and target credit fails before later retry convergence
- **THEN** enabled tests assert `CREDIT_FAILED` is persisted, source `cancelFreeze` is not called, and later retry only targets credit

### Requirement: Account Idempotency and Concurrency Tests
account-service SHALL test idempotency and database uniqueness under realistic duplicate and concurrent calls.

#### Scenario: Duplicate operations have no second side effect
- **WHEN** the same `transferId + operationType` request is submitted twice
- **THEN** tests assert `applied=false` on the duplicate response and balance/ledger changes happen once

#### Scenario: Concurrent duplicate operations converge to one applied write
- **WHEN** multiple threads submit the same idempotency key against real MySQL
- **THEN** exactly one operation is applied, all other responses are duplicate/no-op or safely rejected, and final balances remain correct

### Requirement: Reusable Test Fixtures
The test suite SHALL use focused test-source helpers for repeated response JSON, WireMock stubs, transfer data, account data, and Temporal test harness setup.

#### Scenario: WireMock response helpers produce canonical ApiResponse JSON
- **WHEN** Feign/WireMock integration tests need success or failure responses
- **THEN** they use shared test helpers rather than duplicating handwritten JSON strings

#### Scenario: Temporal test harness closes resources consistently
- **WHEN** Workflow or scenario tests create `TestWorkflowEnvironment`
- **THEN** they use a helper or explicit lifecycle pattern that always closes the environment after each test

### Requirement: Verification Commands Remain Split by Track
The improved tests SHALL preserve the existing Maven split: fast feedback through `mvn test`, full confidence through `mvn verify`.

#### Scenario: Fast track remains Docker-free
- **WHEN** `mvn test` runs without Docker
- **THEN** all `*Test` tests pass without requiring Testcontainers

#### Scenario: Full track proves real boundary behavior
- **WHEN** `mvn verify` runs with Docker available
- **THEN** all `*Test` and `*IT` tests pass, JaCoCo merged gates run, and Testcontainers-backed tests cover real MySQL behavior
