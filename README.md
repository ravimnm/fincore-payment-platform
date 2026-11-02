# FinCore — Secure Payment Processing Platform

FinCore is a backend for a wallet-based payment system, built with Java 21 and Spring Boot 4. It handles user registration and JWT authentication, one wallet per user, wallet-to-wallet transfers, wallet funding through the Razorpay Test Mode gateway, and ledger records for balance movements. A small React client is included.

The project concentrates on the parts of a payment backend that are hard to get right:

- keeping balances consistent under concurrent requests (row-level pessimistic locks, `@Version`; the behavior under load is not yet fully validated, see [Concurrency testing status](#12-concurrency-testing-status)),
- making retried requests safe (idempotency keys),
- keeping money movement and its records in one database transaction,
- crediting a wallet from an external gateway only after server-side signature verification,
- publishing events reliably with a transactional outbox.

> **Status:** a personal engineering project, developed and benchmarked on a local machine. It is not a production deployment, and this document makes no production-readiness claim. Where behavior is intended but not proven by tests, or exists in code but is not active, the text says so. **Concurrent financial integrity under load is not yet fully validated**; see [Concurrency testing status](#12-concurrency-testing-status).

---

## Contents

1. [How to read this document](#1-how-to-read-this-document)
2. [Motivation and problem statement](#2-motivation-and-problem-statement)
3. [Key features](#3-key-features)
4. [System architecture](#4-system-architecture)
5. [Technology stack](#5-technology-stack)
6. [Design decisions](#6-design-decisions)
7. [Authentication and authorization](#7-authentication-and-authorization)
8. [Wallet management](#8-wallet-management)
9. [Wallet-to-wallet payment processing](#9-wallet-to-wallet-payment-processing)
10. [Payment idempotency](#10-payment-idempotency)
11. [Transactions and concurrency control](#11-transactions-and-concurrency-control)
12. [Concurrency testing status](#12-concurrency-testing-status)
13. [Ledger and financial consistency](#13-ledger-and-financial-consistency)
14. [Transactional outbox and messaging](#14-transactional-outbox-and-messaging)
15. [Razorpay integration and gateway transaction lifecycle](#15-razorpay-integration-and-gateway-transaction-lifecycle)
16. [Security design](#16-security-design)
17. [Database design](#17-database-design)
18. [API reference](#18-api-reference)
19. [Project structure](#19-project-structure)
20. [Environment configuration](#20-environment-configuration)
21. [Local development setup](#21-local-development-setup)
22. [Running the application](#22-running-the-application)
23. [Testing strategy](#23-testing-strategy)
24. [Performance testing and benchmarks](#24-performance-testing-and-benchmarks)
25. [Deployment architecture](#25-deployment-architecture)
26. [Engineering trade-offs and limitations](#26-engineering-trade-offs-and-limitations)
27. [Future improvements](#27-future-improvements)
28. [License](#28-license)
29. [Documentation notes](#29-documentation-notes)

---

## 1. How to read this document

Statements in this README were checked against the source code in this repository. These labels mark the status of a capability:

| Label | Meaning |
|---|---|
| **Implemented** | Present in the code and reachable at runtime. |
| **Present, not active** | Code exists but is disabled or not wired into the running application. |
| **Unverified** | Reported historically (for example, in an earlier README) but not supported by an artifact in this repository. |
| **Maintainer-reported** | Observations or figures supplied by the maintainer; no log or result file is committed. |

A mechanism being **implemented** does not mean it is **proven**, and a mechanism described as **intended** states its design purpose, not an experimentally verified outcome. The repository contains no automated concurrency or integration tests (see [Testing strategy](#23-testing-strategy)), and the load tests did not record a financial-integrity check. Intermittent optimistic-lock conflicts have also been reported during load testing; see [Concurrency testing status](#12-concurrency-testing-status).

---

## 2. Motivation and problem statement

Wallet and payment systems fail in ways that ordinary CRUD applications do not:

| Failure mode | Consequence |
|---|---|
| Two concurrent transfers read the same balance | Lost update; money created or destroyed |
| Two opposite-direction transfers lock wallets in different orders | Deadlock |
| A client retries a request after a timeout | Duplicate debit |
| A debit commits but its record or credit does not | Unreconcilable state |
| A client claims "I paid" to an external gateway | Fraudulent wallet credit |

FinCore's design goal is that money movement stays correct when requests are concurrent, duplicated, or fail midway. [Transactions and concurrency control](#11-transactions-and-concurrency-control) separates design intent from verified behavior, and [Concurrency testing status](#12-concurrency-testing-status) records what load testing has shown so far. Correctness under concurrent load is a goal that is **not yet fully validated**.

---

## 3. Key features

| Area | Capability | Status |
|---|---|---|
| Authentication | Registration with BCrypt-hashed passwords; username/password login issuing a signed JWT | Implemented |
| Authorization | Role claims in the JWT (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`); URL-level rules; per-resource ownership checks in controllers | Implemented |
| Wallets | One INR wallet per user, created at registration; random 12-digit wallet number; `ACTIVE` / `FROZEN` / `CLOSED` states; admin freeze and activate | Implemented |
| Transfers | Wallet-to-wallet payments in a single transaction with ordered pessimistic locks | Implemented; behavior under concurrent load not fully validated (see [status](#12-concurrency-testing-status)) |
| Idempotency | Unique idempotency key on payments and on gateway orders | Implemented (see [limits](#10-payment-idempotency)) |
| Ledger | `DEBIT` / `CREDIT` entries with `balance_after` per balance change from transfers and Razorpay funding | Implemented (no read API) |
| Razorpay | Order creation, HMAC-SHA256 signature verification, wallet credit, gateway transaction record | Implemented (Test Mode) |
| Outbox | `payment_outbox` written in the transfer transaction; scheduled relay to RabbitMQ with publisher confirms; inbox deduplication; dead-letter queue | Implemented (consumer only records the event) |
| Search | Recipient search by name, username, phone, user ID or wallet number; Redis-cached | Implemented |
| Rate limiting | In-memory per-IP filter | Present, not active (`@Component` is commented out) |
| Google OAuth2 login | Success handler that provisions a user and issues a JWT | Present, not active (no `oauth2Login()` in the security chain, no client registration properties) |
| Frontend | React + Vite client: sign-in, send, add money, history, dashboard, admin | Implemented |
| CI/CD | GitHub Actions: build, test, push Docker image to GHCR | Implemented |

---

## 4. System architecture

```mermaid
flowchart LR
    subgraph Clients
        SPA["React SPA (Vite)"]
        K6["k6 load scripts"]
        API["curl / Postman"]
    end

    subgraph App["FinCore (Spring Boot)"]
        SEC["Security filter chain<br/>CORS, JwtFilter"]
        CTRL["Controllers<br/>Auth, User, Wallet, Payment,<br/>Admin, Razorpay"]
        SVC["Services<br/>Auth, Wallet, Payment,<br/>Razorpay, Outbox"]
        SCHED["PaymentOutboxScheduler<br/>every 5 s"]
        CONS["PaymentEventConsumer"]
    end

    PG[("PostgreSQL")]
    RD[("Redis<br/>user-search cache")]
    MQ[["RabbitMQ<br/>fincore.events"]]
    RZP["Razorpay API<br/>(Test Mode)"]
    CHK["Razorpay Checkout<br/>(browser)"]

    SPA --> SEC
    K6 --> SEC
    API --> SEC
    SEC --> CTRL --> SVC
    SVC --> PG
    SVC --> RD
    SVC -->|create order| RZP
    SPA --> CHK --> RZP
    SCHED -->|poll unpublished| PG
    SCHED -->|publish + confirm| MQ
    MQ --> CONS -->|inbox insert| PG
```

Layering follows the usual Spring convention: controllers validate input and enforce ownership, services hold business rules and transaction boundaries, repositories expose JPA queries (plus one JDBC repository for the inbox table).

---

## 5. Technology stack

| Layer | Technology | Version / source |
|---|---|---|
| Language | Java | 21 (`pom.xml`: `java.version`) |
| Framework | Spring Boot | 4.0.4 (`spring-boot-starter-parent`) |
| Web / validation | `spring-boot-starter-web`, `spring-boot-starter-validation` | managed by Boot |
| Security | `spring-boot-starter-security`, BCrypt | managed by Boot |
| JWT | `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.11.5 |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate), PostgreSQL driver | managed by Boot |
| Cache | `spring-boot-starter-cache` + `spring-boot-starter-data-redis` | managed by Boot; 5-minute TTL |
| Messaging | `spring-boot-starter-amqp` (RabbitMQ) | managed by Boot |
| OAuth2 client | `spring-boot-starter-oauth2-client` | on classpath, not wired |
| Payment gateway | Razorpay Orders REST API via Spring `RestClient` (no Razorpay SDK) | Test Mode |
| Frontend | React `^18.3.1`, react-router-dom `^6.26.2`, Vite `^5.4.8` | `fincore-frontend/package.json` |
| Container | `eclipse-temurin:21-jre` | `Dockerfile` |
| CI/CD | GitHub Actions, GitHub Container Registry | `.github/workflows/ci-cd.yml` |
| Load testing | Grafana k6 | `payment-load.js`, `razorpay-load-test.js` |

---

## 6. Design decisions

| Decision | Reason | Where |
|---|---|---|
| All balance changes run inside one `@Transactional` service method | Debit, credit, payment row, ledger rows and outbox row commit or roll back together | `PaymentService.processPayment`, `RazorpayService.verifyPayment` |
| `SELECT ... FOR UPDATE` on wallet rows (`PESSIMISTIC_WRITE`) | Intended to serialize writers per wallet so a read-check-write on a balance cannot interleave (optimistic-lock conflicts have still been observed; see [status](#12-concurrency-testing-status)) | `WalletRepository.findByIdForUpdate` |
| Lock wallets in ascending ID order | Intended to prevent lock-cycle deadlocks between opposite transfers by using a fixed global lock order (not tested) | `PaymentService.processPayment` |
| `@Version` on `Wallet` | Optimistic detection, intended as a second line of defense against an unlocked write path; conflicts have been observed in load tests (see [status](#12-concurrency-testing-status)) | `Wallet.version` |
| Idempotency key stored on the payment row with a unique constraint declared on the entity | Retries return the stored result instead of moving money again | `Payment.idempotencyKey` |
| Gateway transactions in their own table | External payment state is separate from internal transfers and can be reconciled against the provider | `GatewayTransaction` |
| Lock the gateway transaction row before crediting | Intended so that two concurrent `verify` calls cannot both credit (not load-tested) | `findByGatewayOrderIdForUpdate` |
| Transactional outbox | The event is written in the same transaction as the payment, so a crash cannot commit a payment and lose its event | `PaymentOutboxService` |
| Inbox table with `ON CONFLICT DO NOTHING` | At-least-once delivery becomes effectively-once processing for the consumer | `PaymentEventInboxRepository` |
| `BigDecimal` / `NUMERIC(19,2)` for money | No floating-point rounding | entity column definitions |
| Razorpay amounts converted with `RoundingMode.UNNECESSARY` | Fails loudly instead of silently rounding paise | `RazorpayClient.createOrder` |

---

## 7. Authentication and authorization

### Flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as AuthController / AuthService
    participant DB as PostgreSQL
    participant F as JwtFilter
    participant S as Spring Security rules
    participant R as Resource controller

    C->>A: POST /auth/login {username, password}
    A->>DB: find user by username
    A->>A: status must be ACTIVE, BCrypt match
    A-->>C: 200 {token, username, roles}

    C->>F: GET /wallet/me + Authorization: Bearer token
    F->>F: parse and verify signature, check expiry
    F->>S: SecurityContext (username, roles as authorities)
    S->>R: URL rule satisfied
    R->>R: ownership check (wallet owner or admin)
    R-->>C: 200
```

### Details

| Item | Implementation |
|---|---|
| Password storage | `BCryptPasswordEncoder` (default strength) |
| Token | JWT, subject = username, claim `roles`, signed with the key derived from `jwt.secret`; the issued tokens in the repository use `HS256` |
| Lifetime | `jwt.expiration=86400000` ms (24 hours), fixed in `application.properties` |
| Secret | `JWT_SECRET` env var; a development default is present in `application.properties` and must be overridden |
| Session | Stateless; CSRF disabled |
| Invalid token | `JwtFilter` clears the context and continues; the request is then treated as unauthenticated |

### Access rules (`SecurityConfig`)

| Path | Access |
|---|---|
| `OPTIONS /**` | Public (CORS preflight) |
| `/auth/register`, `/auth/login`, `/razorpay.html` | Public |
| `/admin/**` | `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` |
| `/wallet/**`, `/payments/**`, `/gateway/razorpay/**`, `/ledger/**`, anything else | Authenticated |

The `/ledger/**` rule exists, but no controller is mapped to it.

On top of URL rules, controllers enforce **ownership**: a payment's sender wallet must belong to the caller; wallets and payments are readable only by their participants or an admin.

Known behavior of the stateless JWT design: roles are read from the token, so a role change takes effect only when the token expires; and the user's `status` is checked at login but not on later requests.

---

## 8. Wallet management

- Registration creates the user and the wallet in the same transaction (`AuthService.register` → `WalletService.createWallet`). `GET /wallet/me` also creates a wallet if none exists.
- Wallet number: 12 random digits from `SecureRandom`, regenerated until unique.
- Defaults: balance `0.00`, currency `INR`, status `ACTIVE`.
- States: `ACTIVE`, `FROZEN`, `CLOSED`. Debits and credits require `ACTIVE`. A `CLOSED` wallet cannot be frozen or activated.
- Freeze and activate lock the wallet row (`findByIdForUpdate`) and are exposed to admins under `/admin/wallets/{walletId}/...`.
- **Direct funding** (`POST /wallet/{walletId}/fund`) adds to a balance with no payment record, gateway record or ledger entry. It is allowed for admins, and for the wallet owner only when `fincore.dev-funding-enabled=true`. **The committed `application.properties` sets this to `true`**; set it to `false` outside local development.

---

## 9. Wallet-to-wallet payment processing

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant P as PaymentController
    participant S as PaymentService
    participant W as WalletService
    participant DB as PostgreSQL

    C->>P: POST /payments (JWT)
    P->>W: getOwnedWallet(senderWalletId, caller)
    P->>S: processPayment(...)
    Note over S,DB: One @Transactional method
    S->>DB: find payment by idempotency key
    alt key already used
        S-->>P: stored payment (no money moved)
    else new request
        S->>DB: lock lower wallet ID (FOR UPDATE)
        S->>DB: lock higher wallet ID (FOR UPDATE)
        S->>S: wallets differ, both ACTIVE, currency matches, balance sufficient
        S->>S: debit sender, credit receiver
        S->>DB: insert payment (COMPLETED) + DEBIT and CREDIT ledger entries
        S->>DB: insert payment_outbox row
        Note over S,DB: COMMIT, or ROLLBACK on any exception
    end
    P->>P: replayed key must belong to the same sender wallet
    P-->>C: 201 PaymentResponse
```

Validation order inside `processPayment`: request fields, idempotency lookup, ordered locks, sender ≠ receiver, both wallets `ACTIVE`, currency match with the request, sufficient balance. Any failure throws a `RuntimeException`, which rolls the transaction back, so a partial debit cannot persist.

### Payment status

`PaymentStatus` defines `CREATED`, `PROCESSING`, `SUCCESS`, `FAILED` and `COMPLETED`. The transfer path creates the payment as `CREATED` in the constructor and sets `COMPLETED` before the single save. In practice only `COMPLETED` is persisted; failed transfers roll back and leave no row. The other statuses are not used by current code.

```mermaid
flowchart TD
    A[Request] --> B{Fields valid?}
    B -- no --> X[400, nothing written]
    B -- yes --> C{Idempotency key exists?}
    C -- yes --> R[Return stored payment]
    C -- no --> D[Lock wallets in ID order]
    D --> E{Active, same currency, funds sufficient?}
    E -- no --> Y[Rollback, 400]
    E -- yes --> F[Debit, credit, payment, ledger, outbox]
    F --> G[Commit: status COMPLETED]
```

---

## 10. Payment idempotency

**Mechanism.** Every payment request carries `idempotencyKey` (max 100 chars). `processPayment` looks the key up first and, if found, returns the stored payment without touching balances. The same pattern applies to Razorpay order creation (`GatewayTransaction.idempotencyKey`).

**What this provides**

- A client that retries after a timeout receives the original result rather than a second debit.
- `PaymentController` checks that a replayed payment belongs to the caller's sender wallet, so one user cannot read another user's payment by reusing a key.

**Limits to be aware of**

| Limit | Detail |
|---|---|
| No request fingerprint | A reused key with a *different* amount or receiver returns the original payment instead of being rejected. |
| Replay status code | A replay returns `201 Created` like the first call; the response does not indicate it is a replay. |
| Concurrent duplicates | The lookup is a read before the locks. Two simultaneous requests with the same new key can both pass it. The second then waits for the wallet locks, and its insert would violate the unique constraint on `payments.idempotency_key`, rolling back its whole transaction (including its debit). **This safety net exists only if the database actually has that unique constraint.** The schema is not versioned in this repository (see [Database design](#17-database-design)), and `ddl-auto=validate` does not check constraints. |
| Gateway orders | Same read-then-insert pattern. A concurrent duplicate could create an extra Razorpay order before the unique constraint rejects the local row; the extra order remains unused at Razorpay. |

---

## 11. Transactions and concurrency control

> **Design intent, not a guarantee.** This section describes what the code is designed to do. It does not claim that optimistic-lock conflicts cannot occur: they have been observed during k6 testing. See [Concurrency testing status](#12-concurrency-testing-status).

### Why transactions are required

A transfer changes four kinds of state: the sender balance, the receiver balance, the payment record, and the ledger/outbox rows. If any of them could commit without the others, the system would be inconsistent. `processPayment` is a single `@Transactional` method (default propagation), so Spring opens one database transaction, and an unchecked exception rolls all of it back. Isolation is not configured and is therefore the database default (PostgreSQL: `READ COMMITTED`).

### Pessimistic locking

`WalletRepository.findByIdForUpdate` is annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`, which Hibernate issues as `SELECT ... FOR UPDATE` on PostgreSQL. The intent is that a second transaction trying to lock the same wallet blocks until the first commits or rolls back, and that the balance is read only after the lock is held, so a check-then-update cannot interleave with another writer. `getWalletForUpdate` is `@Transactional` and joins the caller's transaction; the lock lasts until that transaction ends.

Each balance-changing path in the code calls `getWalletForUpdate` before modifying a wallet: `PaymentService.processPayment`, `RazorpayService.verifyPayment`, and `WalletService.fundWallet`; freeze and activate lock before changing status. That is a statement about the code, not about observed runtime behavior.

### Deterministic lock order

```java
if (senderWalletId < receiverWalletId) {
    senderWallet   = walletService.getWalletForUpdate(senderWalletId);
    receiverWallet = walletService.getWalletForUpdate(receiverWalletId);
} else {
    receiverWallet = walletService.getWalletForUpdate(receiverWalletId);
    senderWallet   = walletService.getWalletForUpdate(senderWalletId);
}
```

If A→B and B→A ran concurrently and each locked its own sender first, each would hold one lock and wait for the other: a deadlock. Always locking the lower ID first is intended to make both transactions request the same lock first, so one waits and the other proceeds. By inspection no lock cycle can form among transfers, but this has not been tested.

The other lock-taking paths do not invert this order: Razorpay `verify` locks the gateway-transaction row and then one wallet; freeze/activate lock one wallet. No path locks a wallet and then waits on a gateway-transaction row.

### Optimistic versioning

`Wallet.version` is a JPA `@Version` column. Hibernate adds `AND version = ?` to wallet updates, increments it, and throws an optimistic-lock exception if the row changed since it was read.

**Design intent:** because every balance-changing path locks first, version conflicts were not expected, and the field was meant as a safety net. **Observed:** conflicts have occurred in k6 runs (see [status](#12-concurrency-testing-status)), so that expectation has not held in at least some scenarios, and the cause is under investigation.

When a conflict is thrown it is not caught or retried. The transaction rolls back, and because no handler targets optimistic-lock exceptions, `GlobalExceptionHandler` returns them as `400 Bad Request` through its `RuntimeException` handler. This mapping follows from the source; it matches the HTTP 400 reported in the latest run.

### Design intent versus experimentally verified behavior

| Property | Mechanism | Design intent | Experimentally verified |
|---|---|---|---|
| No lost balance update between concurrent transfers | Row locks, balance read under lock, `@Version` | Yes | **No.** Optimistic-lock conflicts observed under k6; no balance-conservation check has been run |
| No deadlock between opposite-direction transfers | Ascending-ID lock order | Yes | No test exists |
| Debit, credit, payment, ledger and outbox commit atomically | One `@Transactional` method | Yes | Not tested directly; relies on Spring/JPA transaction semantics |
| Sequential retry does not repeat a transfer | Idempotency lookup | Yes | Not tested |
| Concurrent duplicate does not repeat a transfer | Unique constraint as backstop | Yes | Not tested; depends on a DB constraint that is not in the repository |
| Zero optimistic-lock conflicts | None | **Not claimed** | Contradicted by maintainer-reported runs |
| Ledger always reconciles to balances | None | **Not guaranteed** (see [Ledger](#13-ledger-and-financial-consistency)) | Not tested |

Not configured: lock-wait timeouts, retry on lock or version failure, or deadlock-specific handling. A lock timeout, an optimistic-lock failure or a unique-constraint failure would each surface as a generic `400 Bad Request`.

---

## 12. Concurrency testing status

> **Concurrent financial integrity is not yet fully validated.** Intermittent Hibernate optimistic-lock conflicts have occurred during k6 testing, including after pessimistic locks were introduced in the wallet update paths. The root cause is under investigation. This issue has **not** been resolved.

### What has been observed

| Observation | Source |
|---|---|
| Intermittent optimistic-lock conflicts during k6 load testing, including after pessimistic locking was added to wallet update paths | Maintainer-reported; no log or stack trace is committed |
| Latest local run (10 VUs, 20 s): 1 of 1,585 requests failed with HTTP 400, reported as an optimistic-lock failure | Maintainer-reported; no result file committed. Details in [Performance testing](#24-performance-testing-and-benchmarks) |
| The only committed result with failures (`payment-results.json`, 8 of 1,747 requests) does not record failure causes | Artifact in repo; the failures cannot be attributed to a specific error |

### What is not established

- The **root cause** of the conflicts. No explanation is documented here because none has been confirmed.
- Whether **committed data is consistent** after concurrent runs. No run has checked that total balances are conserved, that each wallet's balance matches its ledger entries, or that each `201` response corresponds to exactly one payment row.
- Whether the **historical zero-failure result** (50,682 requests) is representative. It is unverified and sits uneasily with the observations above.
- That the locking design is correct. It is the intended design, not a verified one.

### Expected effect of a conflict (design behavior, not independently tested)

A conflicting request is rejected rather than committed: `@Version` makes the stale update fail, the transaction rolls back, and the client receives `400`. Because the rollback leaves no payment row for that idempotency key, a retry with the same key would run the transfer afresh. Rejection of a request does not by itself show that other committed data is correct.

### Code inspection notes

These are facts from the current source, listed to aid the investigation. They are **not** a diagnosis.

- Wallet rows are written in four places: `PaymentService.processPayment` (relies on dirty checking of the locked entities; the current source has no explicit `save` of the wallets), `RazorpayService.verifyPayment` (explicit `save`), `WalletService.fundWallet`, and freeze/activate (dirty checking).
- `PaymentController` calls `WalletService.getOwnedWallet`, a separate read-only transaction, before `processPayment`; that read does not take a lock and does not write.
- No code retries on an optimistic-lock failure, and no handler distinguishes it from other errors.
- `wallet-concurrency-audit.txt` is a snapshot of an earlier source version whose `processPayment` called `walletService.save` explicitly; the current source does not. The source version used for each historical run is not recorded.

### Needed to close this item

1. Capture the stack trace, entity and SQL for a failing request, with the server log around it.
2. Reproduce at low VU counts and record whether failures depend on user count, wallet overlap or request mix.
3. Record the code version, script, user and wallet counts and logging level for each run.
4. Add a post-run reconciliation (balance conservation, ledger agreement, payment-row count versus `201` responses).
5. Add automated concurrency tests (see [Future improvements](#27-future-improvements)).

---

## 13. Ledger and financial consistency

`ledger_entries` rows are written in two situations:

| Event | Entries |
|---|---|
| Internal transfer | One `DEBIT` (sender) and one `CREDIT` (receiver), both linked to the payment, each with `balance_after` |
| Razorpay funding | One `CREDIT` linked to the gateway transaction, with `balance_after` |

Entries record `amount`, `entry_type`, the wallet, and `balance_after` taken from the locked wallet after the change.

**What this is and is not**

- It is a per-wallet movement log written in the same transaction as the balance change.
- It is *double-entry-style* for transfers (a matching debit and credit). It is not a full double-entry system: there is no chart of accounts, and gateway funding has no offsetting entry.
- Entries are described in code comments as immutable, but the entity has setters and nothing in the repository enforces append-only behavior at the database level.
- Direct funding (`POST /wallet/{id}/fund`) changes a balance **without** a ledger entry, so with that path enabled, ledger totals can diverge from balances. No reconciliation job exists.
- `LedgerEntryRepository` has query methods (by wallet, payment, gateway transaction), but no controller exposes them.

---

## 14. Transactional outbox and messaging

```mermaid
flowchart LR
    T["processPayment transaction"] -->|same commit| PO[("payment_outbox<br/>PAYMENT_COMPLETED")]
    SCH["Scheduler<br/>fixedDelay 5 s, top 50"] -->|read unpublished| PO
    SCH --> REL["Relay: publish, wait for confirm"]
    REL -->|"routing key payment.completed"| EX[["fincore.events (topic)"]]
    EX -->|"binding payment.#"| Q[["fincore.payment.events"]]
    Q --> CON["PaymentEventConsumer<br/>manual ack"]
    CON --> INB[("payment_event_inbox<br/>ON CONFLICT DO NOTHING")]
    CON -.nack, no requeue.-> DLX[["fincore.events.dlx"]] --> DLQ[["fincore.payment.dlq"]]
```

**Producer side.** `PaymentOutboxService.recordPaymentCompleted` runs inside the payment transaction and inserts a `payment_outbox` row (event ID, aggregate type/ID, event type, JSON payload, attempts, last error). If serialization fails it throws, rolling back the payment. A scheduler polls the 50 oldest unpublished rows every 5 seconds. The relay publishes a persistent message, waits up to 10 seconds for a publisher confirm, treats a negative confirm or an unroutable return as failure, and then sets `published_at`; on failure it increments `attempts` and records `last_error`.

**Consumer side.** The listener (2–5 concurrent consumers, manual acknowledgement) inserts the event into `payment_event_inbox` with `ON CONFLICT (event_id) DO NOTHING`, then acknowledges. Unparseable or failing messages are rejected without requeue and go to the dead-letter queue.

**Guarantees and boundaries**

- Delivery is **at-least-once**: a crash between a confirmed publish and `markPublished` republishes the event; the inbox absorbs duplicates by event ID.
- The consumer currently records and logs the event; no downstream business action (notification, analytics, etc.) is attached.
- The poller does not lock rows, so multiple app instances could publish the same row; the inbox makes that harmless but wasteful.
- Only internal transfers emit events; Razorpay funding does not.
- There is no cap on `attempts`, no purge of published outbox rows or inbox rows, and no dead-letter reprocessing.

---

## 15. Razorpay integration and gateway transaction lifecycle

FinCore uses Razorpay **Test Mode** to fund wallets. The backend talks to `https://api.razorpay.com/v1` with HTTP Basic credentials from `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`. These stay on the server. The browser needs only the public key ID to open Checkout; the React client reads it from `VITE_RAZORPAY_KEY_ID`.

```mermaid
sequenceDiagram
    autonumber
    participant U as Browser (React)
    participant F as FinCore
    participant DB as PostgreSQL
    participant R as Razorpay

    U->>F: POST /gateway/razorpay/orders {walletId, amount, currency, idempotencyKey}
    F->>DB: find gateway_transaction by idempotency key
    F->>R: POST /orders (amount in paise, receipt)
    R-->>F: order id
    F->>DB: insert gateway_transaction (CREATED)
    F-->>U: 201 {orderId, amountInPaise, ...}
    Note over F: Wallet is NOT credited here

    U->>R: Razorpay Checkout (order_id)
    R-->>U: payment_id, order_id, signature

    U->>F: POST /gateway/razorpay/verify
    F->>DB: lock gateway_transaction by order ID (FOR UPDATE)
    alt already COMPLETED
        F-->>U: 200 (no second credit)
    else CREATED
        F->>F: HMAC-SHA256(order_id|payment_id, key secret) equals signature?
        F->>DB: lock wallet (FOR UPDATE), must be ACTIVE, currency match
        F->>F: credit wallet
        F->>DB: CREDIT ledger entry, gateway_transaction = COMPLETED, store payment ID
        F-->>U: 200 {status: COMPLETED, ...}
    end
```

### Why signature verification prevents fraudulent credits

After a successful payment, Razorpay signs `order_id|payment_id` with the account's key secret. Only a party holding that secret can produce a valid signature. `RazorpayClient.verifyPaymentSignature` recomputes the HMAC-SHA256 with the server-side secret and compares it to the client-supplied signature using `MessageDigest.isEqual` (constant-time). A client that submits an arbitrary payment ID cannot produce a matching signature, so the wallet is not credited. The credited amount and wallet come from the stored `gateway_transaction`, not from the request.

### Gateway transactions versus internal transfers

| | Internal transfer | Razorpay funding |
|---|---|---|
| Table | `payments` | `gateway_transactions` |
| Money source | Another wallet (debit + credit) | External payment (credit only) |
| Guard against duplicates | Idempotency key + unique constraint | Idempotency key, unique order ID, row lock + status check |
| Ledger | `DEBIT` + `CREDIT`, linked to payment | `CREDIT`, linked to gateway transaction |
| Outbox event | Yes | No |
| Trust boundary | Authenticated user | Razorpay signature |

### Gateway transaction lifecycle

```mermaid
stateDiagram-v2
    [*] --> CREATED: POST /orders succeeds
    CREATED --> COMPLETED: /verify with a valid signature
    CREATED --> CREATED: /verify fails (rolled back, 400)
    COMPLETED --> COMPLETED: repeated /verify (no-op)
```

`GatewayTransaction.status` is a plain string; only `CREATED` and `COMPLETED` are ever set. There is no `FAILED` or expired state, no Razorpay webhook endpoint, and no reconciliation against Razorpay. If a client never calls `/verify` after paying, the wallet is not credited.

### Test Mode setup

1. Create a Razorpay account and switch the dashboard to **Test Mode**.
2. Generate API keys under the dashboard's API key settings. Copy the Key ID (it begins `rzp_test_`) and the Key Secret.
3. Export `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` for the backend, and set `VITE_RAZORPAY_KEY_ID` for the frontend.
4. Pay in Checkout with Razorpay's documented test payment methods. No real money moves in Test Mode.

`src/main/resources/static/razorpay.html` is a standalone manual test page served at `/razorpay.html`. Replace the key ID hard-coded in that file with your own before using it.

---

## 16. Security design

| Control | Implementation |
|---|---|
| Passwords | BCrypt hashing; Bean Validation on registration |
| Authentication | Stateless JWT bearer tokens (24 h lifetime) |
| Authorization | URL rules by role plus ownership checks in controllers |
| Gateway integrity | Server-side HMAC-SHA256 signature verification, constant-time compare, row lock and status check |
| Data minimization | User search returns a masked phone and never the email |
| Secrets | JWT, Razorpay, Redis and RabbitMQ secrets come from environment variables |

Main known gaps: development defaults for secrets and credentials in `application.properties`, direct funding enabled by default, no wallet-ownership check on the Razorpay endpoints, tokens that cannot be revoked, and a JWT stored in `localStorage` by the frontend.

Full controls, the signature-verification explanation, the complete list of gaps and a suggested hardening order: **[docs/security-analysis.md](docs/security-analysis.md)**.

---

## 17. Database design

PostgreSQL is the system of record. Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate`: the application **does not create or migrate the schema**.

```mermaid
erDiagram
    users ||--o| wallets : "owns (unique user_id)"
    users }o--o{ roles : "user_roles"
    wallets ||--o{ payments : "sender_wallet_id"
    wallets ||--o{ payments : "receiver_wallet_id"
    wallets ||--o{ gateway_transactions : "wallet_id"
    wallets ||--o{ ledger_entries : "wallet_id"
    payments ||--o{ ledger_entries : "payment_id"
    gateway_transactions ||--o{ ledger_entries : "gateway_transaction_id"

    users {
        bigint id PK
        string username UK
        string email UK
        string phone UK
        string password
        enum status
    }
    wallets {
        bigint id PK
        string wallet_number UK
        bigint user_id UK
        numeric balance
        string currency
        enum status
        bigint version
    }
    payments {
        bigint id PK
        string payment_reference UK
        string idempotency_key UK
        bigint sender_wallet_id FK
        bigint receiver_wallet_id FK
        numeric amount
        enum status
    }
    gateway_transactions {
        bigint id PK
        string gateway
        string gateway_order_id UK
        string gateway_payment_id UK
        string idempotency_key UK
        bigint wallet_id FK
        numeric amount
        string status
    }
    ledger_entries {
        bigint id PK
        bigint payment_id FK
        bigint gateway_transaction_id FK
        bigint wallet_id FK
        enum entry_type
        numeric amount
        numeric balance_after
    }
```

Two tables have no foreign keys and are not shown: `payment_outbox` (UUID primary key) and `payment_event_inbox` (accessed with JDBC, keyed by `event_id`).

**Schema is not included in this repository.** The required objects, derived from the entity classes and `JdbcTemplate` SQL, are:

- Tables: `users`, `roles`, `user_roles`, `wallets`, `payments`, `gateway_transactions`, `ledger_entries`, `payment_outbox`, `payment_event_inbox`.
- PostgreSQL enum types for the `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` columns: user status, wallet status, payment status, ledger entry type.
- Constraints the application logic relies on: unique `payments.idempotency_key`, `payments.payment_reference`, `wallets.wallet_number`, `wallets.user_id`, and the unique constraints declared on `gateway_transactions`.
- Seed rows in `roles` for `ROLE_USER`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` (registration fails without `ROLE_USER`).

Exact DDL is not provided here because it cannot be verified from the source alone; Hibernate's `validate` step will report mismatches at startup. Adding versioned migrations is listed under [Future improvements](#27-future-improvements).

---

## 18. API reference

Base URL (default): `http://localhost:8081`. There is no Swagger/OpenAPI dependency, so no generated API documentation URL exists. The Postman files in the repository (`postman_collection.json`, `postman/`) describe an older project (`/transactions`, `/dashboard/*`) and are **not** a reference for this API.

Authenticated endpoints require `Authorization: Bearer <jwt>`. Worked request and response examples (register, login, payments, history, Razorpay order and verify, errors) are in **[docs/api-examples.md](docs/api-examples.md)**.

### Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Create a user and a wallet. `201`, plain-text body |
| POST | `/auth/login` | Public | Returns `{token, username, roles}` |

### Users

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/users/search?q=` | User | Find payable recipients by name, username, phone, user ID or wallet number. Excludes the caller; ≤ 10 results |

### Wallets

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/wallet/me` | User | Caller's wallet (created if missing) |
| GET | `/wallet/{walletNumber}` | Owner or admin | Wallet by 12-digit wallet number |
| GET | `/wallet/{walletId}/balance` | Owner or admin | Balance as a bare number |
| POST | `/wallet/{walletId}/fund?amount=` | Admin; owner only if `fincore.dev-funding-enabled=true` | Direct credit, no ledger entry |

### Payments

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments` | User (sender wallet owner) | Wallet-to-wallet transfer. `201` |
| GET | `/payments/me?limit=` | User | Caller's payment history; `limit` default 50, clamped to 1–200 |
| GET | `/payments/{id}` | Participant or admin | Payment by ID |
| GET | `/payments/idempotency/{key}` | Participant or admin | Payment by idempotency key |

### Razorpay gateway

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/gateway/razorpay/orders` | Authenticated | Create a Razorpay order and a `CREATED` gateway transaction. `201` |
| POST | `/gateway/razorpay/verify` | Authenticated | Verify the signature and credit the wallet. `200` |

### Administration

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/wallets/{walletId}` | Admin | Wallet details |
| POST | `/admin/wallets/{walletId}/freeze` | Admin | Set status `FROZEN` |
| POST | `/admin/wallets/{walletId}/activate` | Admin | Set status `ACTIVE` |

There is no endpoint to grant roles. Admin users are created by inserting into `user_roles`.

### Static

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/razorpay.html` | Public | Manual Razorpay Checkout test page |

### Error format

All handled errors use this body:

```json
{
  "timestamp": "2026-11-01T18:30:00.123",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient wallet balance",
  "path": "/payments"
}
```

| Status | Source |
|---|---|
| `400` | Bean Validation failures (`error: "Validation Failed"`, field messages joined) **and every business-rule `RuntimeException`**: insufficient balance, wallet not found, wallet not active, currency mismatch, invalid signature, invalid credentials, duplicate username/email/phone |
| `403` | `AccessDeniedException` (not owner, direct funding disabled, URL rule denies the role) |
| `500` | Any other unhandled exception |

All business failures map to `400`; the API does not use `404` or `409`. The response a client receives for a missing or expired token depends on Spring Security's default entry point (no custom one is configured) and was not verified by running the application.

---

## 19. Project structure

```text
.
├── pom.xml                         # Maven build (Spring Boot 4.0.4, Java 21)
├── mvnw, mvnw.cmd                  # Maven wrapper
├── Dockerfile                      # JRE 21 image; copies target/*.jar
├── .github/workflows/ci-cd.yml     # Build, test, push image to GHCR
├── payment-load.js                 # k6: internal transfer load test
├── razorpay-load-test.js           # k6: Razorpay order-creation test
├── payment-results.json            # Committed k6 summary export (see docs/performance-testing.md)
├── fincore-frontend/               # React + Vite client
│   └── src/{api.js, auth.jsx, wallet.jsx, pages/*}
├── docs/                           # api-examples.md, performance-testing.md, security-analysis.md
├── src/main/java/com/fincore/backend/
│   ├── FinCoreApplication.java
│   ├── controller/                 # Auth, User, Wallet, Payment, Admin
│   ├── gateway/                    # Razorpay controller, service, client, config, entity, DTOs
│   ├── service/                    # Auth, Wallet, Payment, outbox, event processing, user search
│   ├── messaging/                  # RabbitMQ topology and consumer
│   ├── repository/                 # JPA repositories + JDBC inbox repository
│   ├── entity/  enums/  dto/       # Domain model and DTOs
│   ├── security/                   # SecurityConfig, JwtFilter, JwtUtil, OAuth2 handler (unused)
│   ├── cache/                      # Redis cache key generator
│   ├── ratelimit/                  # RateLimitFilter (disabled)
│   └── exception/                  # GlobalExceptionHandler, ErrorResponse
├── src/main/resources/
│   ├── application.properties
│   └── static/razorpay.html        # Manual Checkout test page
├── src/test/java/...               # Context and connection tests
├── postman_collection.json, postman/   # Legacy; does not describe this API
├── wallet-concurrency-audit.txt    # Source snapshot of an earlier version (not test output)
└── fincore-integration-files.txt   # Source snapshot of an earlier version
```

---

## 20. Environment configuration

Spring Boot reads these from the process environment. The application does not load `.env` files itself; export the variables (or use your IDE, `docker run --env-file`, etc.). `.env` is git-ignored.

| Variable | Used for | Default in `application.properties` |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8081` |
| `APP_NAME` | `spring.application.name` | `fincore` |
| `JWT_SECRET` | JWT signing key (must be at least 32 bytes) | Development value present; **override it** |
| `RAZORPAY_KEY_ID` | Razorpay Basic-auth user | None (**required**) |
| `RAZORPAY_KEY_SECRET` | Razorpay Basic-auth password and signature key | None (**required**) |
| `REDIS_HOST`, `REDIS_PORT` | Redis connection | `localhost`, `6379` |
| `REDIS_PASSWORD` | Redis password | Placeholder default present; override |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | RabbitMQ connection | `localhost`, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ credentials | Broker defaults present; override |
| `RABBITMQ_VHOST` | Virtual host | `/` |

The database URL, username and password are **hard-coded** in `application.properties` (`localhost:5432/fincore`). Override them with Spring Boot's standard property-to-environment mapping: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Similarly, `jwt.expiration` is fixed at 24 hours, and `fincore.dev-funding-enabled` is read as a property; pass it as `--fincore.dev-funding-enabled=false` on the command line.

Frontend variables (`fincore-frontend`):

| Variable | Used for |
|---|---|
| `VITE_API_URL` | API base path; defaults to `/api` (proxied in dev) |
| `VITE_BACKEND_URL` | Dev-proxy target in `vite.config.js`; defaults to `http://localhost:8080` |
| `VITE_RAZORPAY_KEY_ID` | Razorpay public key ID for Checkout |

Template (copy to `.env` and fill in; placeholders only):

```dotenv
# Server
SERVER_PORT=8081
APP_NAME=fincore

# Security
JWT_SECRET=<random-string-of-at-least-32-bytes>

# Razorpay (Test Mode keys)
RAZORPAY_KEY_ID=rzp_test_<your-key-id>
RAZORPAY_KEY_SECRET=<your-key-secret>

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<rabbitmq-user>
RABBITMQ_PASSWORD=<rabbitmq-password>
RABBITMQ_VHOST=/

# PostgreSQL (Spring Boot standard overrides)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fincore
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
```

Never commit real values for the JWT secret, database password, Razorpay secret, or any token.

---

## 21. Local development setup

**Prerequisites:** JDK 21, PostgreSQL, Redis (with a password), RabbitMQ, and Node.js for the frontend. k6 is needed only for load tests. The Maven wrapper is included. The repository has no Docker Compose file.

1. **Clone**

   ```bash
   git clone https://github.com/ravimnm/fincore-payment-platform.git
   cd fincore-payment-platform
   ```

2. **Create the database** named `fincore` and create the schema described in [Database design](#17-database-design). The application will not create tables.

3. **Seed roles** (registration needs `ROLE_USER`):

   ```sql
   INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN'), ('ROLE_SUPER_ADMIN');
   ```

   To make a user an admin, insert a row into `user_roles (user_id, role_id)` for that user and the admin role.

4. **Start Redis and RabbitMQ** (any method). Example with Docker:

   ```bash
   docker run -d --name fincore-redis -p 6379:6379 redis redis-server --requirepass '<redis-password>'
   docker run -d --name fincore-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:management
   ```

5. **Configure the environment** as in [Environment configuration](#20-environment-configuration), including Razorpay Test Mode keys.

6. **Frontend** (optional):

   ```bash
   cd fincore-frontend
   npm install
   echo "VITE_RAZORPAY_KEY_ID=rzp_test_<your-key-id>" > .env
   ```

   The frontend README mentions a `.env.example`, which is not in the repository.

---

## 22. Running the application

**Backend**

```bash
set -a; source .env; set +a      # export the variables from your .env
./mvnw spring-boot:run
```

The API listens on port `8081` unless `SERVER_PORT` is set.

**Frontend**

```bash
cd fincore-frontend
VITE_BACKEND_URL=http://localhost:8081 npm run dev
```

The dev server runs on `http://localhost:5173` and proxies `/api/*` to the backend. `VITE_BACKEND_URL` is read by `vite.config.js` from the shell environment (not from `.env`), and its default is `8080`, so set it when the backend is on `8081`. `localhost:5173` is in the backend CORS allow-list.

**Docker**

```bash
./mvnw clean package -DskipTests
docker build -t fincore .
docker run --rm -p 8081:8081 --env-file .env fincore
```

`Dockerfile` declares `EXPOSE 8080` but the app listens on `SERVER_PORT` (default `8081`); publish the port you configure. Inside a container, `localhost` is the container itself, so set `SPRING_DATASOURCE_URL`, `REDIS_HOST` and `RABBITMQ_HOST` to hosts the container can reach.

---

## 23. Testing strategy

**Automated tests in the repository** (`src/test/java`):

| Test | What it checks |
|---|---|
| `SecureAccessControlServiceApplicationTests` | Spring context loads |
| `RedisConnectionTest` | Write and read a key in Redis |
| `RabbitMQConnectionTest` | A RabbitMQ channel opens |

All three are `@SpringBootTest` tests that need live PostgreSQL, Redis, RabbitMQ and `RAZORPAY_*` variables. There are **no unit tests** for payment, wallet, idempotency or signature logic, and **no concurrency tests**.

**CI** (`.github/workflows/ci-cd.yml`): on push to `main` (or manual dispatch) it runs `mvn clean verify`, builds the Docker image, and pushes it to GHCR. The workflow defines no service containers or environment variables, so whether those integration tests pass in CI was not verified here.

**Load tests** with k6 are the only evidence about behavior under concurrency (see [Performance testing and benchmarks](#24-performance-testing-and-benchmarks)). They check HTTP status codes; they do not verify that balances and ledger sums remain consistent afterwards, and intermittent optimistic-lock failures have appeared in them ([Concurrency testing status](#12-concurrency-testing-status)).

---

## 24. Performance testing and benchmarks

> **Every figure below comes from a local development run. None is a production benchmark, and none establishes production capacity.** The runs differ in date, VU count, duration and configuration, so they are not a trend. Methodology, full per-run details and the evidence behind each figure: **[docs/performance-testing.md](docs/performance-testing.md)**.

Two [Grafana k6](https://k6.io) scripts are in the repository root: `payment-load.js` (internal transfers, ramp to 1,000 VUs over 180 s as committed) and `razorpay-load-test.js` (Razorpay order creation at 2 requests/s for 8 m 20 s). The two targets measure different things and are never combined.

### Latest run: 10 VUs, 20 seconds (local test run)

Internal transfers (`POST /payments`). **Maintainer-reported; no result file is committed.** Script version, user and wallet counts, hardware and logging level were not supplied.

| Metric | Reported |
|---|---:|
| Total requests | 1,585 |
| Successful | 1,584 |
| Failed | 1 (HTTP 400, reported as an optimistic-lock failure) |
| Throughput | 79.095 req/s |
| Average latency | 124.1 ms |
| P95 latency | 254.85 ms |

### All runs on record

| Run | Date | Target | VUs | Duration | Requests | Failures | Avg / P95 | Evidence |
|---|---|---|---|---|---:|---|---|---|
| A | Unrecorded (first reported 2026-08-10) | `/payments` | 1,000 peak | ≈ 180 s | 50,682 | 0 (reported) | 1.74 s / 5.59 s | Historical, unverified |
| B | Unrecorded (first reported 2026-08-24) | Razorpay orders | 5 | 500 s | 999 | 0 (reported) | 173.17 ms / 256.87 ms | Historical, unverified |
| C | Unrecorded (committed 2026-11-01) | `/payments` | 75 peak | ≈ 22.7 s | 1,747 | 8, cause unknown | 463.36 ms / 1,075.48 ms | Artifact: `payment-results.json` |
| D | Unrecorded (reported 2026-11-01) | `/payments` | 10 | 20 s | 1,585 | 1 (HTTP 400, optimistic lock) | 124.1 ms / 254.85 ms | Maintainer-reported |

Reading these correctly:

- Run D's lower latency reflects a far lighter load than Runs A and C; it says nothing about behavior at 1,000 VUs.
- Run B measures the external Razorpay API and its rate limit, not FinCore's transfer path.
- None of the runs checked that balances and ledger totals stayed consistent, so none demonstrates the absence of lost updates. The optimistic-lock failure reported in Run D (Run C's failures have no recorded cause), and the [open concurrency issue](#12-concurrency-testing-status), mean the historical zero-failure figure in Run A should be treated with caution.
- The committed `application.properties` enables SQL and `TRACE` logging and caps the connection pool at 50; whether this applied to any run is unknown.

---

## 25. Deployment architecture

What the repository supports:

```mermaid
flowchart LR
    DEV["Push to main"] --> GHA["GitHub Actions<br/>mvn clean verify"]
    GHA --> BUILD["docker build<br/>eclipse-temurin:21-jre"]
    BUILD --> GHCR[("ghcr.io image<br/>tags: latest, commit SHA")]
```

The pipeline stops at publishing the image to GitHub Container Registry. The repository contains no Docker Compose file, Kubernetes manifests, infrastructure-as-code, or deploy step, and nothing here establishes where the service is hosted. The backend CORS configuration contains a Vercel placeholder origin, which suggests the frontend was meant to be deployed there, but that is not confirmed.

A deployment needs PostgreSQL with the schema applied, Redis, RabbitMQ, and the environment variables from [Environment configuration](#20-environment-configuration).

---

## 26. Engineering trade-offs and limitations

**Configuration and secrets**

| Issue | Detail |
|---|---|
| Development defaults | `application.properties` contains a default JWT secret, the database username and password, a default Redis password and RabbitMQ `guest` credentials. Anyone who knows the default JWT secret can forge tokens for a deployment that did not override it. |
| Direct funding on | `fincore.dev-funding-enabled=true` is committed; it lets a wallet owner credit their own wallet without payment or ledger entry. |
| Verbose logging | SQL, bind parameters and transaction internals are logged at `DEBUG`/`TRACE`. |
| Credentials in tests | The k6 scripts embed pre-generated JWTs. |
| Schema | Not versioned (no Flyway/Liquibase); `ddl-auto=validate` only. |

**Correctness and consistency**

| Issue | Detail |
|---|---|
| Concurrency | Intermittent optimistic-lock conflicts observed in k6 runs, including after pessimistic locks were added; root cause under investigation; concurrent financial integrity not fully validated (see [status](#12-concurrency-testing-status)). |
| Idempotency semantics | No request fingerprint; replays return `201`; concurrent-duplicate safety depends on an unversioned DB constraint. |
| Ledger | Not a full double-entry system; direct funding bypasses it; no reconciliation; "immutable" is by convention. |
| Gateway | No webhook, no `FAILED`/expired state, no reconciliation with Razorpay; a missed `/verify` call means no credit. |
| Transaction scope | `createOrder` holds a database transaction open during the outbound Razorpay call; the `RestClient` has no explicit timeouts. |
| Lock handling | No lock-wait timeout or retry; hot wallets serialize all their transfers. |
| Error model | Every business error is `400`; messages are exposed to clients (`server.error.include-message=always`). |
| Single currency | Wallets are created in `INR`; there is no currency conversion. |

**Security**

See [docs/security-analysis.md](docs/security-analysis.md) for the full list. Highlights: no wallet-ownership check on the Razorpay endpoints; JWTs cannot be revoked and the frontend stores one in `localStorage`; the rate-limit filter and OAuth2 handler are present but not active.

**Messaging**

| Issue | Detail |
|---|---|
| Outbox | At-least-once; no row locking across instances; no retry cap; no cleanup; console logging in the scheduler. |
| Consumer | Records events only; no downstream effect; no DLQ reprocessing. |

**Testing**

| Issue | Detail |
|---|---|
| Coverage | Three integration smoke tests; nothing exercises transfer, idempotency, locking or signature logic. |
| Benchmarks | Local only; result artifacts for most figures are not committed; runs differ in configuration and are not comparable; no integrity assertions. |

---

## 27. Future improvements

Ordered roughly by how directly they close the gaps above.

1. **Diagnose the optimistic-lock conflicts, then add concurrency and integrity tests.** First capture and reproduce a failing request (see [status](#12-concurrency-testing-status)). Then add Testcontainers-based tests that run many parallel opposite-direction transfers and duplicate-key requests, then assert conserved totals and ledger/balance agreement.
2. **Versioned schema.** Add Flyway or Liquibase migrations (including enum types, constraints and role seed data) and make the idempotency constraint explicit.
3. **Safer defaults.** Remove default secrets and credentials, default `dev-funding-enabled` to `false`, turn off SQL/TRACE logging outside a dev profile, and move load-test tokens to environment variables.
4. **Razorpay hardening.** Enforce wallet ownership on both endpoints, add a webhook and reconciliation job, model `FAILED`/expired states, and set client timeouts.
5. **Ledger.** Record direct funding in the ledger, add offsetting gateway entries, and add a reconciliation check.
6. **Idempotency.** Store a request hash and reject mismatched reuse; return a distinguishable replay response; handle the unique-violation race explicitly.
7. **Error model.** Introduce typed exceptions mapped to `404`, `409`, `422` and `401`.
8. **Outbox.** Use `FOR UPDATE SKIP LOCKED`, cap attempts, purge old rows, and add a real consumer and DLQ handling.
9. **Abuse protection.** Replace the disabled filter with a Redis-backed limiter or gateway-level limits.
10. **Documentation and ops.** Add OpenAPI docs, a Docker Compose file for local dependencies, and a deployment step to the pipeline.

---

## 28. License

No `LICENSE` file is present in the repository. Until the owner adds one, the code is not licensed for reuse.

---

## 29. Documentation notes

An earlier, longer version of this README (about 1,450 lines) is preserved in git history at commit `09604fc`. The `README.md` at the previous `HEAD` was a shorter, Razorpay-focused version. This version supersedes both and differs from the older text in these ways:

- Wallet freeze and activate are `POST /admin/wallets/{walletId}/freeze|activate`, not `/wallet/{walletId}/...`.
- There are no ledger API endpoints; ledger data is written but not exposed.
- Rate limiting and Google OAuth2 login are not active in the running application.
- The Razorpay public key reaches the frontend through `VITE_RAZORPAY_KEY_ID`, not from a backend endpoint.
- Redis caching, the transactional outbox, RabbitMQ messaging, the admin API and the React client are documented here and were absent from the shorter README.
- Benchmark figures are labelled by how well the repository supports them.
- This revision adds the [Concurrency testing status](#12-concurrency-testing-status) section, reports the latest 10-VU / 20 s run as maintainer-reported, and moves API examples, performance methodology and extended security analysis into `docs/`. The optimistic-lock observations and the 10-VU / 20 s figures come from the maintainer and are not backed by a committed log or result file.
