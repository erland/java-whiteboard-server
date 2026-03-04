# Dev Environment (Docker Compose)

## Services
- Postgres: `localhost:5432` (db/user/pass: `whiteboard`)
- Keycloak: `http://localhost:18080` (admin/admin)
- Server: `http://localhost:8080`

## Start
```bash
docker compose up -d --build
```

## Health
- Server: `GET http://localhost:8080/api/healthz`
- Quarkus health:
  - `GET http://localhost:8080/q/health/live`
  - `GET http://localhost:8080/q/health/ready`

## Keycloak realm
Realm import lives at `dev/keycloak/realm-whiteboard.json`.
