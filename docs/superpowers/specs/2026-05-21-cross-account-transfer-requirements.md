# Cross Account Transfer Requirements

## Background

The system has one user with two asset accounts:

- Account A is stored in Account A's MySQL database and managed by `account-a-service`.
- Account B is stored in Account B's MySQL database and managed by `account-b-service`.
- `transfer-service` is the transfer entry service and coordinates the full business flow through Feign calls.

The first version must support cross-service and cross-database asset transfer without introducing distributed transaction middleware. The implementation uses an orchestrated Saga: `transfer-service` owns the transfer order state, and each account service guarantees its own local MySQL transaction.

## Goals

- Support transfers from Account A to Account B and from Account B to Account A.
- Support manual review flows and the automatic withdrawal flow described below.
- Write one finance ledger record for every asset change.
- Keep each account database locally consistent.
- Reach cross-service eventual consistency through transfer states, idempotent account operations, and retryable failed steps.

## Non-Goals For The Basic Version

- No Seata, XA, two-phase commit, or other distributed transaction coordinator.
- No message queue.
- No risk engine, reconciliation center, or complex operations console.
- No multi-currency exchange rate conversion.
- No partial transfer.
- No automatic reverse compensation after source frozen assets have already been finally deducted and target credit fails. In that case, the transfer remains retryable until the target credit succeeds or is handled manually.

## Services

### transfer-service

Responsibilities:

- Receive transfer creation requests.
- Validate request parameters.
- Create and persist transfer orders.
- Call the source account service to freeze assets.
- Hold the transfer in `WAIT_REVIEW` for manual review or `WITHDRAW_PENDING` for automatic withdrawal.
- Complete successful transfers by deducting source frozen assets and crediting target available assets.
- Reject transfers by unfreezing source frozen assets.
- Persist step failures and expose retry behavior.

### account-a-service

Responsibilities:

- Manage Account A's balance table in Account A's MySQL database.
- Manage Account A's finance ledger table.
- Provide idempotent asset operation APIs for freeze, confirm debit, cancel freeze, and credit.

### account-b-service

Responsibilities:

- Manage Account B's balance table in Account B's MySQL database.
- Manage Account B's finance ledger table.
- Provide the same idempotent asset operation APIs as `account-a-service`.

The two account services may use the same code structure and database schema, but they must be deployed separately and configured with separate MySQL databases.

## Business Scenarios

### Scenario 1: Account A To Account B With Manual Review

1. User creates an A to B transfer.
2. `transfer-service` creates a transfer order.
3. `transfer-service` calls `account-a-service` to freeze Account A assets: decrease available amount and increase frozen amount.
4. Transfer waits for manual review.
5. If approved:
   - `account-a-service` confirms debit from Account A frozen assets.
   - `account-b-service` credits Account B available assets.
   - Transfer becomes successful.
6. If rejected:
   - `account-a-service` cancels the freeze: increase available amount and decrease frozen amount.
   - Transfer becomes rejected.

### Scenario 2: Account B To Account A With Manual Review

Same as Scenario 1, with source and target services reversed:

- Source service: `account-b-service`.
- Target service: `account-a-service`.

### Scenario 3: Account A To Account B With Automatic Withdrawal

1. User creates an A to B transfer with automatic withdrawal mode.
2. `transfer-service` creates a transfer order.
3. `transfer-service` calls `account-a-service` to freeze Account A assets.
4. Transfer enters automatic withdrawal processing.
5. If automatic withdrawal succeeds:
   - `account-a-service` confirms debit from Account A frozen assets.
   - `account-b-service` credits Account B available assets.
   - Transfer becomes successful.
6. If automatic withdrawal fails:
   - `account-a-service` cancels the freeze.
   - Transfer becomes rejected because the automatic withdrawal did not pass.

## Transfer State Model

The transfer order uses these states:

- `CREATED`: transfer order was created but source account freeze has not completed.
- `FREEZE_FAILED`: source account freeze failed.
- `WAIT_REVIEW`: source assets are frozen and the order waits for manual review.
- `WITHDRAW_PENDING`: source assets are frozen and automatic withdrawal is pending.
- `WITHDRAW_FAILED`: automatic withdrawal failed and cancel-freeze has not completed.
- `CANCEL_FAILED`: cancel-freeze failed and must be retried.
- `REJECTED`: transfer was rejected and source assets were successfully unfrozen.
- `DEBIT_FAILED`: source frozen asset final deduction failed and must be retried.
- `DEBIT_SUCCESS`: source frozen asset final deduction succeeded and target credit has not completed.
- `CREDIT_FAILED`: target credit failed and must be retried.
- `SUCCESS`: source deduction and target credit both succeeded.

Allowed state transitions:

