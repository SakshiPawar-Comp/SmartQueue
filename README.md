# SmartQueue — Enterprise Live Queue Management & Token Orchestration Platform

SmartQueue is a production-grade, multi-tenant digital token and live queue management platform engineered for high-concurrency environments (healthcare, banking, government, retail).

---

## 🏗️ System Architecture

SmartQueue is built with strict boundary segregation, deterministic data consistency, defense-in-depth security, and explainable advisory AI:

```text
React 18 / TypeScript / Vite Client (Tailwind CSS, i18n, Lucide)
   │               ▲
   │ REST (JSON)   │ WebSocket (STOMP / SockJS)
   ▼               │
Spring Boot 3.3.5 Backend (Java 17, Spring Security 6, JPA / Hibernate, HikariCP)
   │
   ├─► PostgreSQL 16 (Authoritative System of Record, ACID Transactions, Row-Level Pessimistic Locks)
   ├─► Redis 7 (Distributed caching, rate limiting, and pub/sub layer)
   └─► FastAPI AI Microservice (Python 3.11, Advisory ETA, Demand Forecasting, Anomaly Detection)
```

### Core Architecture Guarantees
1. **Authoritative State of Record:** `QueueService` + PostgreSQL are the single source of truth. Redis and WebSockets are downstream real-time conduits.
2. **Advisory AI Boundary:** The Python AI microservice provides predictions and recommendations via internal HMAC/API-key secured endpoints. If the AI service is unreachable or degraded, the backend automatically falls back to deterministic mathematical heuristics (`tokensAhead * defaultServiceTimeMinutes`).
3. **Strict Multi-Tenancy & RBAC:** Complete tenant isolation enforced via `TenantFilter`, `TenantContext` ThreadLocal, and scoped database queries. Active product roles: `CUSTOMER`, `STAFF`, `ORGANIZATION_ADMIN`, and `SUPER_ADMIN`.
4. **Deterministic Token Ordering:** Deterministic ordering governed by `priority` (VIP > PRIORITY > NORMAL), creation timestamp, and sequence numbering with row-level pessimistic locking (`PESSIMISTIC_WRITE`).

---

## 📦 Project Structure

```text
smartqueue/
├── backend/                  # Java 17 / Spring Boot 3.3.5 Backend API & WebSocket Server
│   ├── src/main/java/com/smartqueue/
│   │   ├── analytics/        # Time-series metrics, operator stats & CSV/JSON report export
│   │   ├── auth/             # JWT Authentication, refresh rotation, RBAC & user domain
│   │   ├── common/           # Security headers, tenant context, error handling, rate limiting
│   │   ├── notification/     # IN_APP, EMAIL, SMS, PUSH notification engine & dispatchers
│   │   ├── organization/     # Multi-tenant organizations & branches
│   │   ├── qr/               # QR Code generator & deep link routing
│   │   ├── queue/            # Single authoritative QueueEngine, tokens & counters
│   │   └── service/          # Departmental service catalog
│   ├── src/main/resources/
│   │   ├── db/migration/     # V1 to V8 Flyway SQL migrations
│   │   ├── application.yml   # Default/Docker configuration
│   │   ├── application-dev.yml  # Local H2 development profile
│   │   └── application-prod.yml # Production-hardened PostgreSQL & HikariCP profile
│   └── src/test/java/        # Comprehensive unit, integration, and audit test suites (137 tests)
├── frontend/                 # React 18 / TypeScript / Vite SPA
│   ├── src/
│   │   ├── app/              # Router, App root, and global styles
│   │   ├── components/       # Reusable UI primitives, modals, notifications, layouts
│   │   ├── features/         # Customer, Staff Counter, Admin Analytics & Super Admin modules
│   │   ├── lib/              # Fetch API client with HttpOnly refresh rotation, STOMP client
│   │   ├── i18n/             # Complete translations for English (en), Hindi (hi), Marathi (mr)
│   │   └── types/            # Strict TypeScript interfaces matching backend DTOs
├── ai-service/               # Python 3.11 / FastAPI Advisory AI Microservice
│   ├── app/                  # Statistical ETA prediction, seasonal forecasting, z-score anomaly detection
│   └── tests/                # Microservice unit tests (8 tests)
├── deploy/                   # Deployment-Ready Configurations & Manifests
│   └── k8s/                  # Kubernetes ConfigMap, Secret template, and Service/Deployment YAMLs
├── docs/                     # Comprehensive Architecture & API Documentation
│   └── architecture.md
├── docker-compose.yml        # Multi-container orchestration (PostgreSQL, Redis, Backend, Frontend, AI)
└── .env.example              # Environment variables template
```

