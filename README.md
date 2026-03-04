# java-whiteboard-server

In-house collaboration backend for the Whiteboard PWA.

## Tech stack
- Java 21
- Quarkus
- PostgreSQL
- Flyway
- (Planned) Keycloak OIDC/JWT

## Local build
```bash
mvn -q test
mvn -q package
```

## Run in dev mode
```bash
mvn quarkus:dev
```

## Notes
- Step 1 scaffolds the repo, build, and CI only (Quarkus REST + tests + Dockerfile + CI).
- Docker Compose (Postgres + Keycloak) will be added in Step 2.

## Dev stack (Docker Compose)
This repo includes a development `docker-compose.yml` with:
- Postgres (db: `whiteboard`, user/pass: `whiteboard`)
- Keycloak (admin/admin) on http://localhost:18080
- Server on http://localhost:8080

Start everything:
```bash
docker compose up -d --build
```

Stop:
```bash
docker compose down
```

### Keycloak dev realm
A realm is imported automatically at startup:
- Realm: `whiteboard`
- Users:
  - `alice` / `alice`
  - `bob` / `bob`
  - `admin` / `admin`

### Getting a dev access token (password grant)
For quick local testing, Keycloak is configured with a public client `whiteboard-pwa` with direct access grants enabled.

Example token request for `alice`:
```bash
curl -s \
  -d "grant_type=password" \
  -d "client_id=whiteboard-pwa" \
  -d "username=alice" \
  -d "password=alice" \
  "http://localhost:18080/realms/whiteboard/protocol/openid-connect/token" | jq -r .access_token
```

You can then call:
```bash
curl -s http://localhost:8080/api/healthz | jq
```

Note: Step 4 will introduce protected endpoints that require the bearer token.

### Authenticated endpoint: /api/me
Once you have an access token, call:
```bash
TOKEN="$(curl -s \
  -d "grant_type=password" \
  -d "client_id=whiteboard-pwa" \
  -d "username=alice" \
  -d "password=alice" \
  "http://localhost:18080/realms/whiteboard/protocol/openid-connect/token" | jq -r .access_token)"

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me | jq
```

## Invites API (Step 6)
- `POST /api/boards/{boardId}/invites` (owner) -> returns a one-time `token` (store it client-side)
- `GET /api/boards/{boardId}/invites` (owner) -> lists invites (no raw tokens)
- `DELETE /api/boards/{boardId}/invites/{inviteId}` (owner) -> revoke
- `POST /api/invites/validate` (public) -> validate token

## Snapshots API (Step 7)
- `POST /api/boards/{boardId}/snapshots` -> create a versioned snapshot (opaque JSON)
- `GET /api/boards/{boardId}/snapshots/latest` -> latest snapshot
- `GET /api/boards/{boardId}/snapshots/{version}` -> snapshot by version
- `GET /api/boards/{boardId}/snapshots` -> list versions (desc)

## WebSocket MVP (Step 8)
WebSocket endpoint:
- `ws://localhost:8080/ws/boards/{boardId}?userId=<userId>`
- or with an invite token:
  `ws://localhost:8080/ws/boards/{boardId}?invite=<token>`

Protocol (JSON):
- Server -> `joined`: `{ "type":"joined", "boardId":"...", "yourUserId":"...", "users":[...] }`
- Server -> `presence`: `{ "type":"presence", "boardId":"...", "users":[...] }`
- Client -> `op`: `{ "type":"op", "op": { ... } }`
- Server -> `op`: `{ "type":"op", "boardId":"...", "from":"...", "op":{...} }` (broadcast to other peers)
