# Deployment

FlowSight deploys as two units:

- Frontend (Next.js) on Vercel
- Backend (Spring Boot) and Postgres on Render, described by `render.yaml`

## Vercel (frontend)

Vercel auto-detects Next.js. Default build (`next build`) and output settings
are correct; no `vercel.json` is required. Set the root directory to `frontend`.

### Required environment variables

| Variable | Scope | Value | Notes |
|---|---|---|---|
| `NEXT_PUBLIC_API_URL` | Production, Preview | `https://<render-backend>.onrender.com` | Inlined at build time. Must be set before the build runs, or the app calls `http://localhost:8080` and nothing works. Redeploy after changing it. |

That is the only variable the frontend reads. After deploying, the backend's
`CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` must include this Vercel domain.

## Render (backend + database)

Apply `render.yaml` as a Blueprint. It provisions the backend web service (built
from `backend/Dockerfile`, health check `/actuator/health`) and a managed
Postgres instance. Confirm the `plan` and `region` values before applying.

### Variables set automatically by the Blueprint

- `JWT_SECRET` (generated once, stable across deploys)
- `DB_USERNAME`, `DB_PASSWORD` (from the managed database)
- `EMAIL_PROVIDER`, `MAIL_HOST`, `MAIL_PORT`, `PASSWORD_RESET_DEV_EXPOSE_LINK`, `RECEIPT_OCR_URL`

### Variables to set manually in the Render dashboard (marked `sync: false`)

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<internal host>:<port>/flowsight` (host/port on the database Info page) |
| `CORS_ALLOWED_ORIGINS` | The Vercel domain, e.g. `https://your-app.vercel.app` |
| `FRONTEND_URL` | The Vercel domain (used to build password-reset links) |
| `MAIL_USERNAME` | Gmail address |
| `MAIL_PASSWORD` | Gmail App Password (16 chars, no spaces) |
| `MAIL_FROM` | `FlowSight <your-gmail-address>` |
| `GROQ_API_KEY` | Optional; only used if the OCR microservice is deployed |

Flyway runs the migrations automatically on the first boot against the managed
database.

## Post-deploy verification

1. `GET https://<backend>/actuator/health` returns `{"status":"UP"}`.
2. Open the Vercel URL, register a user, and confirm the dashboard loads (proves
   `NEXT_PUBLIC_API_URL` and CORS are correct).
3. Trigger a password reset and confirm the email arrives (proves Gmail SMTP).

## Known limitations

- Uploaded receipt images are written to the container filesystem
  (`/tmp/flowsight-receipts`), which is ephemeral on Render. Files are lost on
  every deploy or restart. Move to object storage (S3/R2) before receipts are
  business-critical, or treat them as transient.
- The high-quality OCR path (the `receipt-ocr` microservice) is not included in
  this Blueprint. With `RECEIPT_OCR_URL` empty, the backend falls back to the
  in-image Tesseract engine.
- Rate limiting is in-memory and per-instance; it resets on restart and is not
  shared across horizontally scaled instances.
