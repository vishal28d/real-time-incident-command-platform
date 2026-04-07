# Real-Time Incident Command Platform

A system that detects alerts from services, creates incidents, and shows everything live on a dashboard — all connected through a message queue (Kafka).

---

## What This Project Does

Imagine you run a company with multiple backend services (payments, auth, etc.). When something breaks, you need to:

1. **Receive the alert** — something broke in production
2. **Create an incident** — log it and track it
3. **See it live** — your team should see it the moment it happens, no refresh needed

This project does exactly that. Three small backend services + one frontend dashboard + Kafka connecting them all.

---

## How It Works (Simple Flow)

```
Your Service / Monitoring Tool
        |
        | POST /api/v1/alerts
        ↓
  Ingestion Service (port 8081)
        |
        | publishes event to Kafka
        ↓
      Kafka (message broker)
        |
        ↓
  Realtime Service (port 8083)
        |
        | pushes event to browser via SSE (live stream)
        ↓
  Frontend Dashboard (port 3000)   ←── also fetches incidents from Incident Service (port 8082)
```

**SSE (Server-Sent Events)** = a way for the server to push data to the browser in real-time, without the browser having to ask repeatedly.

**Kafka** = a message queue. Services don't talk to each other directly. Instead, they drop messages into Kafka and whoever needs that message picks it up. This keeps services independent.

---

## Project Structure

```
Project-1/
├── services/
│   ├── ingestion-service/     → receives alerts from outside
│   ├── incident-service/      → creates & stores incidents
│   └── realtime-service/      → listens to Kafka, pushes to browser
├── frontend/
│   └── web/                   → Next.js dashboard UI
├── libs/
│   └── contracts/             → shared event schema (what messages look like)
└── deploy/
    ├── local/                 → Docker Compose file to run everything locally
    ├── k8s/                   → Kubernetes files to deploy to a cluster
    └── argocd/                → ArgoCD config for auto-deploy from Git
```

---

## The Three Backend Services

### 1. Ingestion Service — `services/ingestion-service/` (port 8081)

**Job:** Accept alerts from any monitoring tool and publish them to Kafka.

- Exposes: `POST /api/v1/alerts`
- Validates the alert (must have `serviceName`, `severity`, `message`)
- Assigns a unique ID to the alert
- Publishes to two Kafka topics:
  - `alert.received.v1` — for alert-specific consumers
  - `incident.timeline.v1` — for the live timeline on the dashboard

### 2. Incident Service — `services/incident-service/` (port 8082)

**Job:** Create incidents and let you list them.

- Exposes:
  - `POST /api/v1/incidents` — create a new incident
  - `GET /api/v1/incidents` — list all incidents
- Stores incidents in memory (no database in this MVP)
- Publishes to:
  - `incident.lifecycle.v1` — for tracking incident state changes
  - `incident.timeline.v1` — so the dashboard shows the new incident live

> The frontend fetches the incident list from this service on page load.

### 3. Realtime Service — `services/realtime-service/` (port 8083)

**Job:** Listen to Kafka and stream events to connected browsers in real-time.

- Exposes: `GET /api/v1/stream/events` (SSE endpoint)
- The browser connects to this once and keeps the connection open
- Every time a new event lands in `incident.timeline.v1`, this service immediately sends it to all connected browsers
- No polling, no refresh — it just appears

---

## Frontend Dashboard — `frontend/web/` (port 3000)

Built with **Next.js + React**.

**What you see on the dashboard:**
- Metric cards: total incidents, open incidents, live event count
- Left panel: list of active incidents (service name, severity, status)
- Right panel: live event timeline — every new alert or incident appears here in real-time

**How it works:**
- On load, it fetches all incidents from Incident Service (`GET /api/v1/incidents`)
- It also opens an SSE connection to Realtime Service and listens for new events
- New events are prepended to the timeline (shows last 25)

---

## Shared Event Format — `libs/contracts/`

Every message published to Kafka follows this structure:

```json
{
  "eventId": "unique ID for this event",
  "eventType": "alert-received or incident-opened",
  "schemaVersion": "v1",
  "occurredAt": "2024-01-01T12:00:00Z",
  "traceId": "optional, for distributed tracing",
  "incidentId": "which incident this belongs to",
  "payload": { ... }
}
```

This is defined in `libs/contracts/schemas/event-envelope.v1.json` and acts as the contract between services.

---

## Running Locally

Everything runs in Docker. One command starts it all:

```bash
docker compose -f deploy/local/docker-compose.yml up --build
```

This starts:
| Service | URL |
|---|---|
| Frontend Dashboard | http://localhost:3000 |
| Ingestion Service | http://localhost:8081 |
| Incident Service | http://localhost:8082 |
| Realtime Service | http://localhost:8083 |
| Kafka UI (inspect topics) | http://localhost:8090 |

---

## Testing the Flow

**Step 1 — Send an alert (simulates a monitoring tool detecting an issue):**

```bash
curl -X POST http://localhost:8081/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"payments-api","severity":"HIGH","message":"5xx spike"}'
```

**Step 2 — Create an incident manually:**

```bash
curl -X POST http://localhost:8082/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{"title":"Payments degraded","serviceName":"payments-api","severity":"HIGH"}'
```

Watch the dashboard at http://localhost:3000 — the timeline updates immediately.

---

## Deployment (Kubernetes + ArgoCD)

### Kubernetes — `deploy/k8s/`

- `namespace.yaml` — creates the `incident-platform` namespace
- `incident-service.yaml` — deploys Incident Service with health checks (`/actuator/health`)
- `kustomization.yaml` — groups all manifests together

### ArgoCD — `deploy/argocd/`

ArgoCD watches the `main` branch of this repo. When you push changes to `deploy/k8s/base/`, ArgoCD automatically applies them to the cluster.

- Auto-sync: enabled
- Self-heal: enabled (if someone manually changes something in the cluster, ArgoCD reverts it)

---

## Tech Stack Summary

| What | Technology |
|---|---|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3.3.4 |
| Message broker | Apache Kafka 3.7.0 |
| Frontend | Next.js 14 + React 18 |
| Containerization | Docker (multi-stage builds) |
| Local orchestration | Docker Compose |
| Production orchestration | Kubernetes |
| GitOps / Auto-deploy | ArgoCD |

---

## What's Not Built Yet (Roadmap)

- **Correlation Service** — automatically group related alerts into one incident instead of creating duplicates
- **Database** — incidents are currently in-memory; adding PostgreSQL would make them persist across restarts
- **RBAC** — role-based access so different team members have different permissions
- **Escalation policies** — auto-escalate if an incident isn't acknowledged within X minutes
- **SLA timers** — track how long incidents stay open
- **K8s overlays** — separate Kubernetes configs for dev, staging, prod environments
# real-time-incident-command-platform
