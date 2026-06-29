# Deploy to Railway

This deploys the whole app as **one service** (the Spring Boot backend also serves
the React UI) plus a **managed PostgreSQL** database. You get a public HTTPS URL like
`https://your-app.up.railway.app` — no domain or TLS setup required.

The build uses the root [`Dockerfile`](Dockerfile), which bundles the frontend into
the backend jar.

## Prerequisites
- The project pushed to a **GitHub repo**.
- A free account at <https://railway.app>.
- A `GROQ_API_KEY` (or an `ANTHROPIC_API_KEY` if you set `LLM_PROVIDER=anthropic`).

## Steps

### 1. Create the project
1. Railway → **New Project** → **Deploy from GitHub repo** → pick this repo.
2. Railway detects the root `Dockerfile` and starts building. Let the first build run
   (it will fail to fully start until the DB + secrets exist — that's expected).

### 2. Add PostgreSQL
1. In the project, **New** → **Database** → **Add PostgreSQL**.
2. This creates a `Postgres` service exposing `PGHOST`, `PGPORT`, `PGUSER`,
   `PGPASSWORD`, `PGDATABASE`.

### 3. Set the backend service variables
Open your app service → **Variables** → add (use Railway's `${{Postgres.*}}` references
so they stay in sync):

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_SECRET` | a random string **≥ 32 chars** |
| `GROQ_API_KEY` | your Groq key |
| `APP_CORS_ALLOWED_ORIGINS` | your app's public URL (set after step 4; e.g. `https://your-app.up.railway.app`) |

Notes:
- `SPRING_PROFILES_ACTIVE=prod` and `PORT` are already handled (the Dockerfile sets the
  profile; the app binds Railway's injected `PORT`).
- The UI and API are the **same origin**, so CORS isn't strictly needed — but set
  `APP_CORS_ALLOWED_ORIGINS` to the public URL anyway (the `prod` profile requires it).

### 4. Expose it publicly
1. App service → **Settings** → **Networking** → **Generate Domain**.
2. Copy the URL, put it in `APP_CORS_ALLOWED_ORIGINS` (step 3), and redeploy.

### 5. Done
Open the URL, register an account, and use the app. The schema is created automatically
(`ddl-auto: update`).

## Things to watch
- **Memory:** OCR (Tesseract renders scanned PDFs at 300 DPI) + the JVM want
  **~1–2 GB**. If big scanned PDFs crash the service, bump the plan's RAM.
- **Uploaded files:** they're written to a container path. For durable storage across
  redeploys, attach a **Railway Volume** to the app service mounted at
  `/data/uploaded-documents` (the app's `APP_STORAGE_DIR`).
- **LLM quota:** the Groq free tier (~100k tokens/day) is easily exhausted by large
  documents and multiple users — budget for a paid tier for real public use.
- **Cost:** Railway's trial/free credits get you started; sustained public use needs a
  paid plan. Check current pricing.
