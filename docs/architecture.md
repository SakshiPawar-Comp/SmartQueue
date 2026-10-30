# SmartQueue Architecture

## 1. System Architecture

SmartQueue uses a modular, multi-service architecture with PostgreSQL as the authoritative system of record.

```text
React/Vite clients
  | REST + WebSocket/STOMP
Spring Boot backend
  |-- Auth and RBAC
  |-- Tenant isolation
  |-- Organization, branch, service management
  |-- Deterministic queue engine
  |-- Staff and counter management
  |-- Notification orchestration
  |-- Analytics and reporting
  |-- Audit and tamper-evident ledger
  |-- AI gateway
  |
  | SQL transactions
PostgreSQL
  |
Redis for cache, coordination, rate limits, pub/sub

Spring Boot backend
  | validated request/response
FastAPI AI service
  | baseline first, ML later
Prediction and recommendation modules
```

Core rule: the backend owns all critical queue state transitions. AI may recommend, classify, predict, and explain, but it cannot directly mutate token, counter, priority, or queue state.

## 2. Recommended Folder Structure

```text
backend/
  src/main/java/com/smartqueue/
    SmartQueueApplication.java
    common/
      config/
      errors/
      observability/
      security/
      tenant/
    auth/
    organization/
    branch/
    servicecatalog/
    queue/
      api/
      application/
      domain/
      infrastructure/
    counter/
    notification/
    analytics/
    audit/
    ai/
  src/main/resources/
    application.yml
    db/migration/
  src/test/java/com/smartqueue/

frontend/
  src/
    app/
    components/
    features/
      public/
      customer/
      staff/
      admin/
      super-admin/
    lib/
    routes/
    styles/

ai-service/
  app/
    main.py
    api/
    core/
    models/
    services/
    providers/
    tests/

docs/
  architecture.md
```

## 3. Database ER Model

All tenant-owned tables include `organization_id` directly or through a mandatory parent relation. Service methods must also enforce tenant scope, so a missing query predicate cannot leak data across organizations.

```text
users
  id uuid pk
  email unique
  password_hash
  full_name
  phone
  preferred_language
  status
  created_at
  updated_at

roles
  id uuid pk
  code unique
  name

user_roles
  user_id fk users
  role_id fk roles
  organization_id fk organizations nullable for super admin

organizations
  id uuid pk
  name
  slug unique
  status
  created_at
  updated_at

branches
  id uuid pk
  organization_id fk organizations
  name
  code
  timezone
  address
  status
  unique(organization_id, code)

services
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  name
  code
  description
  avg_service_duration_seconds
  status
  unique(branch_id, code)

queues
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  service_id fk services
  name
  token_prefix
  status
  current_sequence
  version
  unique(branch_id, service_id)

counters
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  queue_id fk queues nullable
  name
  status
  assigned_staff_id fk staff nullable

staff
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  user_id fk users
  employee_code
  status
  unique(organization_id, employee_code)

tokens
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  queue_id fk queues
  service_id fk services
  customer_id fk users
  sequence_number bigint
  display_token
  priority integer
  status
  called_at
  service_started_at
  completed_at
  held_at
  hold_expires_at
  counter_id fk counters nullable
  staff_id fk staff nullable
  idempotency_key
  version
  created_at
  updated_at
  unique(queue_id, sequence_number)
  unique(queue_id, idempotency_key)

token_events
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  token_id fk tokens
  actor_user_id fk users nullable
  event_type
  previous_status
  new_status
  reason
  metadata_json
  correlation_id
  created_at

queue_configurations
  id uuid pk
  organization_id fk organizations
  queue_id fk queues
  max_size
  hold_duration_seconds
  hold_expiry_policy
  skip_policy
  resume_policy
  notification_thresholds_json
  operating_policy_json
  version

priority_rules
  id uuid pk
  organization_id fk organizations
  queue_id fk queues
  name
  priority_value
  rule_json
  requires_staff_approval
  status

notifications
  id uuid pk
  organization_id fk organizations
  user_id fk users
  token_id fk tokens nullable
  channel
  dedupe_key
  subject
  body
  status
  sent_at
  created_at
  unique(organization_id, dedupe_key)

feedback
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  service_id fk services
  token_id fk tokens
  customer_id fk users
  rating
  comment
  sentiment
  topics_json
  created_at

audit_logs
  id uuid pk
  organization_id fk organizations nullable
  branch_id fk branches nullable
  actor_user_id fk users nullable
  actor_role
  action
  entity_type
  entity_id
  previous_state_json
  new_state_json
  ip_address
  device_metadata_json
  correlation_id
  created_at

operating_hours
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  service_id fk services nullable
  day_of_week
  opens_at
  closes_at
  is_closed

ai_predictions
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  queue_id fk queues nullable
  prediction_type
  input_hash
  output_json
  confidence
  model_name
  created_at

analytics_snapshots
  id uuid pk
  organization_id fk organizations
  branch_id fk branches
  queue_id fk queues nullable
  snapshot_type
  metrics_json
  period_start
  period_end
  created_at
```

Important indexes:

- `tokens(organization_id, branch_id, queue_id, status, created_at)`
- `tokens(queue_id, priority desc, created_at, sequence_number)`
- `token_events(token_id, created_at)`
- `audit_logs(organization_id, created_at)`
- `notifications(user_id, status, created_at)`
- `analytics_snapshots(organization_id, branch_id, period_start, period_end)`

## 4. API Specification

All APIs return a consistent envelope:

```json
{
  "data": {},
  "error": null,
  "correlationId": "req_..."
}
```

