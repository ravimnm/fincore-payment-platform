# FinCore

FinCore is a production-inspired financial backend built with Java 21 and Spring Boot. It implements secure user authentication, JWT-based authorization, wallet management, wallet-to-wallet payment processing, Razorpay payment gateway integration, transaction consistency, idempotent payment handling, double-entry-style ledger records, concurrency control, and API load testing.

The system models two payment flows: internal wallet-to-wallet transfers protected by transactional concurrency controls, and external wallet funding through Razorpay Test Mode with server-side payment signature verification.

---

# Technology Stack

## Backend

* Java 21
* Spring Boot 4
* PostgreSQL

## Payment Gateway

* Razorpay Test Mode
* Razorpay Checkout
* Razorpay Orders API
* HMAC-SHA256 payment signature verification

## Deployment & Infrastructure

* Docker
* GitHub Actions (CI/CD)
* GitHub Container Registry
* Render / Render PostgreSQL

---

# Razorpay Payment Gateway Integration

FinCore integrates with **Razorpay Test Mode** to support external wallet funding.

The integration separates external payment processing from the internal wallet ledger. A Razorpay order is created first, and the FinCore wallet is credited only after the Razorpay payment response is successfully verified.

## Payment Flow

```text
User
 │
 ▼
FinCore Authentication
 │
 ▼
Create Razorpay Order
 │
 ▼
Razorpay Test Mode
 │
 ▼
Razorpay Checkout
 │
 ▼
Payment Success
 │
 ├── razorpay_payment_id
 ├── razorpay_order_id
 └── razorpay_signature
 │
 ▼
FinCore /gateway/razorpay/verify
 │
 ▼
HMAC-SHA256 Signature Verification
 │
 ▼
Lock FinCore Wallet
 │
 ▼
Credit Wallet
 │
 ▼
Mark GatewayTransaction = COMPLETED
 │
 ▼
Create Ledger Entry
 │
 ▼
PostgreSQL Transaction Commit
```

### Gateway Order Creation

```http
POST /gateway/razorpay/orders
```

Example request:

```json
{
    "walletId": 3,
    "amount": 1000.00,
    "currency": "INR",
    "idempotencyKey": "razorpay-ravi-001",
    "description": "FinCore wallet funding"
}
```

FinCore creates the Razorpay order and persists the gateway transaction locally.

Example response:

```json
{
    "gateway": "RAZORPAY",
    "orderId": "order_xxxxxxxxxxxxx",
    "walletId": 3,
    "amount": 1000.00,
    "amountInPaise": 100000,
    "currency": "INR",
    "status": "created",
    "idempotencyKey": "razorpay-ravi-001"
}
```

The wallet is **not credited during order creation**.

---

## Payment Verification

After Razorpay Checkout succeeds, the client sends the payment details to:

```http
POST /gateway/razorpay/verify
```

Request:

```json
{
    "razorpayOrderId": "order_xxxxxxxxxxxxx",
    "razorpayPaymentId": "pay_xxxxxxxxxxxxx",
    "razorpaySignature": "xxxxxxxxxxxxxxxx"
}
```

FinCore then:

1. Retrieves the gateway transaction using the Razorpay order ID.
2. Verifies the Razorpay payment signature using HMAC-SHA256.
3. Prevents duplicate completion of an already completed transaction.
4. Locks the destination wallet using a pessimistic database lock.
5. Validates that the wallet is active.
6. Credits the wallet.
7. Stores the Razorpay payment ID.
8. Marks the gateway transaction as `COMPLETED`.
9. Persists the transaction atomically.

This ensures that a client cannot simply submit an arbitrary payment ID and cause a wallet credit.

---

# Gateway Transaction Tracking

FinCore maintains a dedicated `gateway_transactions` table.

```text
gateway_transactions
│
├── gateway
├── gateway_order_id
├── gateway_payment_id
├── idempotency_key
├── wallet_id
├── amount
├── currency
├── status
├── created_at
└── completed_at
```

Example completed transaction:

```text
Gateway:             RAZORPAY
Gateway Order:       order_TTHmoe4ifiPsN9
Gateway Payment:     pay_TTHtfWUHPyXO5f
Wallet:              3
Amount:              ₹1,000.00
Currency:            INR
Status:              COMPLETED
```

The gateway transaction acts as the bridge between the external payment provider and FinCore's internal wallet ledger.

---

# Gateway Idempotency

Each gateway order requires an idempotency key.

If the same request is submitted again with the same idempotency key, FinCore reuses the existing gateway transaction instead of unnecessarily creating another Razorpay order.

This protects against:

```text
Client retry
     ↓
Network timeout
     ↓
Duplicate order request
```

The application can identify the previously created gateway transaction using:

```text
idempotency_key
```

The database also enforces uniqueness on the idempotency key.

