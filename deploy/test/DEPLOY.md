# Test edge deployment

This document describes the minimal repo-based workflow for running the published-image test stack from this repository.

## Goal

Use the same repo checkout for either:
- local tryout at `http://localhost/*`
- remote test deployment at `http://serverxxx/*`

The test stack is image-first and uses GHCR images for the application/runtime services. No local Maven or frontend build is required to try the app.

## Prerequisites

- Docker
- Docker Compose or the Docker Compose plugin
- Network access to pull container images

## Files involved

- `docker-compose.edge.test.yml`
- `.env`
- `.env.example`

## Supported variables

The main variable is:

```dotenv
PUBLIC_BASE_URL=http://localhost
```

For a remote server, change it to the browser-visible host, for example:

```dotenv
PUBLIC_BASE_URL=http://serverxxx
```

Optional image overrides are also available in `.env.example` if you need to pin or replace image tags.

## Local tryout

1. Create a local environment file:

```bash
cp .env.example .env
```

2. Keep the default public base URL:

```dotenv
PUBLIC_BASE_URL=http://localhost
```

3. Pull and start the stack:

```bash
docker compose -f docker-compose.edge.test.yml pull
docker compose -f docker-compose.edge.test.yml up -d
```

4. Open the application:
- App: `http://localhost/pwa-whiteboard/`
- Keycloak: `http://localhost/auth/`

## Remote test server

1. Clone the repository on the remote host.
2. Create an environment file:

```bash
cp .env.example .env
```

3. Set the public host in `.env`, for example:

```dotenv
PUBLIC_BASE_URL=http://serverxxx
```

4. Pull and start the stack:

```bash
docker compose -f docker-compose.edge.test.yml pull
docker compose -f docker-compose.edge.test.yml up -d
```

5. Open the application from your browser:
- App: `http://serverxxx/pwa-whiteboard/`
- Keycloak: `http://serverxxx/auth/`

## Useful commands

Start in background:

```bash
docker compose -f docker-compose.edge.test.yml up -d
```

View logs:

```bash
docker compose -f docker-compose.edge.test.yml logs -f
```

Stop the stack:

```bash
docker compose -f docker-compose.edge.test.yml down
```

## Notes

- The test stack is intentionally configured for plain HTTP.
- The PWA runtime config is rendered at container startup from the same `PUBLIC_BASE_URL` used by the OIDC-related settings.
- The backend validates tokens against the browser-visible issuer derived from `PUBLIC_BASE_URL`.
- Published images are used by default; local source compilation is not required for this workflow.


## Verification

After startup, use the verification guide in:
- `deploy/test/VERIFY.md`

For a quick template-rendering check before startup, you can also run:

```bash
PUBLIC_BASE_URL=http://localhost ./deploy/test/verify-edge-config.sh
```

or on a remote host:

```bash
PUBLIC_BASE_URL=http://serverxxx ./deploy/test/verify-edge-config.sh
```
