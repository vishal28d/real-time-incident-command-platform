# Real-Time Incident Command Platform

A production-grade system that detects alerts from services, auto-creates incidents, persists them in MongoDB, and streams everything live to an ops dashboard — all connected through Kafka.

Built to demonstrate **event-driven microservices**, **real-time streaming**, **alert correlation**, and **GitOps deployment** in a realistic on-call scenario.

---

## Architecture

```
Your Service / Monitoring Tool
        |
        | POST /api/v1/alerts
        ↓
  Ingestion Service (port 8081)
        |
        | publishes to Kafka: alert.received.v1
        |                     incident.timeline.v1
        ↓
  Incident Service (port 8082)  ←── consumes alert.received.v1
        |                               checks for open incident → correlate or create
        | saves to MongoDB              publishes incident-opened / alert-correlated
        |
        | publishes to Kafka: incident.lifecycle.v1
        |                     incident.timeline.v1
        ↓
  Realtime Service (port 8083)  ←── consumes incident.timeline.v1
        |
        | pushes SSE to browser (all connected clients)
        ↓
  Frontend Dashboard (port 3000)
        |
        └── fetches existing incidents from Incident Service on load
```

---

## Why This Architecture — Interview Talking Points

### Why Kafka instead of direct service-to-service HTTP calls?

Services don't call each other at all. The Ingestion Service has no idea the Incident Service exists — it just fires an event onto `alert.received.v1`. This means:

- **Decoupling**: I can add a new consumer (e.g. a Slack notifier or PagerDuty bridge) without touching any existing service.
- **Resilience**: If the Incident Service is down, Kafka holds the messages. Once it recovers, it replays from where it left off — no alerts are lost.
- **Backpressure**: Kafka naturally absorbs traffic spikes. If monitoring floods us with alerts, the Incident Service processes them at its own pace.

Compare this to HTTP: if the Incident Service is temporarily down, the Ingestion Service gets a 503 and the alert is gone.

---

### Why SSE (Server-Sent Events) instead of WebSockets or polling?

The dashboard only needs the **server → browser** direction. Kafka events flow in one direction; the browser never needs to push data upstream to the Realtime Service.

- **SSE is simpler** than WebSockets for unidirectional push — no upgrade handshake, no protocol overhead, built into every browser.
- **Auto-reconnect** is native to SSE (`EventSource` reconnects automatically on disconnect).
- **HTTP/2 compatible** — SSE multiplexes cleanly over a single connection.

Polling was rejected because it adds latency (up to the poll interval) and wastes bandwidth on empty responses.

---

### Why MongoDB instead of a relational database?

Incidents are semi-structured and schema evolves fast in an ops platform:

- Today an incident has a `title` and `severity`. Tomorrow it might have `runbook`, `postmortem_url`, `linked_alert_ids`, or custom team fields.
- MongoDB lets us add fields without migrations, which matters when the on-call team needs new metadata fast.
- The query patterns are simple: fetch all, fetch by ID, fetch by status — no joins needed.

If we had complex relational queries (e.g. billing, user permissions), PostgreSQL would be the better choice.

---

### Alert Correlation — preventing alert storms

Without correlation, a 5-minute outage on `payments-api` that fires 200 alerts would create 200 separate incidents. Nobody can work in that environment.

The Incident Service checks: **"Is there already an OPEN incident for this service?"** before creating a new one. If yes, it fires an `alert-correlated` event instead of `incident-opened`. The incident count stays manageable; the timeline still shows every alert that came in.

This is the same pattern used by PagerDuty and Datadog — grouping alerts into a single incident reduces on-call fatigue.

```java
// IncidentService.java
Optional<Incident> existing = repository.findByServiceNameAndStatus(
    request.serviceName(), Incident.Status.OPEN);

if (existing.isPresent()) {
    publishEvent("alert-correlated", existing.get(), request.description());
    return existing.get();   // no new incident created
}
```

---

### Circuit Breaker — handling MongoDB failures gracefully

Every write path in the Incident Service is wrapped with Resilience4j's `@CircuitBreaker`. If MongoDB becomes slow or unavailable:

1. The first few failures are counted.
2. Once the threshold is crossed, the circuit **opens** — further requests fast-fail immediately instead of waiting for timeouts.
3. After a configured interval, the circuit **half-opens** to probe recovery.

Without this, a MongoDB blip causes all 8 Kafka consumer threads to hang waiting for a 30-second connection timeout, eventually starving the thread pool. With it, the service degrades gracefully and recovers automatically.

---

### Prometheus Integration

The Ingestion Service has two alert endpoints:

- `POST /api/v1/alerts` — generic JSON (any tool, curl, scripts)
- `POST /api/v1/alerts/prometheus` — Alertmanager webhook format

The Prometheus endpoint parses the standard webhook schema (with `alerts[]`, `labels`, `annotations`) and maps Prometheus severity strings (`critical`, `warning`, `info`) to internal enum values. This means the platform plugs directly into an existing Prometheus + Alertmanager setup with a single webhook URL — no custom exporter needed.

