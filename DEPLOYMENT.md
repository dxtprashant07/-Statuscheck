# Deployment

The app ships as three containers: **PostgreSQL**, the **Spring Boot backend**, and an
**nginx-served React frontend**. Everything is wired together in `docker-compose.yml`.

## 1. Configure secrets

```bash
cp .env.example .env
# edit .env and set: DB_PASSWORD, JWT_SECRET (>=32 chars), GROQ_API_KEY,
# and APP_CORS_ALLOWED_ORIGINS (the URL you open the app at).
```

`docker compose` will refuse to start if `DB_PASSWORD`, `JWT_SECRET` or `GROQ_API_KEY`
are missing — there are no insecure defaults in the `prod` profile.

## 2. Build & run

```bash
docker compose up -d --build
```

- Frontend: http://localhost:8081  (change with `FRONTEND_PORT`)
- Backend health: proxied internally; `docker compose ps` shows health status.
- Data persists in named volumes: `statusreport-pgdata` (database) and
  `statusreport-uploads` (uploaded documents).

First start creates the DB schema automatically (`ddl-auto: update`). Open the site,
register an account, and you're in.

## 3. Production notes / TLS

- **HTTPS (required for public deployment):** terminate TLS in front of the `frontend`
  container — either a platform load balancer or a reverse proxy (nginx/Caddy/Traefik)
  with a real certificate (e.g. Let's Encrypt). Then point `APP_CORS_ALLOWED_ORIGINS`
  at your `https://` domain. JWTs travel in the `Authorization` header, so plain HTTP
  on an untrusted network exposes them.
- **JWT_SECRET / DB_PASSWORD:** use strong, unique values; rotate if leaked.
- **Backups:** snapshot the `statusreport-pgdata` volume (or use `pg_dump`).

## Hardening already in place

- **Fail-fast secrets** — the `prod` profile requires `DB_*`, `JWT_SECRET`,
  `APP_CORS_ALLOWED_ORIGINS` with no defaults; compose refuses to start without them.
- **Non-root container** — the backend image runs as an unprivileged `app` user.
- **Auth rate limiting** — `/api/auth/login` and `/api/auth/register` are throttled
  per client IP (10 requests / minute → HTTP 429) to blunt brute-force.
- **Health checks** — `/actuator/health` drives both the image `HEALTHCHECK` and the
  compose healthcheck; the frontend waits on a healthy backend.
- **Container-aware heap** — `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` so the JVM respects
  the container memory limit.
- **CI build gate** — `.github/workflows/ci.yml` builds the backend jar and frontend
  bundle on every push / PR.

## Pre-go-live checklist

- [ ] Real `.env` with strong `JWT_SECRET` (≥32 chars) and `DB_PASSWORD`.
- [ ] `APP_CORS_ALLOWED_ORIGINS` set to your real public `https://` origin.
- [ ] TLS terminated in front of the stack (see above).
- [ ] A valid `GROQ_API_KEY` with enough quota (free tier ≈100k tokens/day — large
      OCR'd documents can exhaust it; consider a paid tier or `LLM_PROVIDER=anthropic`).
- [ ] Backup/restore tested for `statusreport-pgdata`.
- [ ] `docker compose up -d --build` runs green on the target host (Docker required;
      not verifiable on a machine without Docker).

## Local (non-Docker) development

Backend (file-based H2, no Postgres needed):
```
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Backend against local Postgres (default profile): `mvn spring-boot:run`.
Frontend dev server: `npm run dev` (proxies /api to localhost:8080).

## Still on the roadmap (not yet done)

- **Async long-running jobs** — OCR + LLM passes currently run inside the request
  (nginx/compose timeouts are raised to 600s to accommodate this). Moving to a
  background job + status polling would be the next reliability improvement.
- **Automated tests** — CI currently builds both apps but there is no test suite yet;
  add unit/integration tests so CI gates on behaviour, not just compilation.
- **DB migrations** — schema is managed by `ddl-auto: update`; adopt Flyway/Liquibase
  before the schema gets complex or you need controlled rollbacks.
- **Shared rate-limit store** — the auth limiter is per-instance (in-memory); move it
  to Redis if you run more than one backend replica.
