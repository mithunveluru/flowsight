# Deployment

FlowSight deploys as three units:

- Frontend (Next.js) on Vercel
- Backend (Spring Boot) and Postgres on Render, described by `render.yaml`
- ml-service (FastAPI, optional) as a separate Docker service on Render

The app works with ml-service absent. Every model-backed feature has a
rule-based path that answers when the service is not configured, is down, times
out, or has its flag off. You can ship the backend first and add ml-service
later without a code change.

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
| `BREVO_API_KEY` | Brevo API key (SMTP & API keys section) |
| `BREVO_SENDER_EMAIL` | The sender address verified in Brevo |
| `GROQ_API_KEY` | Optional; see [Optional LLM features](#optional-llm-features) |

Email uses Brevo's HTTPS API, not SMTP: Render blocks outbound SMTP ports on
free/entry tiers, so Gmail SMTP times out there. In Brevo (free, no domain
required), verify a single sender under Senders, generate an API key, and set
the two variables above. Locally you can still use `EMAIL_PROVIDER=gmail`.

### Migrations

Flyway runs the migrations automatically on boot, in version order. The ML
upgrade adds V15 to V21, all additive (new tables and nullable or constant-default
columns, no drops or renames), so the previous backend version keeps working
against the migrated schema.

| Version | What it adds |
|---|---|
| `V15__ml_platform` | `model_versions`, `prediction_log`, `user_feedback`, and `users.personalization_enabled` |
| `V16__merchant_aliases` | `pg_trgm` extension, `merchant_aliases` (seeded), `user_merchant_rules`, `transactions.merchant_raw` |
| `V17__receipt_predictions` | OCR prediction and confirmation columns on `receipts` |
| `V18__category_predictions` | `category_source`, `category_model_version`, `category_alternatives` on `transactions` |
| `V19__recurring_scores` | Scoring columns on `recurring_patterns` |
| `V20__insight_feedback` | `recommendation_kind_stats` |
| `V21__receipt_image_retention` | `image_expires_at` and `image_deleted_at` on `receipts` |

Things to check before the first deploy that includes them:

- **`pg_trgm` is required.** V16 runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`.
  It is a trusted extension on Postgres 13 and later, so the database owner can
  create it. If the database role cannot create extensions, V16 fails and the
  backend does not boot. Try it on a staging database first, or create the
  extension once as a privileged role.
- **Do not renumber or reorder.** Apply V15 to V21 in order. A database that
  already ran a later version cannot pick up an earlier one without Flyway's
  out-of-order setting.
- **V21 expires old receipt images.** It marks every existing receipt image as
  expired, and the first cleanup run (about five minutes after boot) deletes
  them. They were originals with EXIF data and are never served by the new
  image endpoint.

## ml-service (separate Render service)

ml-service holds the models for categorization, merchant embeddings, recurring
scoring, forecasting and receipt OCR. Create it in Render as its own Docker
service, built from `ml-service/Dockerfile` with `ml-service` as the Docker
context. Prefer a private service, since only the backend calls it. The
`render.yaml` Blueprint in this repository does not define it yet, so add it
there or create it from the dashboard. The image is larger than a plain Python
image because it bundles Tesseract and OpenCV for OCR.

The container listens on `$PORT` (default 8000) and serves `GET /health` without
authentication; every other route needs the bearer token.

### ml-service variables

| Variable | Value | Notes |
|---|---|---|
| `ML_SERVICE_TOKEN` | A long random value | Required. With no token, ml-service rejects every request except `/health`. Must equal the backend's `ML_SERVICE_TOKEN`. |
| `ML_MODEL_DIR` | `/models` (image default) | Where model artifacts and the registry manifest live. |
| `ML_BOOTSTRAP` | `true` (default) | On boot, any capability the registry has never seen gets its bundled baseline model registered as active. A capability an operator already registered, retired or put in shadow is left alone. Set `false` to manage versions by hand. |
| `ML_<CAP>_ENABLED` | `true` / `false` | Serve the capability. Off by default. |
| `ML_<CAP>_SHADOW` | `true` / `false` | Shadow mode: the backend keeps the rule-based answer and logs the model's. |
| `ML_OCR_LLM_ENABLED`, `ML_OCR_LLM_MODEL`, `ML_OCR_LLM_URL`, `ML_OCR_LLM_API_KEY`, `ML_OCR_LLM_TIMEOUT` | off, empty, Groq URL, empty, `20` | Optional vision pass for OCR. See [Optional LLM features](#optional-llm-features). |

`<CAP>` is one of `CATEGORIZER`, `MERCHANT_EMBEDDINGS`, `RECURRING_SCORER`,
`FORECASTER`, `OCR`. A capability is served only when its flag is on and an
active model version is registered for it.

### Connecting the backend

Set these on the backend service. The `ENABLED` and `SHADOW` flags must match
the ones on ml-service.

| Variable | Value | Notes |
|---|---|---|
| `ML_SERVICE_URL` | The ml-service internal address, e.g. `http://<service>:<port>` | Empty means the backend never calls ml-service. |
| `ML_SERVICE_TOKEN` | Same value as on ml-service | |
| `ML_<CAP>_ENABLED`, `ML_<CAP>_SHADOW` | `false` by default | Turn on one capability at a time. |
| `ML_<CAP>_TIMEOUT` | `400ms` (`60s` for OCR) | Backend wait before it falls back to the rules. |

Every flag defaults to off on the backend, so deploying the backend alone
changes nothing. docker-compose is the exception: it turns the capabilities on
for local development, because the bundled baselines ship in the image.

A safe rollout for each capability is: set `SHADOW=true` on both services, watch
`prediction_log` and the ml-service logs, then switch to `ENABLED=true`.

### Production token

Production must set a real `ML_SERVICE_TOKEN`, for example `openssl rand -hex 32`.
docker-compose falls back to the well-known value `local-dev-ml-token` so a
local stack works out of the box. That value is a credential if the port is ever
reachable, so never use it outside a local machine, and do not publish the
ml-service port to the internet. ml-service also has no request-size limit of
its own, which is another reason to keep it on the private network.

### Model storage

With `ML_BOOTSTRAP=true`, ml-service seeds its baseline models into `ML_MODEL_DIR`
on every boot where the registry is empty, so it runs correctly on an ephemeral
disk. Anything you register by hand (retrained versions, shadow or retired
status) is lost on redeploy unless `/models` is on a persistent disk. Attach a
Render disk mounted at `/models` if you register your own versions.

## Optional LLM features

Nothing requires an LLM key. All LLM paths are off by default and fall back to
deterministic output.

| Feature | Switch | Notes |
|---|---|---|
| Insight narratives and the Ask FlowSight assistant | `LLM_ENABLED` (default `false`) | Also needs `LLM_MODEL`; no model name is assumed. `LLM_API_KEY` defaults to `GROQ_API_KEY`. `LLM_BASE_URL` can point to any OpenAI-compatible endpoint. Numbers in model output are validated against the supplied facts, and the deterministic text is used on any failure. |
| Merchant naming for names nothing else could resolve | `MERCHANT_LLM_ENABLED` (default `false`) | Also needs `MERCHANT_LLM_MODEL`. Input is redacted. |
| Receipt merchant adjudication and the `receipt-ocr` service | `GROQ_API_KEY` | `GROQ_MERCHANT_MODEL` lists preferred models. If none is available to the key, heuristics answer. |
| Vision pass in ml-service OCR | `ML_OCR_LLM_ENABLED` plus `ML_OCR_LLM_MODEL` | The receipt image is sent to the configured endpoint with detected personal text blacked out. Treat enabling it as a privacy decision. |

The Groq key is optional: leave `GROQ_API_KEY` unset and the app still runs.

## Receipt images

The backend keeps a compact, EXIF-free review copy of each receipt photo under
`UPLOAD_DIR` (default `/tmp/flowsight-receipts`), not the original. Drafts keep
it for 7 days, confirmed receipts for 24 hours after confirmation, and deleting a
receipt removes it immediately. A scheduled job removes expired files hourly.
The backend also needs a writable temp directory (`/tmp`) for the OCR staging
file.

Render's container disk is ephemeral, so a redeploy or restart deletes these
files, possibly before the retention window ends. The receipt then reports an
image as available while the image endpoint returns 404, and the review page
shows "image no longer available". Either accept that, since the extracted
values are stored in the database and the image is only a review aid, or mount a
Render disk at `UPLOAD_DIR` and run a single backend instance.

## Post-deploy verification

1. `GET https://<backend>/actuator/health` returns `{"status":"UP"}`.
2. Open the Vercel URL, register a user, and confirm the dashboard loads (proves
   `NEXT_PUBLIC_API_URL` and CORS are correct).
3. Trigger a password reset and confirm the email arrives (proves Brevo delivery).
4. If ml-service is deployed, call its `/health` from inside the private network:
   each capability lists whether it is enabled and which version is active. Then
   import a CSV and check that transactions show a model category source.

## Known limitations

- The high-quality OCR path (the `receipt-ocr` microservice) is not included in
  this Blueprint. With `RECEIPT_OCR_URL` empty, the backend uses ml-service OCR
  when enabled, and otherwise the in-image Tesseract engine.
- Receipt images are not in object storage. Only the local disk store exists.
  With more than one backend instance, a request served by another instance
  returns 404 for the image.
- Model quality was measured on synthetic data only. See the README for the
  numbers and what they do not show.