---

### GitOps Deployment with ArgoCD

The `deploy/k8s/base/` directory contains Kubernetes manifests managed by Kustomize. ArgoCD watches the `main` branch — any merged commit that changes those manifests is **automatically deployed** to the cluster with self-healing enabled.

This means:
- No manual `kubectl apply` in CI
- Drift detection: if someone manually patches a deployment, ArgoCD reverts it
- Full audit trail: every deploy maps to a git commit

---

## Project Structure

```
Project-1/
├── services/
│   ├── ingestion-service/     → receives alerts, publishes to Kafka
│   ├── incident-service/      → auto-creates & stores incidents, correlation logic
│   └── realtime-service/      → consumes Kafka, pushes SSE to browser
├── frontend/
│   └── web/                   → Next.js 14 dashboard (live events, filters, assign)
├── libs/
│   └── contracts/             → shared Kafka event schema
└── deploy/
    ├── local/                 → Docker Compose (all services + Kafka + MongoDB)
    ├── k8s/                   → Kubernetes manifests (Kustomize)
    └── argocd/                → ArgoCD GitOps config
```

---

## The Three Backend Services

### 1. Ingestion Service — port 8081

Accepts alerts from any monitoring tool and publishes them to Kafka. Stateless — no database.

**Endpoints:**

- `POST /api/v1/alerts` — Generic alert ingestion
  ```json
  { "serviceName": "payments-api", "severity": "HIGH", "message": "5xx spike" }
  ```

- `POST /api/v1/alerts/prometheus` — Prometheus Alertmanager webhook
  Parses standard Prometheus format and converts to internal event schema.

Publishes to:
- `alert.received.v1` — consumed by Incident Service
- `incident.timeline.v1` — consumed by Realtime Service (shown on dashboard immediately)

---

### 2. Incident Service — port 8082

The core domain service. Creates incidents, handles correlation, persists to MongoDB, exposes the full REST API.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/incidents` | Create incident manually |
| `GET` | `/api/v1/incidents` | List all (supports `?status=OPEN&severity=HIGH&serviceName=X&query=text`) |
| `GET` | `/api/v1/incidents/{id}` | Get single incident |
| `PUT` | `/api/v1/incidents/{id}/resolve` | Resolve an incident |
| `PUT` | `/api/v1/incidents/{id}/assign?assignedTo=X` | Assign to a user |
| `PUT` | `/api/v1/incidents/{id}/tags?tag=X` | Add tag |
| `DELETE` | `/api/v1/incidents/{id}/tags?tag=X` | Remove tag |

**Swagger UI:** `http://localhost:8082/swagger-ui.html`

**Kafka consumer:** listens on `alert.received.v1` → runs correlation check → creates or correlates.

**Publishes to:**
- `incident.lifecycle.v1` — lifecycle state changes
- `incident.timeline.v1` — all events, consumed by Realtime Service

---

### 3. Realtime Service — port 8083

Purely a Kafka → SSE bridge. No database, no business logic.

**Endpoint:** `GET /api/v1/stream/events`

The browser opens this connection once and keeps it. Every event on `incident.timeline.v1` is broadcast to all connected clients via SSE. Emitters that disconnect are cleaned up automatically.

---

## Frontend Dashboard — port 3000

Built with **Next.js 14 + React 18**.

**Features:**
- **Live connection badge** — shows SSE connected/reconnecting state
- **Metric cards** — total, open, resolved, live event count
- **Filter tabs** — All / Open / Resolved
- **Search** — filter incidents by title or service name
- **Incident cards** — severity badge, status badge, tags, assigned user, relative time ("2m ago")
- **Resolve button** — calls REST API, updates state immediately
- **Assign button** — opens modal, calls `PUT /assign` endpoint
- **Live Timeline** — every Kafka event rendered in real-time, color-coded by type
- **Loading skeleton** — shown while initial fetch is in flight
- **Auto-reconnect** — SSE reconnects automatically after disconnect

**Data flow:**
1. On mount: `GET /api/v1/incidents` → populates the incidents list
2. SSE stream stays open: new events update state without any polling
3. Resolve/Assign calls REST API → confirmed update reflected in UI

---

## Event Schema — `libs/contracts/`

Every Kafka message follows this envelope:

```json
{
  "eventId": "uuid",
  "eventType": "alert-received | alert-correlated | incident-opened | incident-resolved | incident-assigned",
  "schemaVersion": "v1",
  "occurredAt": "2024-01-01T12:00:00Z",
  "incidentId": "uuid",
  "payload": {
    "title": "...",
    "serviceName": "...",
    "severity": "CRITICAL | HIGH | MEDIUM | LOW",
    "status": "OPEN | INVESTIGATING | RESOLVED | CLOSED",
    "assignedTo": "...",
    "tags": ["auto-created"]
  }
}
```

