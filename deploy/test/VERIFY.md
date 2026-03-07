# Test edge verification guide

This guide is the final verification step for the repo-based HTTP test deployment.

It covers two intended modes:
- local mode at `http://localhost/*`
- remote-host mode at `http://serverxxx/*`

## Prerequisites

- Docker
- Docker Compose or Docker Compose plugin
- A cloned repo checkout
- A `.env` file based on `.env.example`

## Common startup commands

```bash
docker compose -f docker-compose.edge.test.yml pull
docker compose -f docker-compose.edge.test.yml up -d
docker compose -f docker-compose.edge.test.yml logs -f
```

## Localhost mode

### 1. Environment

Use:

```dotenv
PUBLIC_BASE_URL=http://localhost
PUBLIC_HOST=localhost
```

### 2. Startup

```bash
cp .env.example .env
docker compose -f docker-compose.edge.test.yml pull
docker compose -f docker-compose.edge.test.yml up -d
```

### 3. Browser checks

Open:
- `http://localhost/pwa-whiteboard/`
- `http://localhost/auth/`

Verify:
- the PWA loads
- login redirects to the same `localhost` host
- login returns to `/pwa-whiteboard/`

### 4. REST checks

Verify:
- anonymous health endpoint works: `http://localhost/api/healthz`
- authenticated flows work after login
- the app can load boards through the same `localhost` origin

### 5. WebSocket checks

Verify in the app:
- opening a board succeeds
- presence updates appear
- operations propagate without auth errors

### 6. Optional quick config check

```bash
PUBLIC_BASE_URL=http://localhost ./deploy/test/verify-edge-config.sh
```

## Remote-host mode

### 1. Environment

Set the public host in `.env`, for example:

```dotenv
PUBLIC_BASE_URL=http://serverxxx
PUBLIC_HOST=serverxxx
```

### 2. Startup

```bash
docker compose -f docker-compose.edge.test.yml pull
docker compose -f docker-compose.edge.test.yml up -d
```

### 3. Browser checks

From your workstation browser, open:
- `http://serverxxx/pwa-whiteboard/`
- `http://serverxxx/auth/`

Verify:
- the PWA loads from the remote host
- login redirects to `http://serverxxx/auth/...`
- login returns to `http://serverxxx/pwa-whiteboard/`

### 4. REST checks

Verify:
- `http://serverxxx/api/healthz` responds
- authenticated API calls succeed after login
- there are no issuer mismatch errors in server logs

### 5. WebSocket checks

Verify in the app:
- joining a board succeeds
- presence updates appear
- operations propagate through `/ws/...`
- there are no WebSocket auth failures caused by the proxy path

### 6. Optional quick config check

Run on the server:

```bash
PUBLIC_BASE_URL=http://serverxxx ./deploy/test/verify-edge-config.sh
```

## Log signals to watch for

### Good signs
- Keycloak starts and imports the realm
- nginx starts without config errors
- server starts and connects to Postgres
- no OIDC issuer mismatch errors in server logs
- no redirect URI mismatch errors in Keycloak login flow

### Problem signs
- Keycloak redirect URI mismatch
- backend token issuer mismatch
- browser login loop between `/pwa-whiteboard/` and `/auth/`
- WebSocket join rejected only in the test edge setup

## Cleanup

Stop the stack:

```bash
docker compose -f docker-compose.edge.test.yml down
```
