# Development Plan — java-whiteboard-server

## 1. Goal
Build an in-house collaboration server for the PWA whiteboard that replaces the current managed backend with a Docker-runnable service.

## 2. Technology Choices (explicit)
- **Language**: Java 21
- **Framework**: Quarkus
- **Database**: PostgreSQL
- **Migrations**: Flyway
- **Authentication**: Keycloak (OIDC/JWT)
- **Build**: Maven
- **Packaging**: container image + runnable jar
- **Local dev**: Docker Compose (Postgres + Keycloak + server)

## 3. Project Structure (proposed)
```text
java-whiteboard-server/
  docs/
    functional-specification.md
    development-plan.md
  src/main/java/...
    api/                 # REST endpoints (boards, invites, snapshots)
    ws/                  # WebSocket endpoints and message handlers
    domain/              # domain objects, permissions, sequencing
    persistence/         # repositories/DAOs
    security/            # Keycloak/JWT validation, invite token verification
    service/             # orchestration: rooms, snapshot service, op log service
  src/main/resources/
    application.properties
    db/migration/        # Flyway migrations
  src/test/java/...
    api/                 # REST tests
    ws/                  # websocket protocol tests
```

## 4. Architecture Decisions (implementation-level)

### 4.1 WebSocket model
- One WS endpoint, e.g. `/ws/board/{boardId}`
- First client message must authenticate:
  - Either `Authorization` header during upgrade (preferred when proxy supports)
  - Or initial `AUTH` message containing bearer token or invite token
- Maintain in-memory **Room** per board:
  - participants map: sessionId -> connection
  - presence state per session
  - sequence counter per board (or backed by DB sequence)

### 4.2 Persistence strategy
MVP durability:
- Store **latest snapshot** per board (JSON) with version.

Optionally (later):
- Store **append-only op log** for audit/replay.

MVP join behavior:
- On join, send latest snapshot and current presence list.
- Missed-ops replay can be a later enhancement.

### 4.3 Invite tokens
- MVP: opaque random token stored as **hash** in DB; raw token never stored.
- Include permission (viewer/editor) and optional expiry.
- Validate token on join and for invite endpoints.

### 4.4 Authorization
- Owner: board.ownerUserId == authenticated user id
- Shared users (phase 2): `board_permissions` table
- Invited participants: invite defines permission
- Enforce “viewer cannot write ops”.

## 5. Step-by-step implementation plan (LLM-friendly)

### Step 1 — Create repo skeleton + build + CI
**Deliverables**
- Maven Quarkus project scaffolding
- GitHub Actions workflow: build + tests
- Basic README with local dev commands
- Dockerfile for the service

**Verification**
- `mvn -q test`

---

### Step 2 — Docker Compose for Postgres + Keycloak + server (dev)
**Deliverables**
- `docker-compose.yml` with Postgres + Keycloak
- Keycloak realm import:
  - one client for the whiteboard server
  - test users/roles for local dev
- Server config to validate Keycloak-issued JWTs

**Verification**
- `docker compose up -d`
- Server logs show DB connection OK

---

### Step 3 — Flyway baseline schema
**Deliverables**
- Flyway migrations to create:
  - `boards`
  - `invites`
  - `board_snapshots`
  - (optional) `board_ops`
- Quarkus config for Flyway on startup

**Verification**
- Start server; confirm tables exist

---

### Step 4 — Security: authenticated HTTP baseline
**Deliverables**
- JWT validation configured
- `GET /api/me` returns subject + roles
- Standard error format for 401/403

**Verification**
- With valid token → 200
- Without token → 401

---

### Step 5 — Boards API
**Deliverables**
- Endpoints:
  - `POST /api/boards`
  - `GET /api/boards`
  - `GET /api/boards/{id}`
  - `PATCH /api/boards/{id}`
  - `DELETE /api/boards/{id}` (archive or delete)
- Ownership enforced

**Verification**
- Integration tests for access control

---

### Step 6 — Invites API
**Deliverables**
- Endpoints:
  - `POST /api/boards/{id}/invites`
  - `GET /api/boards/{id}/invites`
  - `POST /api/invites/{token}/validate`
  - `DELETE /api/invites/{token}`
- Secure token generation + hashed storage

**Verification**
- Tests for expiry/revocation/maxUses

---

### Step 7 — Snapshots API
**Deliverables**
- Endpoints:
  - `GET /api/boards/{id}/snapshot`
  - `PUT /api/boards/{id}/snapshot` (with version precondition)
- Size limits + validation

**Verification**
- Save snapshot → retrieve snapshot

---

### Step 8 — WebSocket MVP (join + presence + op broadcast)
**Deliverables**
- WS endpoint `/ws/board/{id}`
- Auth supports:
  - bearer JWT (owner/shared)
  - invite token (viewer/editor)
- Join sends:
  - latest snapshot + snapshot version
  - presence list
  - session id
- Presence broadcast
- Ops broadcast with monotonic `seq`

**Verification**
- WS tests with two clients; viewer cannot write ops

---

### Step 9 — Hardening
**Deliverables**
- Configurable limits (message size, rate limits)
- Health (liveness/readiness)
- Metrics for connections/ops/errors
- Structured logging with correlation ids

**Verification**
- Readiness fails if DB down; limits enforced

## 6. Notes on frontend migration
- Replace Supabase calls with the new REST APIs.
- Point WS URL to this service.
- Keep the existing message protocol where possible; version if needed.
