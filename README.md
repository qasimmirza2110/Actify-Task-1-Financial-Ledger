# Task 1 — Financial Ledger & Audit System

## Overview

A Spring Boot + PostgreSQL REST backend for customer financial accounts. Users register, log in with JWT, open
accounts, and deposit, withdraw and transfer money. Every money movement is stored as balanced double-entry ledger
lines. Transfers pay a 1.5 % fee plus 18 % GST on that fee. Admins can reverse transactions, read audit logs and run
the monthly interest job. Simple fraud rules block suspicious outgoing money.

This is an internship learning project, not a production banking system.

## Features

- Customer registration and login with JWT; passwords stored as BCrypt hashes
- Two roles: `CUSTOMER` and `ADMIN`
- Account creation; deposit, withdrawal and transfer between accounts
- Transfer fee (1.5 %) and GST (18 % on the fee), posted to system accounts
- Double-entry ledger: every transaction has debit total = credit total
- Paged transaction history per account
- Ownership checks: customers can only use their own accounts
- Audit log for successful and failed operations (admin read-only)
- Fraud rules: single-transfer limit and velocity limit
- Admin reversal through a compensating transaction
- Monthly interest (4 % per year, compounded monthly), safe to run twice for the same month

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation) |
| Persistence | Hibernate 7.4.5, PostgreSQL (driver 42.7.13) |
| Security | Spring Security 7.1.1, jjwt 0.12.6 (HS256), BCrypt |
| Build | Maven wrapper (Maven 3.9.16) |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, Maven Surefire / Failsafe |

## Architecture

```
Client / Postman
  → Spring Security filter chain
  → JwtAuthenticationFilter (validates the Bearer token, loads the user)
  → Controller (request/response DTOs only)
  → Service (business rules, @Transactional, ownership checks, locking)
  → Repository (Spring Data JPA)
  → PostgreSQL
```

Controllers stay thin; rules live in services.