---

## 🚀 Quick Start & Development

### Prerequisites
- Java 17 JDK
- Node.js 20+ & npm
- Python 3.11+
- Docker & Docker Compose (Optional for full stack containerization)

### 1. Backend (Dev Profile with In-Memory H2)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
- API Base URL: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:smartqueue_dev`, User: `sa`, Password: *empty*)
- Health Check: `http://localhost:8080/actuator/health`

### 2. Frontend
```bash
cd frontend
npm install
npm run dev
```
- Frontend UI: `http://localhost:5173`

### 3. AI Service
```bash
cd ai-service
python -m venv .venv
# On Windows: .venv\Scripts\activate | On Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```
- AI Service API: `http://localhost:8000`
- Health Check: `http://localhost:8000/health`

---

## 🐳 Docker Compose Deployment

Run the complete multi-service stack with a single command:

```bash
docker compose up --build -d
```

Services started:
| Service | Internal Port | External Port | Role |
| :--- | :--- | :--- | :--- |
| `postgres` | 5432 | 5432 | PostgreSQL 16 Database |
| `redis` | 6379 | 6379 | Redis 7 Caching & Rate Limiting |
| `backend` | 8080 | 8080 | Spring Boot API & WebSocket Broker |
| `frontend` | 80 | 3000 | Nginx serving React Production Build |
| `ai-service` | 8000 | 8000 | FastAPI AI Prediction Microservice |

---

## ☸️ Kubernetes Deployment Templates

Deployment-ready Kubernetes manifests are provided in `deploy/k8s/`:

```bash
# 1. Apply namespace, configmap, and secrets
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret-template.yaml

# 2. Deploy AI Service, Backend, and Frontend
kubectl apply -f deploy/k8s/ai-service-deployment.yaml
kubectl apply -f deploy/k8s/backend-deployment.yaml
kubectl apply -f deploy/k8s/frontend-deployment.yaml
```

---

## 🧪 Verification & Test Suites

The codebase includes full regression, security, concurrency, and end-to-end audit test suites:

### Backend Test Suite (137 Tests)
```bash
cd backend
mvn test
```
- **Phase 1:** Multi-Tenancy & User Authentication (9 tests)
- **Phase 2:** Authoritative QueueEngine & Token Lifecycle (9 tests)
- **Phase 3:** Customer Experience, Discovery & Deterministic ETA (8 tests)
- **Phase 4:** Multi-Counter Concurrency, Capacity & Staff Assignment (97 tests baseline)
- **Phase 5:** QR Codes, Deep Links, Notifications & Event Listeners (112 tests baseline)
- **Phase 6:** Analytics Aggregations, Daily/Weekly Reports & CSV Export (116 tests baseline)
- **Phase 7 Hardening & Audits:**
  - `Phase7SecurityAuditTest`: Token rotation, replay detection, logout revocation, RBAC & IDOR (7 tests)
  - `Phase7ConcurrencyAuditTest`: Multi-counter atomic next, concurrent join, capacity overflow (3 tests)
  - `Phase7ResilienceAuditTest`: AI fallback to deterministic calculation, notification durability (3 tests)
  - `Phase7EndToEndAuditTest`: Full customer & staff lifecycle integration (1 test)
  - **Result: 137 / 137 PASS (0 failures, 0 errors, 0 skipped)**

### Frontend Verification
```bash
cd frontend
npm run typecheck
npm run build
```
- **Result: PASS (0 TypeScript errors, production bundle compiled cleanly)**

### AI Microservice Test Suite (8 Tests)
```bash
cd ai-service
.venv\Scripts\python.exe -m unittest discover tests
```
- **Result: 8 / 8 PASS (OK)**

---

## 🛡️ Security Hardening

- **Defense-in-Depth HTTP Headers:** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0`, `Referrer-Policy: strict-origin-when-cross-origin`, configurable `HSTS`.
- **JWT Security:** Ephemeral short-lived access tokens (15m), secure HTTP-only refresh tokens with cryptographic rotation, SHA-256 token hashing, and explicit replay detection.
- **WebSocket Security:** Channel-level STOMP subscription authorization verifying JWT claims and tenant boundaries (`/topic/orgs/{orgId}/**`, `/topic/queues/{queueId}`, `/topic/users/{userId}/notifications`).
- **Database Connection Pooling:** HikariCP tuned for production with configurable `minimum-idle`, `maximum-pool-size`, connection timeouts, and leak detection thresholds.
- **Actuator Endpoint Protection:** Restricted in production to `/actuator/health` and `/actuator/info` only.