The same envelope is used for all event types — consumers check `eventType` to decide what to do.

---

## Running Locally

```bash
./restart.sh
```

This handles: stop → remove conflicting containers → prune networks → start stack.

Or manually:

```bash
docker compose -f deploy/local/docker-compose.yml down --remove-orphans
docker compose -f deploy/local/docker-compose.yml up --build
```

| Service | URL |
|---------|-----|
| Frontend Dashboard | http://localhost:3000 |
| Ingestion Service | http://localhost:8081 |
| Incident Service | http://localhost:8082 |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| Realtime Service | http://localhost:8083 |
| Kafka UI | http://localhost:8090 |
| Prometheus | http://localhost:9090 |
| Alertmanager | http://localhost:9093 |
| MongoDB | localhost:27017 |

---

## Testing the Flow

**1. Send an alert → auto-creates incident → appears live on dashboard:**

```bash
curl -X POST http://localhost:8081/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"payments-api","severity":"HIGH","message":"5xx spike detected"}'
```

**2. Send a second alert for the same service → triggers correlation (no new incident):**

```bash
curl -X POST http://localhost:8081/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"payments-api","severity":"CRITICAL","message":"complete outage"}'
```
Watch the timeline — you'll see `alert-correlated` instead of `incident-opened`.

**3. Simulate a Prometheus Alertmanager webhook:**

```bash
curl -X POST http://localhost:8081/api/v1/alerts/prometheus \
  -H "Content-Type: application/json" \
  -d '{
    "version": "4",
    "status": "firing",
    "receiver": "webhook",
    "groupLabels": {"alertname": "ServiceDown"},
    "commonLabels": {"service": "payments-api", "severity": "critical"},
    "commonAnnotations": {"summary": "Payment service is down"},
    "externalURL": "http://localhost:9093",
    "alerts": [{
      "status": "firing",
      "labels": {"alertname": "ServiceDown", "service": "checkout-api", "severity": "critical"},
      "annotations": {"summary": "Checkout API not responding"},
      "startsAt": "2024-01-01T00:00:00Z",
      "generatorURL": "http://localhost:9090"
    }]
  }'
```

**4. Create an incident manually:**

```bash
curl -X POST http://localhost:8082/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{"title":"DB connection pool exhausted","serviceName":"user-service","severity":"HIGH"}'
```

**5. Resolve an incident:**

```bash
curl -X PUT http://localhost:8082/api/v1/incidents/{id}/resolve
```

**6. Filter open HIGH incidents:**

```bash
curl "http://localhost:8082/api/v1/incidents?status=OPEN&severity=HIGH"
```

Open the dashboard at http://localhost:3000 and watch everything appear live.

---

## Deployment (Kubernetes + ArgoCD)

### Kubernetes — `deploy/k8s/base/`

Manifests for all services, Kafka, MongoDB, and the frontend. Uses **Kustomize** to group resources.

- All services get health check probes (`/actuator/health`)
- ConfigMaps separate environment config from image builds
- Namespace: `incident-platform`

### ArgoCD — `deploy/argocd/`

- Watches `main` branch, path `deploy/k8s/base/`
- **Self-heal enabled** — any manual cluster changes are reverted automatically
- Every deployment is traceable to a git commit

---

## Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| Backend language | Java 21 | Virtual threads, modern switch expressions |
| Backend framework | Spring Boot 3.3.4 | Kafka, MongoDB, Resilience4j, Actuator out of the box |
| Message broker | Apache Kafka 3.7.0 | Durable, replayable, decoupled pub/sub |
| Database | MongoDB 7 | Schema flexibility for evolving incident fields |
| Resilience | Resilience4j Circuit Breaker | Prevents cascading failures on DB outages |
| API docs | SpringDoc OpenAPI (Swagger) | Auto-generated from annotations |
| Frontend | Next.js 14 + React 18 | SSR + client hooks, minimal config |
| Real-time | Server-Sent Events | Unidirectional push, simpler than WebSockets |
| Containerization | Docker (multi-stage builds) | Lean images, reproducible builds |
| Local orchestration | Docker Compose | One-command startup for all 8 services |
| Production orchestration | Kubernetes | Declarative, scalable, health-checked |
| GitOps / Auto-deploy | ArgoCD | Git as source of truth, automatic drift correction |

---

## Roadmap

- **Alert Correlation** — group related alerts into one incident ✅
- **Circuit Breaker** — Resilience4j on all MongoDB/Kafka paths ✅
- **Prometheus Integration** — Alertmanager webhook receiver ✅
- **Assignment & Tagging** — assign incidents to users, tag for categorization ✅
- **Full-text search** — search incidents by title keyword ✅
- **RBAC** — role-based access for viewer / responder / admin
- **Escalation policies** — auto-escalate if not acknowledged within X minutes
- **SLA timers** — track MTTD and MTTR per service
- **Comments / activity log** — per-incident audit trail
- **K8s overlays** — separate configs for dev, staging, prod