---

# Razorpay Security

Razorpay API credentials are used only by the backend.

```text
Razorpay Key ID
Razorpay Key Secret
        │
        ▼
Backend RazorpayClient
        │
        ▼
Razorpay API
```

The Razorpay secret is never exposed to the browser.

The frontend receives only the Razorpay public key required to initialize Checkout.

Payment verification is performed server-side using the Razorpay secret.

---

# Razorpay Load Testing (Local Benchmark)

Razorpay Test Mode was benchmarked separately from FinCore's internal wallet-to-wallet transfer benchmark.

The purpose of this test was to evaluate the application's **external payment gateway integration and order-creation path**, rather than the capacity of Razorpay's production infrastructure.

The load test uses Grafana k6.

```powershell
k6 run .\razorpay-load-test.js
```

## Controlled Gateway Load Test

The final benchmark used a controlled arrival rate of:

```text
2 Razorpay order requests / second
```

for approximately:

```text
8 minutes 20 seconds
```

This rate was intentionally limited because higher request rates caused Razorpay Test Mode to return:

```text
429 Too Many Requests
```

The rate limiting occurred at the external Razorpay API rather than inside the FinCore wallet transaction engine.

### Results

| Metric                    |              Result |
| ------------------------- | -------------------: |
| Scheduled iterations      |               1,000 |
| Completed HTTP requests   |             **999** |
| Dropped iterations        |               **1** |
| Successful HTTP requests  |      **999 (100%)** |
| HTTP failure rate         |              **0%** |
| Average latency           |       **173.17 ms** |
| Median latency            |       **140.08 ms** |
| P90 latency               |       **217.95 ms** |
| P95 latency               |       **256.87 ms** |
| Maximum latency           |          **2.92 s** |
| Throughput                | **~2 requests/sec** |
| Maximum VUs used          |               **5** |

All application-level checks passed:

```text
✓ status is 201
✓ gateway is RAZORPAY
✓ orderId exists
✓ amount is correct
```

The final run produced:

```text
999 / 999 successful HTTP requests
0 / 999 HTTP failures
(1,000 iterations scheduled, 1 dropped)
```

---

## External Gateway Rate Limiting

A higher-concurrency experiment attempted 1,000 order creations using 20 VUs.

The result was:

```text
1,000 requests
      ↓
63 successful
      ↓
937 HTTP 429 responses
```

The `429 Too Many Requests` responses originated from Razorpay Test Mode.

This demonstrated an important characteristic of external integrations:

> Increasing application concurrency does not necessarily increase end-to-end throughput when the external dependency becomes the bottleneck.

Therefore, the controlled 2 requests/second benchmark is used as the representative Razorpay integration benchmark rather than the 20-VU test.

---

# Internal Payment vs Razorpay Funding

FinCore contains two distinct money-movement paths.

### Wallet-to-Wallet Transfer

```text
Authenticated User
       ↓
Payment API
       ↓
Idempotency
       ↓
Deterministic Lock Ordering
       ↓
Pessimistic Wallet Locks
       ↓
Balance Validation
       ↓
Debit Sender
       ↓
Credit Receiver
       ↓
Payment Record
       ↓
Ledger Entries
       ↓
PostgreSQL Commit
```

This path is responsible for the application's **high-concurrency transactional payment benchmark**.

### External Wallet Funding

```text
Authenticated User
       ↓
FinCore
       ↓
Razorpay Order
       ↓
Razorpay Checkout
       ↓
Payment Verification
       ↓
Signature Validation
       ↓
Wallet Lock
       ↓
Wallet Credit
       ↓
Ledger
       ↓
PostgreSQL
```

The Razorpay integration is therefore an **external funding mechanism**, while the wallet-to-wallet payment engine handles internal transfers.

---

# Performance Benchmarks

FinCore has two separate performance benchmarks.

## Internal Wallet Transfer Benchmark

The wallet-to-wallet payment engine was load-tested using authenticated users and concurrent payment requests.

The benchmark exercises:

* JWT authentication
* Payment API
* Idempotency
* Pessimistic wallet locking
* Deterministic lock ordering
* Balance validation
* Debit and credit operations
* Payment persistence
* Ledger persistence
* PostgreSQL transactions

Previously measured benchmark:

| Metric                         |            Result |
| ------------------------------- | -----------------: |
| Maximum Concurrent VUs         |         **1,000** |
| Authenticated Load-Test Users  |            **25** |
| Total Requests                 |        **50,682** |
| Successful Requests            | **50,682 (100%)** |
| Failed Requests                |        **0 (0%)** |
| Throughput                     |  **281.56 req/s** |
| Average Latency                |        **1.74 s** |
| Median Latency                 |        **1.26 s** |
| P90 Latency                    |        **3.83 s** |
| P95 Latency                    |        **5.59 s** |
| Maximum Latency                |       **16.88 s** |

