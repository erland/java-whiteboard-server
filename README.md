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