Errors use:

```json
{
  "data": null,
  "error": {
    "code": "QUEUE_CLOSED",
    "message": "The selected queue is closed.",
    "details": {}
  },
  "correlationId": "req_..."
}
```

Phase 1 foundation:

- `GET /api/platform/health`
- `GET /actuator/health`
- `GET /swagger-ui.html`

Planned public and auth:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/organizations`
- `GET /api/organizations/{organizationId}/branches`
- `GET /api/branches/{branchId}/services`
- `GET /api/qr/{signedReference}`

Customer:

- `POST /api/queues/{queueId}/tokens`
- `GET /api/tokens/{tokenId}`
- `GET /api/customers/me/tokens`
- `POST /api/tokens/{tokenId}/hold`
- `POST /api/tokens/{tokenId}/resume`
- `POST /api/tokens/{tokenId}/skip`
- `POST /api/tokens/{tokenId}/cancel`
- `GET /api/customers/me/notifications`
- `POST /api/tokens/{tokenId}/feedback`

Staff:

- `GET /api/staff/counters`
- `POST /api/staff/counters/{counterId}/open`
- `POST /api/staff/counters/{counterId}/close`
- `POST /api/staff/queues/{queueId}/next`
- `POST /api/tokens/{tokenId}/recall`
- `POST /api/tokens/{tokenId}/start`
- `POST /api/tokens/{tokenId}/complete`
- `POST /api/tokens/{tokenId}/transfer`

Admin:

- `POST /api/admin/organizations`
- `POST /api/admin/branches`
- `POST /api/admin/services`
- `POST /api/admin/queues`
- `POST /api/admin/counters`
- `POST /api/admin/staff`
- `GET /api/admin/analytics`
- `GET /api/admin/reports`
- `GET /api/admin/audit-logs`
- `GET /api/admin/ai-insights`
- `PUT /api/admin/queue-configurations/{configurationId}`
- `PUT /api/admin/priority-rules/{ruleId}`

Idempotent operations accept `Idempotency-Key` where retries could duplicate work.

## 5. WebSocket Event Model

Transport: STOMP over WebSocket at `/ws`.

Topics:

- `/topic/organizations/{organizationId}/branches/{branchId}/queues/{queueId}`
- `/topic/organizations/{organizationId}/branches/{branchId}/admin`
- `/topic/staff/{staffId}`
- `/user/queue/notifications`
- `/topic/display/branches/{branchId}`

Event envelope:

```json
{
  "eventId": "uuid",
  "eventType": "TOKEN_UPDATED",
  "organizationId": "uuid",
  "branchId": "uuid",
  "queueId": "uuid",
  "entityType": "TOKEN",
  "entityId": "uuid",
  "occurredAt": "2026-08-11T10:00:00Z",
  "correlationId": "req_...",
  "payload": {}
}
```

Supported events:

- `TOKEN_CREATED`
- `TOKEN_UPDATED`
- `TOKEN_CALLED`
- `TOKEN_HELD`
- `TOKEN_RESUMED`
- `TOKEN_SKIPPED`
- `TOKEN_CANCELLED`
- `TOKEN_STARTED`
- `TOKEN_COMPLETED`
- `COUNTER_OPENED`
- `COUNTER_CLOSED`
- `QUEUE_UPDATED`
- `ETA_UPDATED`
- `NOTIFICATION_CREATED`
- `AI_RECOMMENDATION_CREATED`
- `AUDIT_EVENT_RECORDED`

Authorization is enforced before subscription and before publishing user-targeted events.

## 6. Queue Engine Design

The queue engine will live under `backend/src/main/java/com/smartqueue/queue`. It will expose application services rather than controller-level business logic.

Deterministic ordering policy for the initial implementation:

1. Higher priority first.
2. Earlier `created_at` first.
3. Lower `sequence_number` first.
4. Lower token UUID as final deterministic tie-breaker.

Concurrency controls:

- Database transaction around token creation and state transitions.
- Row-level locking for queue sequence allocation and call-next selection.
- Unique constraints for `(queue_id, sequence_number)` and idempotency keys.
- Optimistic locking with `version` on mutable aggregate roots.
- Token state machine validation before every transition.

State machine:

- `WAITING -> CALLED -> SERVING -> COMPLETED`
- `WAITING -> HELD -> WAITING`
- `HELD -> EXPIRED`
- `WAITING -> SKIPPED`
- `WAITING -> CANCELLED`

Every transition writes a `token_events` row and an audit event when the action is administrative, policy-sensitive, or dispute-sensitive.

## 7. Development Roadmap

1. Phase 1: Project setup and architecture.
2. Phase 2: Authentication and RBAC.
3. Phase 3: Organization, branch, and service management.
4. Phase 4: Queue and token engine.
5. Phase 5: Staff and counter management.
6. Phase 6: Customer dashboard.
7. Phase 7: Real-time WebSockets.
8. Phase 8: Hold, resume, skip, and priority handling.
9. Phase 9: Notifications.
10. Phase 10: Admin analytics.
11. Phase 11: AI ETA.
12. Phase 12: AI forecasting and bottleneck detection.
13. Phase 13: Advanced AI.
14. Phase 14: Audit logging.
15. Phase 15: Optional tamper-evident audit ledger.
16. Phase 16: Testing and security hardening.
17. Phase 17: Docker and deployment.
18. Phase 18: Final polish.

Phase gates:

- The application starts.
- Tests for the current phase pass.
- Known defects are fixed before moving to the next phase.
- New buttons and screens introduced in a phase have real behavior for that phase.