These results represent a local development benchmark and should not be interpreted as production capacity.

---

## Razorpay Integration Benchmark

The Razorpay benchmark measures the external gateway order-creation path separately.

| Metric                   |        Result |
| ------------------------- | -------------: |
| Scheduled Iterations     |     **1,000** |
| Completed HTTP Requests  |       **999** |
| HTTP Success Rate        |      **100%** |
| HTTP Failure Rate        |        **0%** |
| Average Latency          | **173.17 ms** |
| P95 Latency              | **256.87 ms** |
| Maximum Latency          |    **2.92 s** |
| Controlled Rate          |   **2 req/s** |

The gateway benchmark is intentionally separated from the internal payment benchmark because the external Razorpay API imposes its own rate limits and network characteristics.

---

# Engineering Guarantees

FinCore now provides two complementary payment mechanisms:

```text
                    FinCore
                       │
          ┌────────────┴────────────┐
          │                         │
   Internal Transfers          External Funding
          │                         │
          ▼                         ▼
 Wallet → Wallet                Razorpay
          │                         │
          ▼                         ▼
Concurrency Control          Gateway Integration
Idempotency                   Order Creation
Transactions                  Signature Verification
Pessimistic Locks             Gateway Idempotency
Double-entry-style Ledger     Wallet Credit
          │                         │
          └────────────┬────────────┘
                       ▼
                    PostgreSQL
```

The system therefore demonstrates both:

* **transactional wallet-to-wallet payment processing**, and
* **integration with an external payment gateway for wallet funding**.

---

# ☁️ Production Deployment

FinCore is deployed as a containerized Spring Boot backend using GitHub Actions and Render.

## Deployment Pipeline

```text
GitHub
   ↓
GitHub Actions
   ↓
Maven Tests
   ↓
Build JAR
   ↓
Build Docker Image
   ↓
GitHub Container Registry
   ↓
Render
   ↓
FinCore Spring Boot API
   ↓
Render PostgreSQL
```

Every push to the `main` branch triggers the CI/CD workflow, which automatically:

* Checks out the latest source code
* Sets up Java 21
* Runs the Maven test suite
* Builds the Spring Boot JAR
* Builds a Docker image
* Publishes the image to GitHub Container Registry
* Deploys the application through Render

## Production Infrastructure

The deployed environment uses:

* **Render** — application hosting
* **Render PostgreSQL** — production database
* **Docker** — application containerization
* **GitHub Actions** — CI/CD automation
* **GitHub Container Registry** — Docker image registry
* **Spring Boot 4 / Java 21** — application runtime

Production configuration is supplied through environment variables rather than hard-coded credentials.

Configured production secrets include:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
RAZORPAY_KEY_ID
RAZORPAY_KEY_SECRET
```

This keeps database credentials, JWT signing secrets, and payment-gateway credentials outside the source repository.

---

# 💳 Razorpay Integration & Test Interface

FinCore includes a Razorpay payment-gateway integration for creating and verifying payment orders through the backend, as described above.

To exercise this flow end-to-end, a small **Razorpay test interface** was also built. This is **not a complete FinCore frontend** — the main application is a backend/API system. The UI exists solely to drive and demonstrate the Razorpay payment flow (order creation, Checkout, and verification) against the deployed API.

```text
Razorpay Test UI
       ↓
FinCore REST API
       ↓
Payment Service
       ↓
Razorpay Gateway
       ↓
Razorpay Order
```

Authentication, authorization, payment validation, signature verification, wallet locking, and ledger persistence all remain on the server side, as documented above.

The deployed system has been used to successfully exercise wallet creation, wallet-to-wallet transfers, and Razorpay order creation/verification against the production backend.

---

# ⚡ Production Load Testing

The deployed Razorpay order-creation endpoint (on Render) was also tested using Grafana k6, using the same controlled-rate approach as the local benchmark above.

The test scheduled 1,000 iterations at a controlled rate of approximately 2 requests/second.

**Results:**

* 1,000 iterations scheduled
* 999 HTTP requests completed
* 1 dropped iteration
* 0% HTTP failure rate (999/999 successful)
* 100% application-level checks passed
* Average latency: ~173 ms
* P95 latency: ~257 ms
* Maximum latency: ~2.92 s

The test was deliberately rate-limited rather than aggressively saturating the endpoint, avoiding Razorpay's external API rate limits while measuring the deployed application's behavior.

---

# 🌐 Live API

**Production:** https://fincore-aeg6.onrender.com

FinCore is primarily exposed as a REST API. The Razorpay test interface is provided only for demonstrating and testing the payment integration, not as a full application frontend.

The deployment is continuously updated through the GitHub Actions pipeline whenever changes are pushed to `main`.
