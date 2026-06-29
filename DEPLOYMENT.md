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

- **HTTPS:** terminate TLS in front of the `frontend` container — either a platform
  load balancer or a reverse proxy (nginx/Caddy/Traefik) with a real certificate
  (e.g. Let's Encrypt). Then point `APP_CORS_ALLOWED_ORIGINS` at your `https://` domain.
- **JWT_SECRET / DB_PASSWORD:** use strong, unique values; rotate if leaked.
- **Backups:** snapshot the `statusreport-pgdata` volume (or use `pg_dump`).

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
- Automated tests + CI/CD.
- Rate limiting on the public auth endpoints.
