# 🚀 Secure Finance Backend API

A production-style backend system for managing financial transactions with **JWT authentication**, **Role-Based Access Control (RBAC)**, **pagination**, and **MongoDB aggregation-based analytics**.

---

## 🧠 System Overview

This project implements a secure backend service for managing user transactions with:

* 🔐 JWT-based authentication
* 🛡️ Role-Based Access Control (RBAC)
* 💰 Transaction CRUD operations
* 📊 Dashboard analytics (MongoDB Aggregation)
* 📄 Pagination & filtering
* ⚠️ Structured error handling
* 🚦 Basic rate limiting

---

## 🏗️ Architecture

```
controller/
  AuthController
  TransactionController
  DashboardController
  AdminController

service/
  AuthService
  TransactionService

repository/
  UserRepository
  TransactionRepository
  RoleRepository

entity/
  User
  Transaction
  Role

security/
  JwtFilter
  JwtUtil
  SecurityConfig
  CustomUserDetailsService

exception/
  GlobalExceptionHandler
  ErrorResponse

dto/
  AuthRequest
  AuthResponse
  PagedResponse
```

---

## 🔐 Roles & Permissions

| Role         | Access                |
| ------------ | --------------------- |
| ROLE_ADMIN   | Full CRUD + Dashboard |
| ROLE_ANALYST | Read + Analytics      |
| ROLE_VIEWER  | Dashboard only        |

---

## 🔐 Authentication Flow

1. User registers → `/auth/register`
2. User logs in → `/auth/login`
3. JWT token returned
4. Token used in requests:

```
Authorization: Bearer <token>
```

---

## 📦 API Endpoints

### 🔑 Auth

* `POST /auth/register`
* `POST /auth/login`

---

### 💰 Transactions

* `POST /transactions` → Create (ADMIN)
* `GET /transactions` → Get all (Paginated)
* `PUT /transactions/{id}` → Update (Owner + ADMIN)
* `DELETE /transactions/{id}` → Delete (ADMIN)
* `GET /transactions/filter?type=income` → Filter
* `GET /transactions/trends` → Monthly trends

---

### 📊 Dashboard

* `GET /dashboard/summary`
* `GET /dashboard/category`

---

### 🔐 Admin

* `GET /admin/data`

---

## 📄 Pagination

Example:

```
GET /transactions?page=0&size=10
```

Response:

```
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 25,
  "totalPages": 3
}
```

---

## 📊 Analytics (MongoDB Aggregation)

Implemented using `MongoTemplate`:

* Total Income vs Expense
* Category-wise spending
* Monthly trends

---

## ⚠️ Error Handling

All errors follow a structured format:

```
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/transactions"
}
```

---

## 🚦 Rate Limiting

* Basic IP-based rate limiting using filter
* Prevents excessive requests (429 response)

---

## ⚙️ Tech Stack

* Java 17+
* Spring Boot
* Spring Security
* MongoDB
* JWT (jjwt)

---

## ▶️ How to Run

### 1. Clone repo

```
git clone <your-repo-link>
cd secure-access-control-service
```

### 2. Configure MongoDB

Update in `application.properties`:

```
spring.data.mongodb.uri=your_mongodb_uri
jwt.secret=your_secret_key
```

---

### 3. Run application

```
mvn spring-boot:run
```

Server runs on:

```
http://localhost:8080
```

---

## 🧪 API Testing

Use Postman collection:

1. Import collection
2. Set environment:

```
baseUrl = http://localhost:8080
token = <auto-set>
```

---

## 🧠 Key Design Decisions

* Used **JWT + Spring Security filters** for stateless authentication
* Implemented **RBAC using roles + authorities**
* Used **MongoDB aggregation** for efficient analytics
* Added **pagination** for scalability
* Centralized error handling using `@RestControllerAdvice`

---

## ⚠️ Limitations

* Rate limiter does not reset counts (memory-based)
* No refresh token mechanism
* No frontend (API-only system)

---

## 📌 Future Improvements

* Redis-based rate limiting
* Refresh tokens
* Swagger/OpenAPI documentation
* Advanced filtering & search

---

## 👨‍💻 Author

Ravi Sankar Manem

###GitHub: https://github.com/ravimnm
---

## ⭐ Summary

This project demonstrates:

* Backend system design
* Security implementation (JWT + RBAC)
* Scalable API design (pagination)
* Data analytics using MongoDB aggregation
* Production-style error handling

---
## Audit & Security Integration

This application integrates with the following security components:

| Component | Purpose |
|-----------|---------|
| [SMTAP](https://github.com/ravimnm/audit-compliance-platform) | Centralized audit logging, integrity verification, and compliance backend |
| [JRSA](https://github.com/ravimnm/java-runtime-security-agent) | Runtime JVM security monitoring and telemetry generation |