- `CREATED -> WAIT_REVIEW` after source freeze succeeds for manual review transfers.
- `CREATED -> WITHDRAW_PENDING` after source freeze succeeds for automatic withdrawal transfers.
- `CREATED -> FREEZE_FAILED` after source freeze fails.
- `WAIT_REVIEW -> DEBIT_SUCCESS -> SUCCESS` after approval succeeds.
- `WAIT_REVIEW -> REJECTED` after rejection and cancel-freeze succeeds.
- `WITHDRAW_PENDING -> DEBIT_SUCCESS -> SUCCESS` after automatic withdrawal succeeds.
- `WITHDRAW_PENDING -> WITHDRAW_FAILED -> REJECTED` after automatic withdrawal fails and cancel-freeze succeeds.
- `WAIT_REVIEW -> CANCEL_FAILED` if manual rejection cancel-freeze fails.
- `WITHDRAW_FAILED -> CANCEL_FAILED` if automatic withdrawal cancel-freeze fails.
- `WAIT_REVIEW -> DEBIT_FAILED` or `WITHDRAW_PENDING -> DEBIT_FAILED` if source confirm debit fails.
- `DEBIT_SUCCESS -> CREDIT_FAILED` if target credit fails.
- Failed states return to the next successful state when retry succeeds.

## Account Operation APIs

Both account services expose the same asset operation contract.

### Freeze

`POST /internal/accounts/assets/freeze`

Request fields:

- `transferId`: globally unique transfer id.
- `userId`: user id.
- `amount`: positive transfer amount.
- `assetCode`: asset symbol such as `USDT`.
- `direction`: `A_TO_B` or `B_TO_A`.

Behavior:

- Check available balance is greater than or equal to amount.
- Decrease available amount.
- Increase frozen amount.
- Insert finance ledger record with operation type `FREEZE`.
- Insert idempotency operation record for `transferId + FREEZE`.

### Confirm Debit

`POST /internal/accounts/assets/confirm-debit`

Behavior:

- Check frozen balance is greater than or equal to amount.
- Decrease frozen amount.
- Insert finance ledger record with operation type `CONFIRM_DEBIT`.
- Insert idempotency operation record for `transferId + CONFIRM_DEBIT`.

### Cancel Freeze

`POST /internal/accounts/assets/cancel-freeze`

Behavior:

- Check frozen balance is greater than or equal to amount.
- Decrease frozen amount.
- Increase available amount.
- Insert finance ledger record with operation type `CANCEL_FREEZE`.
- Insert idempotency operation record for `transferId + CANCEL_FREEZE`.

### Credit

`POST /internal/accounts/assets/credit`

Behavior:

- Increase available amount.
- Insert finance ledger record with operation type `CREDIT`.
- Insert idempotency operation record for `transferId + CREDIT`.

## Idempotency Requirements

Every account operation must be idempotent by `transferId + operationType`.

If the same operation is received again:

- Do not update balance again.
- Do not insert another finance ledger record.
- Return success if the first execution succeeded.
- Return the stored failure result if the first execution failed after recording an operation result.

Recommended implementation:

- Create an `asset_operation` table with a unique key on `transfer_id, operation_type`.
- Insert the operation row before applying the balance change inside the same local transaction.
- If the unique key already exists and its status is `SUCCESS`, return success immediately.
- Use row-level locking on the balance row during balance mutation.

## Database Requirements

### transfer-service database

`transfer_order` stores:

- `id`
- `transfer_id`
- `user_id`
- `source_account_type`
- `target_account_type`
- `asset_code`
- `amount`
- `transfer_mode`
- `status`
- `last_error_code`
- `last_error_message`
- `version`
- `created_at`
- `updated_at`

`transfer_step_log` stores:

- `id`
- `transfer_id`
- `step_name`
- `step_status`
- `request_body`
- `response_body`
- `error_message`
- `created_at`

### account service database

`account_balance` stores:

- `id`
- `user_id`
- `asset_code`
- `available_amount`
- `frozen_amount`
- `version`
- `created_at`
- `updated_at`

`finance_ledger` stores:

- `id`
- `transfer_id`
- `user_id`
- `asset_code`
- `operation_type`
- `available_delta`
- `frozen_delta`
- `available_after`
- `frozen_after`
- `created_at`

`asset_operation` stores:

- `id`
- `transfer_id`
- `operation_type`
- `user_id`
- `asset_code`
- `amount`
- `status`
- `response_code`
- `response_message`
- `created_at`
- `updated_at`

## Error Handling

- If source freeze fails because of insufficient available balance, the transfer becomes `FREEZE_FAILED`.
- If Feign call times out or returns an unknown result, the transfer service records the failure state and relies on retry.
- If source confirm debit succeeds but target credit fails, the transfer becomes `CREDIT_FAILED`; retry must only call target credit.
- If cancel-freeze fails, the transfer becomes `CANCEL_FAILED`; retry must only call cancel-freeze.
- Account services must not silently swallow balance mutation failures.

## Acceptance Criteria

- A to B manual approval completes with Account A frozen amount deducted and Account B available amount increased.
- A to B manual rejection restores Account A available amount and removes the frozen amount.
- B to A manual approval completes with Account B frozen amount deducted and Account A available amount increased.
- A to B automatic withdrawal success completes the same final asset changes as approval.
- A to B automatic withdrawal failure restores Account A available amount and removes the frozen amount.
- Repeated account operation requests with the same `transferId + operationType` do not duplicate balance changes or finance ledger records.
- Each successful balance mutation has exactly one matching finance ledger row.
- A failed target credit after source debit can be retried until target credit succeeds.
