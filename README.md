# FinCore - Secure Payment Processing Platform

FinCore is a production-inspired financial backend built with Java 21 and Spring Boot. It implements secure user authentication, wallet management, payment processing, transaction consistency, idempotent payment handling, double-entry-style ledger records, concurrency control, and API load testing.

The system is designed around a core requirement of financial software:

> **Money movement must remain correct even when requests are concurrent, duplicated, or fail during processing.**

FinCore uses PostgreSQL for transactional persistence, JWT-based authentication, pessimistic database locking for concurrent wallet operations, and Grafana k6 for performance testing.

---

## Features

### Authentication & Authorization

* User registration
* User login
* BCrypt password hashing
* JWT authentication
* JWT role claims
* Role-based authorization
* User account status validation
* Stateless Spring Security configuration

Supported roles:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_USER
```

---

### Wallet Management

* Wallet creation
* Unique wallet number generation
* Wallet balance management
* Wallet activation
* Wallet freezing
* Wallet status validation
* Wallet funding
* Currency support
* Optimistic version field
* Pessimistic locking for transaction processing

Wallet states:

```text
ACTIVE
FROZEN
CLOSED
```

Each user can have a maximum of one wallet.

---

### Payment Processing

FinCore implements an atomic wallet-to-wallet payment workflow.

A payment performs:

```text
Validate request
      ↓
Check idempotency key
      ↓
Lock sender wallet
      ↓
Lock receiver wallet
      ↓
Validate wallet status
      ↓
Validate currency
      ↓
Validate balance
      ↓
Debit sender
      ↓
Credit receiver
      ↓
Create payment
      ↓
Create ledger entries
      ↓
Commit transaction
```

Features include:

* Wallet-to-wallet transfers
* Atomic database transactions
* Sender balance validation
* Currency validation
* Sender/receiver validation
* Payment references
* Payment status tracking
* Payment descriptions
* Idempotency protection
* Concurrent transaction handling
* Transaction rollback through Spring transactions

---

### Idempotency

Payment requests require an idempotency key.

Example:

```json
{
    "senderWalletId": 4,
    "receiverWalletId": 3,
    "amount": 1000.00,
    "currency": "INR",
    "idempotencyKey": "payment-ravi-ramana-001",
    "description": "Test payment"
}
```

If the same idempotency key is submitted again, FinCore returns the existing payment instead of processing the transfer again.

This prevents duplicate money movement caused by:

* Client retries
* Network timeouts
* Duplicate HTTP requests
* Application-level retries

---

### Concurrency Control

Payment processing uses PostgreSQL pessimistic write locks.

Wallets are locked using:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

The payment service also locks wallets in deterministic ID order.

For example:

```text
Transaction A:
Wallet 3 → Wallet 4

Transaction B:
Wallet 4 → Wallet 3
```

Both transactions acquire locks in the same order:

```text
Wallet 3
   ↓
Wallet 4
```

This reduces the possibility of deadlocks caused by opposite lock acquisition order.

The locking strategy ensures that concurrent transfers cannot incorrectly modify the same wallet balance.

---

## Ledger

Every completed payment creates ledger entries.

For example:

```text
Payment: ₹1,000

Sender Wallet
    DEBIT  ₹1,000
    balance_after = ₹29,000

Receiver Wallet
    CREDIT ₹1,000
    balance_after = ₹6,000
```

Ledger entries contain:

* Payment reference
* Wallet
* Entry type
* Amount
* Balance after transaction
* Creation timestamp

Supported entry types:

```text
DEBIT
CREDIT
```

The ledger provides a transaction history that can be used to reconstruct wallet activity.

---

# Architecture

FinCore follows a layered Spring Boot architecture.

```text
                         ┌──────────────────────┐
                         │       Client         │
                         │ Postman / k6 / UI    │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    Spring Security   │
                         │      JWT Filter      │
                         │     RBAC / Auth      │
                         └──────────┬───────────┘
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │        REST Controllers      │
                     │                             │
                     │ Auth │ Wallet │ Payment     │
                     └──────────────┬──────────────┘
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │          Services            │
                     │                             │
                     │ AuthService                  │
                     │ WalletService                │
                     │ PaymentService               │
                     └──────────────┬──────────────┘
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │       Spring Data JPA        │
                     │        Repositories          │
                     └──────────────┬──────────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │      PostgreSQL      │
                         │                      │
                         │ Users                │
                         │ Roles                │
                         │ Wallets              │
                         │ Payments             │
                         │ Ledger Entries       │
                         └──────────────────────┘