| Package (`com.actify.financialledger`) | Responsibility |
|---|---|
| `user` | `AppUser` entity, register/login (`AuthController`, `AuthService`), `AdminUserInitializer` |
| `security` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AppUserDetailsService`, `AppUserPrincipal`, JSON 401/403 handlers |
| `account` | `Account` entity, `AccountService` (ownership checks, row locking), system accounts created on startup |
| `transaction` | `FinancialTransactionService` (deposit/withdraw/transfer), `ReversalService`, `TransferChargeCalculator`, read-only `TransactionQueryService` |
| `ledger` | `LedgerEntry` entity and `LedgerService` — the only place that posts ledger lines and changes balances |
| `audit` | `AuditLog` entity, `AuditService`, admin read endpoint |
| `fraud` | `FraudDetectionService` (limit + velocity rules), `FraudProperties` |
| `interest` | `InterestCalculator`, `InterestService`, `InterestScheduler`, admin manual trigger |
| `exception` | task exceptions, `ApiError`, `GlobalExceptionHandler` |
| `common` | `PageResponse` for paged lists |

## Core Design Decisions

**Authentication.** `POST /api/auth/login` checks the email and BCrypt hash through Spring Security's
`AuthenticationManager`. `JwtService` then issues an HS256 token signed with `LEDGER_JWT_SECRET`; it contains only
`sub` (email), `iat` and `exp`. On each request `JwtAuthenticationFilter` verifies the signature and expiry and loads
the user from the database again, so a disabled or deleted user is rejected even with an old token.

**Authorization.** New users are always `CUSTOMER`. `ADMIN` is created only on startup from `LEDGER_ADMIN_EMAIL` /
`LEDGER_ADMIN_PASSWORD`. `SecurityConfig` restricts `/api/audit-logs/**`, `/api/admin/**` and
`POST /api/transactions/*/reverse` to `ADMIN`.

**Ownership.** Services take the user id from the authenticated principal, never from the request. Money can only be
moved out of (or into) your own account; owners and admins can view an account.

**Transactions and rollback.** Each deposit, withdrawal, transfer, reversal and interest credit runs in one
`@Transactional` service method. If any step fails, nothing is saved.

**Pessimistic locking and lock ordering.** Before reading any balance, the service locks every account row it will
touch with `SELECT … FOR UPDATE` (`AccountService.lockAccountsInIdOrder`), always in ascending account id order.
Concurrent requests on the same account wait for each other, and opposite-direction transfers cannot deadlock.

**Money.** Always `BigDecimal` with 2 decimals, rounded `HALF_UP`. Amounts with more than 2 decimals are rejected,
not silently rounded. Comparisons use `compareTo`.

**Double-entry bookkeeping.** Each operation is one `financial_transactions` row and two or more `ledger_entries`
rows with total DEBIT = total CREDIT (`LedgerService.validateBalanced`). CREDIT increases a balance, DEBIT decreases
it. The other side of customer money is always a system account (`SYS-CASH`, `SYS-FEE-REVENUE`, `SYS-GST-PAYABLE`,
`SYS-INTEREST-EXPENSE`), so the sum of all balances is always 0.00. Ledger rows are never updated or deleted.

| Operation | DEBIT | CREDIT |
|---|---|---|
| Deposit 500 | SYS-CASH 500 | customer 500 |
| Withdraw 200 | customer 200 | SYS-CASH 200 |
| Transfer 1000 | sender 1017.70 | receiver 1000.00, SYS-FEE-REVENUE 15.00, SYS-GST-PAYABLE 2.70 |
| Monthly interest | SYS-INTEREST-EXPENSE | customer |

**No negative customer balance.** Checked before posting, again inside `LedgerService`, and by a database CHECK
constraint (`chk_customer_balance_not_negative`).

**Audit logging.** Successful operations write an `audit_logs` row in the same transaction. Failures (insufficient
balance, fraud, access denied, wrong password) are written in a separate `REQUIRES_NEW` transaction, so the record
survives the rollback. There is no API to update or delete audit rows.

**Fraud rules.** `FraudDetectionService` runs after the account is locked: a single transfer above
`LEDGER_FRAUD_MAX_TRANSFER` is rejected, and more than `LEDGER_FRAUD_MAX_TRANSACTIONS` withdrawals/transfers within
`LEDGER_FRAUD_WINDOW_MINUTES` are rejected. The limits are sample values.

**Reversal.** `ReversalService` locks the original transaction, rejects it if already reversed or if it is itself a
reversal, locks the affected accounts in id order, then creates a **new** `REVERSAL` transaction with exactly opposite
ledger lines (fee and GST included) and marks the original `REVERSED`. Financial history is never deleted. If the
receiver has already spent the money, the reversal is rejected. The database allows only one reversal per
transaction (`uk_fin_tx_reversal_of`).

**Interest idempotency.** Monthly interest is `balance × 0.04 / 12`, rounded `HALF_UP`. `InterestScheduler` credits
the previous month once a month. Each credit uses the unique reference `INT-<account>-<YYYY-MM>`, so running the job
twice for the same month credits nothing the second time.

## Database

Two PostgreSQL databases are used:

| Database | Used by |
|---|---|
| `financial_ledger_task1_db` | the application |
| `financial_ledger_task1_test_db` | integration tests (`mvnw verify`); tables are created and dropped on every run |

Tables are created by Hibernate (`ddl-auto=update`):

| Table | Contents |
|---|---|
| `app_users` | email (unique), BCrypt password hash, role, status |
| `accounts` | customer and system accounts, owner, balance, status; unique account number |
| `financial_transactions` | one row per business operation: type, status, amount, fee, GST, unique reference, reversal link |
| `ledger_entries` | DEBIT/CREDIT lines per transaction; amounts must be positive (`chk_ledger_amount_positive`) |
| `audit_logs` | actor, action, entity type/id, transaction reference, SUCCESS or FAILURE, details, time |

## API Reference

All endpoints except register and login require `Authorization: Bearer <token>`.

| Method | URL | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | body `{"email","password"}`, creates a CUSTOMER |
| POST | `/api/auth/login` | public | returns `{"accessToken","tokenType":"Bearer","expiresInMs"}` |
| POST | `/api/accounts` | authenticated | open an account for the logged-in user |
| GET | `/api/accounts/my` | authenticated | my accounts |
| GET | `/api/accounts/{id}` | owner or admin | one account |
| POST | `/api/accounts/{id}/deposit` | owner | body `{"amount": 500.00}` |
| POST | `/api/accounts/{id}/withdraw` | owner | body `{"amount": 200.00}` |
| POST | `/api/transfers` | owner of source | body `{"sourceAccountId","destinationAccountId","amount"}` |
| GET | `/api/transactions/{id}` | either side or admin | one transaction with its ledger lines |
| GET | `/api/accounts/{id}/transactions?page=0&size=20` | owner or admin | history, newest first (size 1–100) |
| POST | `/api/transactions/{id}/reverse` | admin | reverse a transaction |
| GET | `/api/audit-logs?page=0&size=20` | admin | audit trail, newest first (size 1–100) |
| POST | `/api/admin/interest/run?period=YYYY-MM` | admin | run the monthly interest job for a month |

Errors use one JSON shape, `{"timestamp","status","error","message","path"}`, without stack traces.

| Status | Meaning |
|---|---|
| 400 | invalid input |
| 401 | missing/invalid token or wrong credentials |
| 403 | not your account, or admin only |
| 404 | account or transaction not found |
| 409 | insufficient balance, already reversed, duplicate email, data conflict |
| 422 | rejected by a fraud rule |

## Local Setup

Requirements: Java 17 and PostgreSQL. The Maven wrapper downloads Maven itself.

1. Create the databases (pgAdmin or `psql`):

   ```sql
   CREATE DATABASE financial_ledger_task1_db;
   CREATE DATABASE financial_ledger_task1_test_db;
   ```

2. Set the environment variables (see below) in your terminal session or IDE run configuration.
   `LEDGER_JWT_SECRET` is required; the application does not start without it.

3. Run the application:

   ```bat
   mvnw.cmd spring-boot:run
   ```

   On Linux/macOS use `./mvnw spring-boot:run`. The API listens on `http://localhost:8080` (Postman base URL).

