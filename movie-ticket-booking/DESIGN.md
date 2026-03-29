# Online Movie Ticket Booking Platform — Solution Design

---

## Table of Contents

1. [Solution Overview](#1-solution-overview)
2. [Functional Scope](#2-functional-scope)
3. [System Architecture](#3-system-architecture)
4. [Data Model](#4-data-model)
5. [API Contract](#5-api-contract)
6. [Concurrency & Transactional Design](#6-concurrency--transactional-design)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [Platform Provisioning & Sizing](#8-platform-provisioning--sizing)
9. [Monitoring & Observability](#9-monitoring--observability)
10. [Security](#10-security)
11. [Assumptions & Skipped Areas](#11-assumptions--skipped-areas)

---

## 1. Solution Overview

XYZ's platform serves two personas:

| Persona | Capability |
|---------|-----------|
| **Theatre Partner (B2B)** | Onboard theatres, manage shows, configure seat inventory |
| **End Customer (B2C)** | Discover movies, browse shows by city/date, book & pay for tickets |

**Technology choices:**

| Layer | Choice | Reason |
|-------|--------|--------|
| Language | Java 17 | LTS, virtual threads (Project Loom), strong ecosystem |
| Framework | Spring Boot 3.2 | Mature, production-tested, rich integrations |
| Database | PostgreSQL 16 | ACID, row-level locking, JSONB for metadata |
| Cache / Seat lock | Redis 7 (Cluster) | Sub-millisecond ops, SETNX for atomic seat holds |
| Messaging | Apache Kafka | Event streaming, decoupled notifications, replay |
| API Gateway | AWS API Gateway / Kong | Rate limiting, auth, routing |
| Container | Docker + Kubernetes (EKS) | Horizontal scaling, rolling deploys |
| CI/CD | GitHub Actions → ArgoCD | GitOps, environment promotion |
| Observability | Prometheus + Grafana + ELK | Metrics, dashboards, log aggregation |

---

## 2. Functional Scope

### Implemented — Read Scenario

**Browse theatres running a movie in a city on a date**

- Endpoint: `GET /api/v1/shows`
- Input: `movieId`, `city`, `date`, optional `screenType`, `language`
- Output: Paginated list of theatres, each with scheduled shows and real-time seat availability
- Caching: Redis (3-minute TTL per movie+city+date key)

### Implemented — Write Scenario

**Book tickets by selecting theatre, show, and seats**

- Endpoint: `POST /api/v1/bookings`
- Idempotent via client-supplied `idempotencyKey`
- Two-phase: seat reservation (`AWAITING_PAYMENT`) → payment confirmation (`CONFIRMED`)
- Concurrent-safe: Redis SETNX lock + PostgreSQL pessimistic row lock

### Platform Offers (both implemented via Strategy pattern)

| Offer | Rule |
|-------|------|
| `AFTERNOON_20` | 20% off all tickets in afternoon shows (12:00–17:00) |
| `THIRD_TICKET_50` | 50% off the 3rd ticket in any booking |

Best applicable discount per ticket wins (discounts do not stack).

---

## 3. System Architecture

```
                         ┌─────────────────────────────────────────────┐
                         │              Clients                         │
                         │   B2C Web/Mobile App   B2B Partner Portal    │
                         └──────────────────┬──────────────────────────┘
                                            │ HTTPS
                                ┌───────────▼────────────┐
                                │     API Gateway         │
                                │  (Rate limit / Auth /   │
                                │   WAF / TLS termination)│
                                └───────────┬────────────┘
                                            │
                   ┌────────────────────────┼──────────────────────────┐
                   │                        │                          │
         ┌─────────▼──────────┐  ┌──────────▼───────────┐  ┌──────────▼──────────┐
         │  Movie & Show       │  │  Booking Service      │  │  Theatre Mgmt       │
         │  Service (Read)     │  │  (Write)              │  │  Service (B2B)      │
         │  Spring Boot        │  │  Spring Boot          │  │  Spring Boot        │
         └──────┬──────────────┘  └────┬──────────────────┘  └─────────┬───────────┘
                │                      │                               │
         ┌──────▼──────────────────────▼───────────────────────────────▼────────┐
         │                         PostgreSQL (RDS Multi-AZ)                     │
         │  Tables: movie, show, theatre, screen, seat, seat_inventory, booking  │
         └─────────────────────────────────────────────────────────────────────-─┘
                │                      │
         ┌──────▼──────┐       ┌────────▼──────────┐
         │ Redis Cluster│       │  Apache Kafka      │
         │ - Show cache │       │  - booking.*       │
         │ - Seat locks │       │  - notification.*  │
         └─────────────┘       └────────────────────┘
                                        │
                         ┌──────────────▼─────────────────┐
                         │  Notification Service           │
                         │  (email / SMS / push)           │
                         └────────────────────────────────-┘
```

### Service Decomposition

```
movie-ticket-booking/               ← This repo (monolith for now, decomposable)
  ├── Movie & Show service          ← Read: browse/search shows
  ├── Booking service               ← Write: seat reservation, payment lifecycle
  ├── Theatre management service    ← B2B: show CRUD, seat inventory management
  └── Pricing engine                ← Strategy pattern, offer evaluation
```

Future microservice splits: `NotificationService`, `PaymentService`, `AnalyticsService` — event-driven via Kafka.

---

## 4. Data Model

### Entity Relationship (key tables)

```
theatre_partner ──< theatre ──< screen ──< seat
                                   │
                                   └──< show ──< seat_inventory
                                         │              │
                                    movie              booking ──< booking_item
                                                          │
                                                       customer
```

### Key Design Decisions

| Decision | Reasoning |
|----------|-----------|
| `seat_inventory` is materialised per show | Avoids runtime joins for availability; direct UPDATE for booking |
| `version` column on `seat_inventory` | Optimistic lock fallback for read-heavy concurrent scenarios |
| `idempotency_key` unique constraint on `booking` | Safe retries across network failures |
| `booking_audit` append-only | Compliance trail; never update, only insert |
| `offer` table | Externalises discount rules; new offers = new rows, no code change |

---

## 5. API Contract

### Read: Browse Shows

```
GET /api/v1/shows
  ?movieId={uuid}
  &city=Bangalore
  &date=2026-03-29
  [&screenType=IMAX]
  [&language=Hindi]
  [&page=0&size=20]

200 OK
{
  "success": true,
  "data": {
    "content": [
      {
        "theatreId": "...",
        "theatreName": "PVR Forum Mall",
        "address": "Whitefield, Bangalore",
        "city": "Bangalore",
        "latitude": 12.9698,
        "longitude": 77.7499,
        "shows": [
          {
            "showId": "...",
            "startTime": "14:30",
            "endTime": "17:00",
            "slot": "AFTERNOON",
            "screenName": "Screen 2",
            "screenType": "3D",
            "basePrice": 300.00,
            "availableSeats": 87,
            "totalSeats": 120
          }
        ]
      }
    ],
    "totalElements": 5,
    "totalPages": 1
  }
}
```

### Write: Initiate Booking

```
POST /api/v1/bookings
Authorization: Bearer {jwt}
Content-Type: application/json

{
  "showId": "a1b2c3d4-...",
  "customerId": "f1e2d3c4-...",
  "seatInventoryIds": ["s1...", "s2...", "s3..."],
  "idempotencyKey": "customer-session-uuid-abc123"
}

201 Created
{
  "success": true,
  "data": {
    "bookingId": "...",
    "bookingRef": "BK20260329000042",
    "status": "AWAITING_PAYMENT",
    "seats": [
      { "seatInventoryId": "...", "rowLabel": "D", "seatNumber": 7,
        "category": "PREMIUM", "price": 300.00, "status": "LOCKED" }
    ],
    "totalAmount": 900.00,
    "discountAmount": 150.00,
    "finalAmount": 750.00,
    "expiresAt": "2026-03-29 15:42:00",
    "message": "Seats reserved for 10 minutes. Please complete payment."
  }
}
```

### Error Response

```
409 Conflict
{
  "success": false,
  "message": "One or more selected seats are currently held by another booking.",
  "error": {
    "code": "SEAT_UNAVAILABLE",
    "detail": "Seats [D7, D8] are locked. Please select different seats."
  }
}
```

---

## 6. Concurrency & Transactional Design

### The Double-Booking Problem

Naive approach: check availability → book. Race condition if two customers check simultaneously.

### Solution: Two-Layer Locking

```
Customer A requests seats [D7, D8]
Customer B requests seats [D7, D9]

Layer 1 — Redis SETNX (fast, ~1ms):
  A: SET seat_lock:D7 "idem-A" NX PX 600000  → OK
  A: SET seat_lock:D8 "idem-A" NX PX 600000  → OK
  B: SET seat_lock:D7 "idem-B" NX PX 600000  → FAIL (key exists)
     → B gets immediate 409, no DB load

Layer 2 — PostgreSQL SELECT FOR UPDATE (durability):
  A: SELECT * FROM seat_inventory WHERE id IN (...) AND status='AVAILABLE' FOR UPDATE
     → rows locked; UPDATE status='LOCKED'
     → COMMIT

  (B would fail here too even without Redis, but Redis prevents the DB round-trip)
```

### Why Both Layers?

| Scenario | Redis only | DB only | Both |
|----------|-----------|---------|------|
| Redis restart | ❌ lock lost | ✅ | ✅ |
| DB deadlock under load | ✅ | ❌ | ✅ |
| Cross-AZ network partition | ❌ | ✅ | ✅ |

### Booking State Machine

```
PENDING
  └─► AWAITING_PAYMENT  (seats locked in Redis + DB)
        ├─► CONFIRMED    (payment success; seats → BOOKED; Redis keys released)
        ├─► EXPIRED      (TTL elapsed; seats released; scheduled job cleans up)
        └─► CANCELLED    (customer / admin action; seats released)
```

### Expiry Job (Scheduled)

```java
// Runs every 5 minutes to clean up expired AWAITING_PAYMENT bookings
@Scheduled(fixedDelay = 5 * 60 * 1000)
void releaseExpiredBookings() {
    bookingRepository.findExpiredBookings(BookingStatus.AWAITING_PAYMENT, LocalDateTime.now())
        .forEach(b -> bookingService.cancelBooking(b.getBookingRef(), SYSTEM_USER));
}
```

### Idempotency

Client generates a UUID `idempotencyKey` per booking attempt. On server side:
- First request → create booking, persist key
- Duplicate request → `SELECT` on unique index returns existing row → return same response
- Safe across client retries and network failures

---

## 7. Non-Functional Requirements

### Scalability — Targeting 99.99% Availability

| Requirement | Design Decision |
|-------------|-----------------|
| Handle 100k concurrent users | Kubernetes HPA; pods scale on CPU + custom RPS metric |
| Read-heavy (browse shows) | Redis cache with 3-min TTL; read replicas in PostgreSQL |
| Write spikes (show release) | Kafka buffers booking events; seat locks in Redis prevent DB saturation |
| Multi-city, multi-country | City/country as first-class model attribute; no schema partitioning needed at V1 |
| Database scaling | PgBouncer connection pooling; read replicas via RDS read replica; partitioning `seat_inventory` by `show_date` for archival |

### 99.99% Availability Breakdown

```
Component            Availability  Mitigation
──────────────────── ───────────── ─────────────────────────────────────
RDS Multi-AZ          99.95%        Auto-failover < 30s
Redis Cluster (3 AZ)  99.99%        Sentinel + read replicas
EKS (3 AZ, 6 pods)    99.99%+       Graceful termination, PodDisruptionBudget
Kafka (3 brokers)     99.95%        ISR=2, min.insync.replicas=2
API Gateway (managed) 99.99%        AWS SLA
──────────────────── ───────────── ─────────────────────────────────────
Combined (SLA chain)  ~99.88%       Achieved via Circuit Breakers + fallbacks
Target with failover  99.99%        Multi-region active-passive for critical paths
```

### Theatre Integration (B2B)

| Theatre Type | Integration Pattern |
|--------------|---------------------|
| New theatre, no IT system | Direct REST API + Partner Portal (web app) |
| Theatre with existing PMS | Webhook push (theatre pushes show data) or scheduled pull via adapter |
| Legacy SOAP-based systems | Integration layer with SOAP-to-REST adapter (Spring WS / Apache CXF) |

**Approach:** Published an Open API spec for the Theatre Management Service. Partners implement a lightweight adapter SDK or use the hosted portal. Event-driven onboarding: `TheatreOnboardedEvent` triggers seat template generation.

### Localisation

- `language` field on `Movie` entity; UI filters by language
- Show times stored in UTC, displayed in theatre's local timezone (stored as `ZoneId` on `Theatre`)
- Multi-currency: `price` stored in ISO currency code + amount; conversion at display layer
- i18n strings externalised via Spring's `MessageSource`

### Payment Gateway Integration

```
BookingService                PaymentGateway (Razorpay/Stripe)
     │                               │
     │── POST /bookings ─────────────┤
     │◄─ {bookingRef, amount} ───────┤
     │                               │
     │─────────── redirect to PG ────►│
     │                               │── customer pays
     │◄────────── webhook callback ──│
     │   {paymentRef, status}        │
     │── POST /bookings/{ref}/confirm ┤
     │                               │
```

- Circuit breaker (Resilience4j) around payment gateway calls: 5-second timeout, 3-attempt retry with exponential backoff
- Idempotency key forwarded to PG to prevent duplicate charges
- Webhook signature verification (HMAC-SHA256)

### Monetisation

| Revenue Stream | Mechanism |
|---------------|-----------|
| Convenience fee | Fixed + percentage per booking (configurable per city) |
| Premium listing | Theatres pay for featured placement in search results |
| Advertising | Banner placements on search result pages |
| Ancillary | F&B pre-orders, parking booking upsells |
| Data insights | Anonymised box-office analytics sold to studios (opt-in) |

### OWASP Top 10 Mitigations

| Threat | Mitigation |
|--------|-----------|
| Injection (SQL) | JPA parameterised queries; no raw SQL concatenation |
| Broken Auth | JWT (short-lived 15-min access + refresh token rotation) |
| Sensitive Data Exposure | TLS everywhere; PII encrypted at rest (AES-256 in RDS) |
| IDOR | Ownership checks: `customerId` extracted from JWT, not request body |
| Security Misconfiguration | Helm chart hardened; no `root` containers; network policies |
| XSS | JSON API only; CSP headers via API Gateway |
| CSRF | Stateless JWT; SameSite=Strict cookies for web |
| Mass Assignment | Explicit DTO-to-entity mapping; no direct entity binding |
| Rate Limiting (DoS) | Bucket4j per customer + per IP at API Gateway |
| Logging sensitive data | MDC for traceId; PII masked in log patterns |

### Compliance

- **PCI-DSS**: Card data never touches our servers; full redirect to certified PG
- **GDPR / DPDP (India)**: Customer data deletion API, consent management, data residency by region
- **CERT-In**: Incident reporting within 6 hours; log retention 180 days

---

## 8. Platform Provisioning & Sizing

### Cloud Architecture (AWS)

```
Region: ap-south-1 (Mumbai)   [Primary]
Region: ap-southeast-1 (Singapore) [DR / Multi-region read]

ap-south-1:
  VPC (10.0.0.0/16)
  ├── Public Subnets (3 AZs)
  │   └── ALB → API Gateway → EKS Ingress
  ├── Private App Subnets (3 AZs)
  │   └── EKS node groups (spot + on-demand mix)
  └── Private Data Subnets (3 AZs)
      ├── RDS Multi-AZ (PostgreSQL)
      ├── ElastiCache (Redis Cluster Mode, 3 shards × 1 replica)
      └── Amazon MSK (Kafka, 3 brokers)
```

### Sizing Estimate (V1, 50 cities)

| Component | Sizing | Instance |
|-----------|--------|----------|
| Booking Service | 6 pods × 2 vCPU / 4 GB | Auto-scale 3–15 |
| Movie/Show Service | 4 pods × 1 vCPU / 2 GB | Auto-scale 2–10 |
| Theatre Mgmt | 2 pods × 1 vCPU / 2 GB | Fixed (low traffic) |
| PostgreSQL (RDS) | db.r6g.xlarge (4 vCPU, 32 GB) | Multi-AZ |
| Redis | cache.r6g.large × 3 shards | Cluster mode |
| Kafka (MSK) | kafka.m5.large × 3 | MSK managed |
| EKS Nodes | m5.2xlarge × 9 (3 per AZ) | Mix spot/OD |

### Release Management

| Stage | Process |
|-------|---------|
| Dev → Staging | PR merge triggers GitHub Actions: build → test → push image |
| Staging → Prod | ArgoCD sync gate; manual approval for major releases |
| Rollout strategy | Canary via Argo Rollouts: 10% → 30% → 100% with automated rollback on error rate spike |
| Database migrations | Flyway; backward-compatible schema changes enforced (expand-contract pattern) |
| Feature flags | LaunchDarkly for gradual city rollouts and A/B testing offers |
| Geo rollout | City-by-city activation via feature flags; no separate deployments |

### Internationalisation

- `Theatre.zoneId` stores IANA timezone (e.g., `Asia/Kolkata`, `Asia/Dubai`)
- All DB timestamps in UTC; client receives UTC + `zoneId` and renders locally
- Currency: `ISO 4217` code per city configuration
- Phone number: E.164 format with country code
- New country launch checklist: RDS read replica in-region, compliance review, payment gateway local support

---

## 9. Monitoring & Observability

### Three Pillars

**Metrics (Prometheus + Grafana)**
- Booking throughput (bookings/sec)
- Seat lock acquisition success rate
- Payment gateway latency (P50/P95/P99)
- Cache hit rate for show search
- Pod CPU / memory, JVM heap

**Logs (ELK / OpenSearch)**
- Structured JSON logs; MDC injects `traceId`, `userId`, `bookingRef`
- Log levels: INFO in prod, DEBUG in staging; dynamic level change via Actuator
- Retention: 30 days hot, 180 days cold (S3 Glacier)

**Traces (OpenTelemetry → Jaeger / AWS X-Ray)**
- Distributed tracing across booking → payment → notification chains
- Auto-instrumented via Spring Boot OTEL starter

### Key KPIs

| KPI | Target |
|-----|--------|
| Booking success rate | > 99.5% |
| Seat lock contention rate | < 2% |
| P99 booking latency | < 500ms |
| Payment gateway timeout rate | < 0.1% |
| Show search cache hit rate | > 85% |
| Platform availability | 99.99% |
| Mean Time to Recovery (MTTR) | < 15 min |

### Alerting

- PagerDuty integration; severity tiers (P1/P2/P3)
- Alert: booking error rate > 1% for 2 consecutive minutes → page on-call
- Alert: Redis seat lock failure rate spike → auto-scale pod count

---

## 10. Security

### Authentication & Authorisation

```
Customer JWT Payload:
  { "sub": "customer-uuid", "roles": ["CUSTOMER"], "city": "Bangalore", "exp": ... }

Theatre Partner JWT:
  { "sub": "partner-uuid", "roles": ["THEATRE_PARTNER"], "partnerId": "...", "exp": ... }

Admin:
  { "sub": "admin-uuid", "roles": ["ADMIN"], "exp": ... }
```

- Tokens issued by Keycloak / AWS Cognito
- Access token TTL: 15 minutes; Refresh token TTL: 7 days (rotated on use)
- Booking API: customer can only act on their own `customerId` (extracted from JWT)

### Data Protection

- PII (name, email, phone) encrypted at column level using pgcrypto
- Payment card data: never stored; PG tokenisation (Razorpay token)
- Audit log: immutable, separate DB user with INSERT-only permission

---

## 11. Assumptions & Skipped Areas

### Implemented
- Read scenario: show search with theatre grouping, cache, pagination
- Write scenario: seat booking with Redis+DB locking, pricing, idempotency, Kafka events
- Platform offers: afternoon discount, 3rd ticket discount (Strategy pattern)
- Unit tests for pricing logic and booking service

### Skipped / Out of Scope (noted)
- **UI layer**: Not required per spec
- **Payment gateway webhook handler**: Stub exists; full Razorpay/Stripe integration not wired
- **Theatre Management Service**: Entity model and endpoints designed; CRUD implementation skipped
- **Bulk booking**: Architecture supports it (list of seatIds); bulk API not added to this submission
- **Notification Service**: Kafka consumer skeleton only; email/SMS templates not implemented
- **Multi-region active-active**: Discussed in NFR section; implementation requires Conflict-free Replicated Data Types (CRDT) or region affinity routing — out of V1 scope
- **Refund workflow**: Mentioned in `cancelBooking`; full saga not implemented
- **Admin portal**: Roles defined in JWT; admin endpoints not scaffolded

### Key Assumptions
1. One booking per customer per `idempotencyKey`; customers can create multiple bookings for a show
2. Seat inventory is generated when a show is created (batch insert from screen.seats)
3. Afternoon discount and 3rd-ticket discount are mutually exclusive — best discount wins per ticket
4. Payment is handled by an external gateway; this service only tracks `paymentRef`
5. Theatre onboarding is manual for V1 via back-office; self-service portal in V2

---