```

---

# Transaction Processing Architecture

The core payment transaction is intentionally designed around database consistency.

```text
                 Payment Request
                       │
                       ▼
              Validate Request
                       │
                       ▼
             Idempotency Check
                       │
                       ▼
             Acquire Wallet Locks
                       │
                ┌──────┴──────┐
                ▼             ▼
          Sender Wallet   Receiver Wallet
                │             │
                ▼             ▼
             Validate State
                    │
                    ▼
             Validate Balance
                    │
                    ▼
              Debit Sender
                    │
                    ▼
             Credit Receiver
                    │
                    ▼
             Create Payment
                    │
                    ▼
          Create Ledger Entries
                    │
                    ▼
             Database Commit
```

All payment operations execute inside a Spring `@Transactional` boundary.

---

# Database Model

The core entities are:

```text
User
 │
 ├── Roles
 │
 └── Wallet
        │
        ├── Payments (sender)
        │
        ├── Payments (receiver)
        │
        └── Ledger Entries
                │
                └── Payment
```

### Main tables

```text
users
roles
user_roles
wallets
payments
ledger_entries
```

---

# Security

## JWT Authentication

Authentication is stateless.

After successful login, the server returns a JWT containing:

```text
subject
roles
issued-at
expiration
```

Example payload:

```json
{
    "sub": "ramanagadu",
    "roles": [
        "ROLE_USER"
    ],
    "iat": "...",
    "exp": "..."
}
```

The JWT is then supplied with protected requests:

```http
Authorization: Bearer <JWT>
```

---

## Password Security

Passwords are never stored in plaintext.

FinCore uses:

```text
BCryptPasswordEncoder
```

The database stores BCrypt password hashes.

---

## Role-Based Access Control

Protected endpoints are secured through Spring Security.

Example:

```text
/admin/**
    ROLE_SUPER_ADMIN
    ROLE_ADMIN

/wallet/**
    authenticated users

/payments/**
    authenticated users

/ledger/**
    authenticated users
```

Authentication endpoints remain publicly accessible:

```text
/auth/register
/auth/login
```

---

# API

## Authentication APIs

### Register

```http
POST /auth/register
```

Request:

```json
{
    "firstName": "Ravi",
    "lastName": "Sankar",
    "username": "ravi_test",
    "email": "ravi.test@fincore.com",
    "phone": "9876543210",
    "password": "password"
}
```

Response:

```text
User Registered Successfully
```

---

### Login

```http
POST /auth/login
```

Request:

```json
{
    "username": "ravi_test",
    "password": "password"
}
```

Response:

```json
{
    "token": "<JWT>",
    "username": "ravi_test",
    "roles": [
        "ROLE_USER"
    ]
}
```

---

# Wallet APIs

### Create Wallet

```http
POST /wallet/user/{userId}
```

Example:

```http
POST /wallet/user/5
```

Response:

```json
{
    "id": 3,
    "walletNumber": "764373417328",
    "userId": 5,
    "balance": 0,
    "currency": "INR",
    "status": "ACTIVE"
}
```

---

### Get Wallet

```http
GET /wallet/{walletId}
```

---

### Get Balance

```http
GET /wallet/{walletId}/balance
```

---

### Fund Wallet

```http
POST /wallet/{walletId}/fund?amount=5000
```

Example:

```http
POST /wallet/3/fund?amount=5000
```

---

### Freeze Wallet

```http
POST /wallet/{walletId}/freeze
```

---

### Activate Wallet

```http
POST /wallet/{walletId}/activate
```

---

# Payment APIs

### Create Payment

```http
POST /payments
```

Request:

```json
{
    "senderWalletId": 4,
    "receiverWalletId": 3,
    "amount": 1000.00,
    "currency": "INR",
    "idempotencyKey": "payment-ravi-ramana-001",
    "description": "Test payment"
}
```

Response:

```json
{
    "id": 1,
    "paymentReference": "PAY-XXXXXXXX",
    "idempotencyKey": "payment-ravi-ramana-001",
    "senderWalletId": 4,
    "receiverWalletId": 3,
    "amount": 1000.00,
    "currency": "INR",
    "status": "COMPLETED",
    "description": "Test payment",
    "createdAt": "...",
    "completedAt": "..."
}
```

---

### Get Payment

```http
GET /payments/{id}
```

---

### Get Payment by Idempotency Key

```http
GET /payments/idempotency/{key}
```

---

# Ledger APIs

Ledger repository support includes wallet and payment transaction history.

### Wallet Ledger

```http
GET /ledger/wallet/{walletId}
```

### Payment Ledger

```http
GET /ledger/payment/{paymentId}
```

---

# Payment State

Payment lifecycle is represented using payment status values.

```text
CREATED
COMPLETED
```

Failed requests are rejected before an invalid transaction is committed.

---

# Rate Limiting

FinCore includes a servlet-based rate limiting filter.

The filter tracks requests by client IP and rejects requests exceeding the configured limit.

Example response:

```http
429 Too Many Requests
```

Current implementation is intended as a lightweight application-level protection mechanism for the development system.

For production deployment, this would be replaced or supplemented by a distributed rate limiter such as Redis or an API gateway.
---
# Performance Benchmarks

FinCore was load-tested using **Grafana k6** against the authenticated payment-processing API on a local development environment.

The benchmark used **25 dedicated authenticated load-test users and wallets**, with each request authenticated using the corresponding user's JWT. Concurrent wallet-to-wallet transfers exercised the transaction-processing path, including idempotency validation, pessimistic wallet locking, balance updates, payment persistence, and ledger creation.

## 1000-VU Payment Load Test

| Metric | Result |
|---|---:|
| Maximum Concurrent VUs | **1,000** |
| Authenticated Load-Test Users | **25** |
| Total Requests | **50,682** |
| Successful Requests | **50,682 (100%)** |
| Failed Requests | **0 (0%)** |
| Throughput | **281.56 req/s** |
| Average Latency | **1.74 s** |
| Median Latency | **1.26 s** |
| P90 Latency | **3.83 s** |
| P95 Latency | **5.59 s** |
| Maximum Latency | **16.88 s** |

### Load Profile

The benchmark progressively increased concurrency:

```text
100 VUs
   ↓
350 VUs
   ↓
700 VUs
   ↓
1000 VUs
   ↓
1000 VUs sustained
   ↓
0 VUs
---

## Performance Observations

The benchmark demonstrates an important scaling characteristic.

At lower concurrency:

```text
100 VUs → 158.6 req/s
300 VUs → 189.2 req/s
```

Increasing concurrency beyond this point produced significantly higher latency without a proportional throughput increase:

```text
500 VUs → 188.4 req/s
```

The staged 1000-VU benchmark reached approximately:

```text
201.2 req/s
```

with zero HTTP failures.

This indicates that the local environment reaches a throughput saturation point as concurrency increases, while additional concurrent requests primarily increase transaction latency.

The payment workload also exercises pessimistic wallet locking, database transactions, wallet balance updates, payment persistence, and ledger creation.

> These results represent a local development benchmark and should not be interpreted as production capacity.

---

# Load Testing

The repository contains the k6 load-testing script:

```text
payment-load.js
```

Run the benchmark with:

```powershell
k6 run .\payment-load.js
```

Example fixed-concurrency tests:

```powershell
k6 run --vus 100 --duration 30s .\payment-load.js
```

```powershell
k6 run --vus 300 --duration 30s .\payment-load.js
```

```powershell
k6 run --vus 500 --duration 30s .\payment-load.js
```

The benchmark validates:

* HTTP success rate
* Payment creation
* Payment status
* Concurrent transaction handling
* Response latency
* Throughput
* Database-backed payment processing

---

# Technology Stack

## Backend

* Java 21
* Spring Boot 4.0.4
* Spring Security
* Spring Data JPA
* Hibernate ORM
* Jakarta Persistence
* Maven
* JWT
* BCrypt

## Database

* PostgreSQL 18
* PostgreSQL ENUM types
* Foreign keys
* Unique constraints
* Database transactions
* Pessimistic row-level locking

## Testing & Performance

* Grafana k6
* Postman
* Spring Boot Test

## Development

* Git
* GitHub
* PowerShell

---

# Project Structure

```text
secure-finance-backend/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── fincore/
│   │   │           └── backend/
│   │   │               │
│   │   │               ├── controller/
│   │   │               │   ├── AuthController.java
│   │   │               │   ├── WalletController.java
│   │   │               │   ├── PaymentController.java
│   │   │               │   └── ...
│   │   │               │
│   │   │               ├── dto/
│   │   │               │
│   │   │               ├── entity/
│   │   │               │   ├── User.java
│   │   │               │   ├── Role.java
│   │   │               │   ├── Wallet.java
│   │   │               │   ├── Payment.java
│   │   │               │   └── LedgerEntry.java
│   │   │               │
│   │   │               ├── enums/
│   │   │               │
│   │   │               ├── repository/
│   │   │               │   ├── UserRepository.java
│   │   │               │   ├── RoleRepository.java
│   │   │               │   ├── WalletRepository.java
│   │   │               │   ├── PaymentRepository.java
│   │   │               │   └── LedgerEntryRepository.java
│   │   │               │
│   │   │               ├── service/
│   │   │               │   ├── AuthService.java
│   │   │               │   ├── WalletService.java
│   │   │               │   └── PaymentService.java
│   │   │               │
│   │   │               ├── security/
│   │   │               │   ├── config/
│   │   │               │   ├── filter/
│   │   │               │   └── jwt/
│   │   │               │
│   │   │               └── ratelimit/
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│
├── postman/
├── payment-load.js
├── pom.xml
├── README.md
└── .gitignore
```

---

# Data Integrity

FinCore treats money movement as a transactional operation.

A successful payment must maintain:

```text
Sender Balance - Amount
+
Receiver Balance + Amount
```

The payment and its ledger entries are persisted within the same transaction.

If an exception occurs before commit, the transaction is rolled back.

This prevents partially completed transfers such as:

```text
Sender debited
      ↓
Application failure
      ↓
Receiver not credited
```

Instead:

```text
BEGIN TRANSACTION
      ↓
Debit
      ↓
Credit
      ↓
Payment
      ↓
Ledger
      ↓
COMMIT
```

or:

```text
BEGIN TRANSACTION
      ↓
Failure
      ↓
ROLLBACK
```

---

# Database Consistency Mechanisms

FinCore uses multiple layers of consistency protection.

### Unique Constraints

Used for:

```text
username
email
phone
wallet_number
payment_reference
idempotency_key
```

### Foreign Keys

Used to maintain relationships between:

```text
users
wallets
payments
ledger_entries
roles
```

### Transactions

Payment processing uses:

```java
@Transactional
```

### Pessimistic Locking

Wallets participating in a payment are locked before balance modification.

### Validation

Payments validate:

* Positive amount
* Valid currency
* Active sender wallet
* Active receiver wallet
* Sufficient balance
* Different sender and receiver wallets
* Valid idempotency key

---

# Example Transaction

Initial state:

```text
Ravi Wallet
Balance: ₹30,000

Ramana Wallet
Balance: ₹5,000
```

Payment:

```text
₹1,000
```

After successful processing:

```text
Ravi Wallet
Balance: ₹29,000

Ramana Wallet
Balance: ₹6,000
```

Ledger:

```text
Ravi Wallet
DEBIT
₹1,000
Balance After: ₹29,000

Ramana Wallet
CREDIT
₹1,000
Balance After: ₹6,000
```

---

# Running the Project

## Prerequisites

Install:

```text
Java 21
PostgreSQL 18
Maven
k6 (optional, for load testing)
```

Verify Java:

```powershell
java -version
```

Verify PostgreSQL:

```powershell
psql --version
```

Verify k6:

```powershell
k6 version
```

---

## Database

Create the PostgreSQL database:

```sql
CREATE DATABASE fincore;
```

Configure the required environment variables.

Example:

```text
SERVER_PORT=8080
APP_NAME=fincore

DB_URL=jdbc:postgresql://localhost:5432/fincore
DB_USERNAME=postgres
DB_PASSWORD=<your-password>

JWT_SECRET=<long-random-secret>
```

Do not commit `.env` or production credentials to GitHub.

---

## Start the Backend

Using Maven Wrapper:

```powershell
.\mvnw clean compile
```

Run the application:

```powershell
.\mvnw spring-boot:run
```

The backend starts on:

```text
http://localhost:8080
```

---

# Basic Workflow

The application can be tested in the following order.

```text
1. Register user
       ↓
2. Login
       ↓
3. Receive JWT
       ↓
4. Create wallet
       ↓
5. Fund wallet
       ↓
6. Create second wallet
       ↓
7. Fund second wallet
       ↓
8. Transfer money
       ↓
9. Verify payment
       ↓
10. Verify ledger
```

---

# Example API Workflow

### 1. Register

```http
POST /auth/register
```

### 2. Login

```http
POST /auth/login
```

Copy the returned JWT.

### 3. Create wallets

```http
POST /wallet/user/{userId}
Authorization: Bearer <JWT>
```

### 4. Fund a wallet

```http
POST /wallet/{walletId}/fund?amount=30000
Authorization: Bearer <JWT>
```

### 5. Transfer money

```http
POST /payments
Authorization: Bearer <JWT>
```

```json
{
    "senderWalletId": 4,
    "receiverWalletId": 3,
    "amount": 1000.00,
    "currency": "INR",
    "idempotencyKey": "payment-ravi-ramana-001",
    "description": "Test payment"
}
```

### 6. Retrieve payment

```http
GET /payments/{paymentId}
Authorization: Bearer <JWT>
```

---

# Error Handling

The application exposes structured error responses.

Example:

```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "Insufficient wallet balance",
    "path": "/payments",
    "timestamp": "..."
}
```

Common validation failures include:

```text
Invalid username or password
User account is not active
Wallet not found
User already has a wallet
Wallet is not active
Insufficient wallet balance
Currency mismatch
Sender and receiver wallets must be different
Payment amount must be greater than zero
Idempotency key is required
Too many requests
```

---

# Future Enhancements

Potential production-oriented improvements include:

* Redis-based distributed rate limiting
* Redis caching
* Kafka event streaming
* Outbox pattern
* Distributed tracing
* Prometheus metrics
* Grafana dashboards
* OpenTelemetry
* API gateway
* Refresh tokens
* Key rotation for JWT signing
* Transaction history pagination
* Database partitioning
* Read replicas
* Connection pool tuning
* Horizontal application scaling
* Docker deployment
* Kubernetes deployment
* CI/CD pipeline
* Integration test suite
* Testcontainers-based PostgreSQL testing
* Fraud detection
* Transaction limits
* Scheduled settlement workflows
* Multi-currency wallets
* Webhook/event notification system

---

# Engineering Focus

FinCore was built to explore backend engineering problems that appear in transactional financial systems rather than simply implementing CRUD APIs.

The project focuses on:

```text
Authentication
      +
Authorization
      +
Transactional Integrity
      +
Concurrency Control
      +
Idempotency
      +
Database Locking
      +
Ledger Design
      +
Error Handling
      +
Rate Limiting
      +
Load Testing
```

The primary engineering challenge is maintaining correct financial state while multiple clients concurrently attempt to modify the same resources.

---

# Author

**Ravi Sankar Manem**

GitHub:

https://github.com/ravimnm

---

# License

This project is intended for educational and engineering portfolio purposes.