On startup the application creates the four system accounts and, if configured, the admin user. Both steps are
skipped when the records already exist.

## Environment Variables

| Variable | Required | Default |
|---|---|---|
| `LEDGER_JWT_SECRET` | yes, at least 32 characters | — |
| `LEDGER_JWT_EXPIRATION` | no | `3600000` (ms) |
| `LEDGER_DB_URL` | no | `jdbc:postgresql://localhost:5432/financial_ledger_task1_db` |
| `LEDGER_DB_USERNAME` | no | `postgres` |
| `LEDGER_DB_PASSWORD` | no | empty (the PostgreSQL driver can then use a `pgpass.conf` file) |
| `LEDGER_ADMIN_EMAIL`, `LEDGER_ADMIN_PASSWORD` | no | empty, no admin is created |
| `LEDGER_FRAUD_MAX_TRANSFER` | no | `100000.00` |
| `LEDGER_FRAUD_WINDOW_MINUTES` | no | `10` |
| `LEDGER_FRAUD_MAX_TRANSACTIONS` | no | `5` |
| `LEDGER_INTEREST_CRON` | no | `0 0 1 1 * *` (01:00 on the 1st of each month; `-` disables it) |
| `LEDGER_INTEREST_ZONE` | no | `UTC` |
| `LEDGER_TEST_DB_URL`, `LEDGER_TEST_DB_USERNAME`, `LEDGER_TEST_DB_PASSWORD` | no, tests only | test database, `postgres`, empty |

Example (Windows `cmd`, current window only; placeholders, not real values):

```bat
set LEDGER_JWT_SECRET=<your-local-secret-of-32-plus-characters>
set LEDGER_DB_PASSWORD=<your-local-db-password>
set LEDGER_ADMIN_EMAIL=<your-local-admin-email>
set LEDGER_ADMIN_PASSWORD=<your-local-admin-password>
```

`.env.example` lists the same names with placeholder values.

## Example Financial Flow

1. `POST /api/auth/register` — create a customer
2. `POST /api/auth/login` — get a Bearer token
3. `POST /api/accounts` — open an account
4. `POST /api/accounts/{id}/deposit` with `{"amount": 2000.00}`
5. `POST /api/accounts/{id}/withdraw` with `{"amount": 100.00}`
6. `POST /api/transfers` to a second customer's account
7. `GET /api/transactions/{id}` — the transfer with its ledger lines
8. `POST /api/transactions/{id}/reverse` — as admin
9. `GET /api/audit-logs` — as admin, see successes and failures
10. `POST /api/admin/interest/run?period=2026-09` — as admin

## Example Transfer Calculation

`TransferChargeCalculator` is the only place charges are calculated:

```
fee   = amount × 1.5 %   rounded to 2 decimals, HALF_UP
GST   = fee × 18 %       on the rounded fee, not on the amount
total = amount + fee + GST   (what the sender pays)

amount 1000.00 → fee 15.00, GST 2.70, sender debit 1017.70, receiver credit 1000.00
```

The sender's balance must cover the total. For very small transfers the fee can round to 0.00; then no fee/GST
ledger line is written.

## Testing

```bat
mvnw.cmd test      :: unit tests, no database needed
mvnw.cmd verify    :: unit tests + integration tests against financial_ledger_task1_test_db
```

Integration tests (`*IT`) start the full application, call the API through the real security filter with MockMvc and
run on PostgreSQL. After every integration test, `LedgerInvariants` checks with SQL that every transaction is
balanced, every balance matches its ledger lines, no customer balance is negative and all balances add up to 0.

| Kind | Classes |
|---|---|
| Unit (28) | `TransferChargeCalculatorTest`, `LedgerServiceTest`, `JwtServiceTest`, `InterestCalculatorTest`, `FraudDetectionServiceTest` |
| Integration (34) | `FinancialTransactionIT`, `AuthIT`, `InterestIT`, `ConcurrencyIT`, `FinancialLedgerApplicationIT` |

Last run (2026-10-05, Java 17.0.12, PostgreSQL 17): `mvnw.cmd verify` → 28 unit + 34 integration tests,
0 failures, 0 errors, 0 skipped, BUILD SUCCESS.

## Security Notes

Secrets, credentials, database passwords and other private environment values are supplied locally through
environment variables and are not committed to this repository. `.env.example` contains placeholders only, and real
`.env` files are excluded by `.gitignore`. The values in `src/test/resources/application-test.properties` are fixed,
clearly test-only values used against the local test database.

## Known Limitations

- Schema is managed by Hibernate `ddl-auto=update`; there is no migration tool (Flyway/Liquibase).
- No refresh tokens or token revocation; a token is valid until it expires unless the user is disabled or deleted.
- The interest scheduler has no distributed lock; on several instances the work would be repeated (duplicates are
  still skipped by the unique reference).
- The admin can trigger interest for any month, including future months.
- A reversal does not check whether the accounts are BLOCKED.
- Every transfer locks the shared fee and GST system accounts.
