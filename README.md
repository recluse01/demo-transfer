# Demo Transfer

Basic cross-service account transfer demo using Spring Boot, Feign, MySQL, and an orchestrated Saga.

## Services

- `transfer-service`: transfer entry service and Saga coordinator.
- `account-a-service`: Account A asset service, backed by the `account_a` database.
- `account-b-service`: Account B asset service, backed by the `account_b` database.
- `account-service`: shared account asset implementation used by both account applications.
- `common`: shared DTOs and enums.

## Design

- [Service implementation overview](docs/design/service-implementation-overview.md)

## Requirements

- JDK 8 compatible runtime.
- Maven 3.8+.
- MySQL 8.

The project currently compiles with source/target level 1.8. Local verification was run with Maven and a newer JDK.

## Database Setup

Create three databases:

```sql
CREATE DATABASE transfer DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE account_a DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE account_b DEFAULT CHARACTER SET utf8mb4;
```

Apply schemas:

```bash
mysql -uroot -proot transfer < docs/sql/transfer_schema.sql
mysql -uroot -proot account_a < docs/sql/account_schema.sql
mysql -uroot -proot account_b < docs/sql/account_schema.sql
```

Seed one balance row in each account database before running manual smoke tests:

```sql
INSERT INTO account_balance (user_id, asset_code, available_amount, frozen_amount, version, created_at, updated_at)
VALUES ('user-1', 'USDT', 1000.00000000, 0.00000000, 0, NOW(), NOW());
```

## Run Locally

Set database credentials with environment variables when they are not `root/root`:

```bash
export TRANSFER_DB_USERNAME=root
export TRANSFER_DB_PASSWORD=root
export ACCOUNT_A_DB_USERNAME=root
export ACCOUNT_A_DB_PASSWORD=root
export ACCOUNT_B_DB_USERNAME=root
export ACCOUNT_B_DB_PASSWORD=root
```

Start the services in separate terminals:

```bash
mvn -q -pl account-a-service spring-boot:run
mvn -q -pl account-b-service spring-boot:run
mvn -q -pl transfer-service spring-boot:run
```

Default ports:

- `transfer-service`: `8080`
- `account-a-service`: `8081`
- `account-b-service`: `8082`

## Example Requests

Create A to B manual review transfer:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B","mode":"MANUAL_REVIEW"}'
```

Create B to A manual review transfer:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"B_TO_A","mode":"MANUAL_REVIEW"}'
```

Create A to B automatic withdrawal transfer:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-1","assetCode":"USDT","amount":10,"direction":"A_TO_B","mode":"AUTO_WITHDRAW"}'
```

Approve a manual transfer:

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"message":"approved"}'
```

Reject a manual transfer:

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/review \
  -H 'Content-Type: application/json' \
  -d '{"approved":false,"message":"rejected"}'
```

Submit automatic withdrawal result:

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/withdraw-result \
  -H 'Content-Type: application/json' \
  -d '{"success":true,"message":"withdraw success"}'
```

Retry a failed transfer step:

```bash
curl -X POST http://localhost:8080/transfers/{transferId}/retry
```

Query a transfer:

```bash
curl http://localhost:8080/transfers/{transferId}
```

## Verification

Run all tests:

```bash
mvn -q test -DfailIfNoTests=false
```

The test suite covers:

- Account freeze, confirm debit, cancel freeze, credit, and idempotent duplicate freeze.
- A to B manual approval and rejection.
- B to A manual approval.
- A to B automatic withdrawal success and failure.
- Target credit failure after source debit and retry from `CREDIT_FAILED`.
