# FlowSight

**Behavioral financial intelligence for people who already know their numbers.**

FlowSight turns raw transactions and receipts into a clear, honest read on how
money actually moves: recurring commitments, recoverable spending, behavioral
drift, and the long-term consequences of the decisions you repeat. It never
connects to a bank. You bring the data (CSV export, manual entry, or a receipt
photo); FlowSight does the thinking.

![FlowSight dashboard](screenshots/dashboard.png)

---

## Table of contents

- [Why this exists](#why-this-exists)
- [Key features](#key-features)
- [Machine learning and feature flags](#machine-learning-and-feature-flags)
- [Product walkthrough](#product-walkthrough)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Project structure](#project-structure)
- [Installation](#installation)
- [Environment variables](#environment-variables)
- [Running with Docker](#running-with-docker)
- [Desktop companion](#desktop-companion)
- [API overview](#api-overview)
- [Security](#security)
- [Deployment](#deployment)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Why this exists

Most expense trackers stop at categorization. They answer *what* you spent:

- How much went to food this month?
- What were my biggest expenses?

They rarely answer the questions that actually change behavior:

- Are my spending habits drifting upward over time?
- Which recurring commitments could I quietly retire?
- If I take this EMI, how much flexibility do I give up over the next ten years?
- What is that repeated Friday-night habit costing me per year?

The gap is **behavioral and consequential**, not transactional. FlowSight is
built to close it. Instead of a prettier ledger, it layers focused analytical
engines over a single normalized transaction record: behavioral signals,
recurring-payment detection, leak detection, decision simulation, and narrative
report generation. Findings are framed as observations, not judgments, and every
receipt-derived transaction is reviewed by the user before it is ever saved.

---

## Key features

Every item below is implemented in this repository.

### Authentication
- Email + password registration and login, JWT bearer auth (JJWT, HMAC-SHA;
  the recommended 64-byte secret yields HS512).
- **Short-lived access tokens (15 min) with rotating refresh tokens (14 days).**
  Refresh tokens are stored as SHA-256 hashes only, consumed on each use, and
  reusing a spent token revokes every live session for that user.
- Logout revokes the refresh token server-side.
- BCrypt (cost 12) password hashing; email normalization and duplicate detection.
- Account-enumeration suppression on login and password-reset flows.
- Password reset by email with pluggable delivery (Gmail SMTP, Brevo, Resend);
  single-use 30-minute tokens, hashed at rest.
- Stateless sessions; hydration-safe auth guard and single-flight token refresh
  on the web client.

### Transaction management
- CSV import driven by **header detection, not per-bank parsers**. Column aliases
  cover Date/Value Date, Narration/Particulars, Debit/Withdrawal/Money Out,
  Credit/Deposit/Money In, Dr/Cr type columns, and running balance.
- **Balance-only statements are reconstructed** from running-balance deltas when
  a file carries no amount column, with confidence scoring and warnings.
- Per-row error recovery: one malformed row never fails the batch, and a row
  whose amount cannot be resolved is reported as an error rather than silently
  coerced to zero.
- Manual entry with a live auto-categorization preview.
- 14 categories plus an explicit uncategorized state. Each transaction records
  where its category came from (your edit, a merchant rule you taught it, the
  model, or keyword rules), a confidence, and the top three alternatives.
- Edit merchant, category and notes in place, act on many rows at once
  (recategorize, mark reviewed, delete), and work through a "help categorize"
  queue that shows the least certain rows first.
- Full CRUD with per-user row isolation, pagination, and category/date filtering.

### Categorization
- A text classifier (TF-IDF features, logistic regression, numpy-only at
  inference) runs in ml-service and returns a calibrated probability per
  category. Keyword rules remain the fallback.
- A personal layer blends in the user's own reviewed transactions, so a
  correction changes later predictions for that user and nobody else. It is
  skipped when the user turns personalization off.
- On the 476-row evaluation set the rules score 0.4622 accuracy and the model
  0.9580. Read that with care: it is a synthetic set with merchants that overlap
  the training merchants. On the 125 rows with merchants the model had not seen
  it scores 0.896, and a held-out-merchant check on the training corpus gives
  0.667. Real statements will land lower than the headline.

### Merchant resolver
- One cascade turns messy descriptors into a canonical merchant: your own rules,
  an exact alias match, a trigram fuzzy match (`pg_trgm`), a nearest-alias
  lookup in ml-service, an optional LLM step, and finally a cleaned descriptor.
  The original text is kept alongside the resolved name.
- The alias table ships 383 entries across India, the US, the UAE and Saudi
  Arabia. UPI, IMPS and NEFT payees are never sent to an LLM, since they are
  often people.
- Correcting a merchant or category teaches a per-user rule. You can preview how
  many rows a rule would change before applying it. On the 352-row merchant set
  the resolved name matches exactly on 0.9602 of rows, and the old alias lookup
  went from 0.1392 to 0.9347. The regional aliases were written with that set in
  view, so this measures coverage rather than generalization.

### OCR receipt processing
- Image upload (JPEG / PNG / WebP / TIFF / BMP, 5 MB cap) with a per-user quota.
- Several fused passes: image preprocessing (orientation, background removal,
  deskew, contrast), up to three Tesseract passes, and an arithmetic
  reconciliation of subtotal, taxes, discounts, tip, round-off and payment
  lines against the total. The in-process **amount validator** still guards
  against taking a subtotal, VAT or GST line for the grand total.
- Per-field confidence and a decision for merchant, amount, date and currency:
  accept, accept with a highlight, or ask the user. The currency is detected
  rather than assumed to be INR, and a missing date stays empty instead of
  defaulting to today.
- An optional vision-model pass in ml-service reads the image with personal
  text blacked out, and any amount it returns must also appear in a Tesseract
  pass.
- **Review-first workflow**: OCR produces a draft only. No transaction is created
  until the user confirms. The review page shows the receipt image beside the
  fields, candidate totals as choices, line items and taxes as evidence, and
  records each correction as feedback. Confirming twice returns the same
  transaction.
- On the 200-receipt synthetic set the Tesseract path reads the total on 0.805
  of receipts (0.180 before this work), date 0.885, currency 1.000 and line items
  at F1 0.848. Arabic-Indic digit totals are 0.000, because the English
  Tesseract model cannot read them. These receipts are synthetic, not real
  photos.
- A compact, metadata-free review copy of the image is kept for 7 days for
  drafts and 24 hours after confirmation, then deleted.
- Falls back to the in-image Tesseract engine when ml-service and the external
  OCR microservice are unavailable or unconfigured.

### Behavioral insights
- Weekend overspend, lifestyle inflation, category drift, ticket size and
  frequency signals, each judged against the user's own history instead of a
  fixed threshold. A detector that does not have enough history yet is listed as
  needing more history rather than guessed.
- A behavioral profile plus recommendations ranked by saving and by how often
  you accept that kind of suggestion. Accept or dismiss a recommendation and it
  is remembered; dismissed ones stay hidden for 30 days.
- A long-view timeline showing what each recurring choice costs over 1, 5, and
  10 years, including opportunity cost.
- Optional LLM narratives and an "Ask FlowSight" assistant, off by default. See
  [Machine learning and feature flags](#machine-learning-and-feature-flags).

### Financial leak detection
- Surfaces duplicate subscriptions, price creep, silent drains, and bank fees.
- Each finding carries a severity tier, a recoverable-amount estimate and an
  evidence block naming the method, the window it read and the thresholds it
  applied.
- Price creep is found with a change-point test on 13 months of payments, and
  drains are judged against the user's own typical ticket and spend.

### Recurring detection
- Detects subscriptions and recurring bills across five cadence tiers
  (weekly to annual). A learned scorer in ml-service decides which merchant
  groups really recur and predicts the next charge with an interval; the older
  interval and variance rules are the fallback.
- Merchant names resolve through the merchant resolver, so
  `NETFLIX`, `Netflix.com` and `UPI/NETFLIX/MONTHLY` group as one merchant.
- Auto-flags cancellation candidates; users can confirm or dismiss, and
  confirmations survive future re-scans. Confirming or dismissing is recorded
  as feedback.
- On 120 synthetic users, group-level F1 was 0.7802 for the rules and 0.9982 for
  the model, and the share of irregular merchants wrongly stored as recurring
  fell from 0.5469 to 0.0018. Both the generator and the training data were
  written by the same author, so this shows the features separate synthetic
  processes, not real-world accuracy.

### Forecast
- Month-by-month projections of income, spend, net and each category, with 80%
  and 95% intervals and a note on which method produced them and how much
  history it had. Budgets use the same forecast to project month-end spend and
  support rollover.
- Against a flat-mean baseline on 160 synthetic users, weighted absolute
  percentage error improved from 0.338 to 0.281, and for budget month-end spend
  from 0.876 to 0.210. The 80% intervals covered 0.808 of outcomes. It was
  not better everywhere: with 0 to 3 months of history the net-flow error is
  worse than the flat mean.
- Falls back to an in-app forecaster with identical numbers when ml-service is
  off.

### Consequence simulation
- Models one-time purchases, recurring expenses, savings adjustments, and loan
  EMIs.
- Projects monthly, yearly, five-year, and ten-year impact against a real
  baseline (your last three months of transactions and active recurring patterns).
- A Monte Carlo run resamples your own month-to-month cash-flow swings to return
  a fan of outcomes (10th, 50th and 90th percentile), the probability of
  reaching an active goal by its date with and without the decision, and a
  flexibility range. The same request returns the same result.
- Returns a financial flexibility score before and after the decision.
- Opportunity cost uses an assumed annual return (8% by default, adjustable,
  and labelled as default or user-set in the response).

### Budgets and goals
- Per-category and overall budgets with spend-vs-limit summaries, forecast-based
  pace projection with intervals, rollover, and over-budget warnings.
- Savings goals with contributions, daily-pace guidance, and lifecycle states
  (active / complete / abandoned).

### Reports
- Monthly summary with category breakdown and tax-eligible deductions.
- Indian Income Tax section **80C / 80D / 80E** detection from transaction text,
  financial-year aware (April–March).
- Narrative "financial review" PDFs generated **asynchronously** (OpenPDF).
- CSV export for spreadsheets, accountants, and tax filing, with spreadsheet
  formula-injection neutralized in free-text columns.

### Desktop companion
- A Tauri 2 native app: a Raycast-style popup for quick capture and top signals.
- Widget and expanded window modes, density that adapts to the actual window
  width, global shortcut, system tray, hide-on-blur, native notifications,
  background sync.
- Reuses the backend, DTO types, and the web app's design system — no duplicated
  logic.

### Interface
- Keyboard command palette (⌘K / Ctrl+K) over every destination and quick action.
- Light and dark themes driven entirely by design tokens, including chart colors.
- Motion primitives that respect `prefers-reduced-motion`.
- Responsive from 320px up; the sidebar becomes an off-canvas drawer on mobile.

### Operations
- Flyway migrations, container health checks, and a `/actuator/health` endpoint.
- Append-only audit log written in a `REQUIRES_NEW` transaction so it survives
  caller rollback.
- Rate limiting backed by Postgres, so limits hold across instances and restarts.

---

## Machine learning and feature flags

The models live in a separate service, `ml-service` (FastAPI). The backend calls
it through a small client with a connect timeout, a per-call timeout and a
circuit breaker. Every model-backed feature sits behind a flag and has a
rule-based fallback that returns the answer the app gave before: if the flag is
off, the URL or token is unset, the call times out, the service returns an
error, or the response is malformed, the user gets the rule-based result. The
whole app works with ml-service absent.

Each capability has an `ML_<CAP>_ENABLED` flag and an `ML_<CAP>_SHADOW` flag.
In shadow mode the backend serves the rule-based answer and logs what the model
would have said, so a model can be compared before it is switched on. Set the
`ENABLED` and `SHADOW` flags on both the backend and ml-service. A capability
is served only when ml-service also has an active model version for it.

| Capability | Flag | Used for | Fallback |
|---|---|---|---|
| `categorizer` | `ML_CATEGORIZER_ENABLED` | Transaction categories with calibrated probabilities and alternatives | Keyword rules |
| `merchant-embeddings` | `ML_MERCHANT_EMBEDDINGS_ENABLED` | Nearest known merchant alias by character n-grams | Alias, trigram and cleaning steps |
| `recurring-scorer` | `ML_RECURRING_SCORER_ENABLED` | Which merchant groups recur, next date and interval | Interval and variance rules |
| `forecaster` | `ML_FORECASTER_ENABLED` | Forecast intervals for the forecast, budget pace and simulator | In-app forecaster (same numbers) |
| `ocr` | `ML_OCR_ENABLED` | Receipt reading: preprocessing, Tesseract passes, reconciliation | `receipt-ocr` service, then in-image Tesseract |
| `narrative` | `ML_NARRATIVE_ENABLED` | Declared, but nothing calls it yet | n/a |

The LLM features are separate from ml-service and from each other. They need a
model name and a key, because no model name is assumed, and each falls back to
deterministic text.

| Feature | Flag | Behavior when off or failing |
|---|---|---|
| Insight narratives, Ask FlowSight model path | `LLM_ENABLED` | Template text and keyword intents |
| Merchant naming for names nothing else resolved | `MERCHANT_LLM_ENABLED` | Cleaned descriptor |
| Vision pass in ml-service OCR | `ML_OCR_LLM_ENABLED` | Tesseract-only reading |

**Ask FlowSight** answers questions about the user's own data with seven
read-only tools (spending summary, spending by category, top merchants,
recurring payments, leaks, budget status, goal status). The user id always comes
from the authenticated session, never from the question or the model. Personal
identifiers are redacted before any text reaches an external model, and a model
answer is kept only if every number in it appears in the tool results it cites.
Write requests are refused. Without a model, keyword intents answer from the
same tools. The grounding validator and the intent matcher are scored on
synthetic cases that were written alongside the code, so those scores show no
regressions on known cases, not quality on real questions. The quality of live
model answers has not been measured.

**Privacy.** Corrections and recommendation answers are stored as feedback for
later use. Users can turn personalization off; the personal layer, learned
merchant rules and personalized ranking then stop using their history. Receipt
images and the vision pass are the one place an image leaves the backend, and
that pass is off unless you enable it.

**About the evaluation numbers.** The `eval/` directory holds the scoring
harness and baselines. The accuracy figures in this README come from those
runs, and all of them use synthetic data written by the project's author, so
treat them as upper bounds. No real statements or receipts were scored.

---

## Product walkthrough

```
Create account
      │
      ▼
Import transactions   ── CSV (header-detected) or manual entry
      │
      ▼
Upload receipts       ── image → fused OCR passes → reconciliation → per-field confidence
      │
      ▼
Review & confirm      ── editable draft; nothing saved until you approve
      │
      ▼
Analyze spending      ── category breakdown, cashflow, top merchants, alerts
      │
      ▼
Detect money drains   ── recurring commitments + leak detection (ranked)
      │
      ▼
View behavioral insights ── drift, concentration, curated observations
      │
      ▼
Simulate a decision   ── "what does this EMI cost me over 10 years?"
      │
      ▼
Generate a report     ── narrative PDF + tax-eligible deductions + CSV export
```

---

## Screenshots

### Transactions

CSV import, manual entry, and every transaction in one filterable table with its
source and confidence-scored category.

![Transactions](screenshots/transactions.png)

### Receipt OCR

Upload a photo against your remaining quota, then review an editable draft.
Nothing is saved until you confirm it.

![Receipt upload](screenshots/receipt-upload.png)

![Receipt review](screenshots/receipt-review.png)

### Financial overview

Spend against income over time, with projected months rendered as dashed
continuations.

![Analytics](screenshots/analytics.png)

### Behavioral insights

A spending profile, the patterns behind it, and recommendations ranked by what
they are actually worth.

![Insights](screenshots/insights.png)

The long view puts a number on what each recurring commitment costs over one,
five, and ten years, alongside the opportunity cost of not investing it.

![Long-term consequences](screenshots/insights-long-view.png)

### Recoverable spending

Duplicate subscriptions, silent price creep, high-frequency small spends, and
bank fees, each with the affected items expanded.

![Leak detection](screenshots/leaks.png)

### Recurring payments

Detected cadences with a confidence tier, next expected date, and status, split
into confirmed, detected, and possible.

![Recurring detection](screenshots/recurring.png)

### Decision simulator

Model a purchase, subscription, savings change, or loan against your real
baseline and see the flexibility shift before you commit.

![Decision simulator](screenshots/simulator.png)

![Simulator projection and tradeoffs](screenshots/simulator-projection.png)

### Budgets and goals

![Budgets](screenshots/budgets.png)

![Goals](screenshots/goals.png)

### Reports

Async narrative PDF generation, CSV export, and tax-eligible deduction detection.

![Reports](screenshots/reports.png)

![Tax-eligible deductions](screenshots/reports-tax.png)

![Generated PDF report](screenshots/report-pdf.png)

### Authentication

![Sign in](screenshots/auth-login.png)

![Create account](screenshots/auth-register.png)

---

## Architecture

### System architecture

```mermaid
flowchart TB
    subgraph Clients
        Web["Next.js 15 web app<br/>(app/ · features/ · components/)"]
        Desktop["Tauri 2 desktop companion<br/>(React 19 · shared design system)"]
    end

    subgraph Backend["Spring Boot 3.5 backend  (/api/v1)"]
        Ctrl["Controllers"]
        Svc["Services & analytical engines<br/>analytics · recurring · leaks · simulation · insights · reports"]
        Sec["Security<br/>JWT filter · refresh rotation · rate limiter · audit log"]
        Repo["Spring Data JPA repositories"]
    end

    DB[("PostgreSQL 16<br/>Flyway V1..V21")]
    ML["ml-service (FastAPI)<br/>categorizer · merchant embeddings · recurring scorer<br/>forecaster · OCR"]
    OCR["Receipt OCR microservice<br/>(optional second opinion)"]
    LLM["OpenAI-compatible LLM<br/>(optional, off by default)"]
    Mail["Email provider<br/>Gmail SMTP · Brevo · Resend"]

    Web -- "HTTPS / JWT" --> Ctrl
    Desktop -- "HTTPS / JWT" --> Ctrl
    Ctrl --> Sec --> Svc --> Repo --> DB
    Svc -- "bearer token, timeout, circuit breaker<br/>rule-based fallback" --> ML
    Svc -- "multipart upload" --> OCR
    Svc -- "redacted facts only" --> LLM
    Svc -- "password reset" --> Mail
```

ml-service is optional. The backend sends it work only when the matching flag is
on, and keeps the rule-based path as the fallback for every call.

### Data flow (ingestion)

```mermaid
flowchart LR
    A["CSV / manual / receipt"] --> B["Ingest"]
    B --> C["Resolve merchant<br/>rules · alias · fuzzy · cleaned"]
    C --> D["Categorize<br/>model + personal layer, rules fallback"]
    D --> E[("Transaction record")]
    E --> F["Analytics"]
    E --> G["Recurring detection"]
    E --> H["Leak detection"]
    E --> I["Simulation baseline"]
    E --> J["Insights & reports"]
```

Every analytical surface reads from the same normalized transaction record. There
is one source of truth, and no engine reaches back into raw bank infrastructure.

### CSV ingestion

```mermaid
flowchart TB
    F["Uploaded .csv"] --> V{"Validate<br/>size · extension · content type"}
    V --> H["StatementFormatDetector<br/>match header aliases"]
    H -- "amount / debit / credit column" --> R["Resolve amount<br/>column identity, then sign or type"]
    H -- "balance column only" --> B["BalanceDeltaCalculator<br/>reconstruct from running balance"]
    B --> R
    H -- "no usable headers" --> X["Reject with row errors"]
    R --> N["Normalize + categorize"]
    N --> T[("Transactions")]
```

### Authentication flow

```mermaid
sequenceDiagram
    participant C as Client
    participant API as AuthController
    participant S as AuthService
    participant DB as PostgreSQL
    C->>API: POST /api/v1/auth/login {email, password}
    API->>S: authenticate
    S->>DB: load user by normalized email
    S->>S: BCrypt verify (cost 12)
    S->>DB: store SHA-256 hash of new refresh token
    S-->>API: sign access token (15 min)
    API-->>C: { token, refreshToken }
    Note over C,API: requests send Authorization: Bearer <access token>
    C->>API: any request → 401 (access token expired)
    C->>API: POST /api/v1/auth/refresh {refreshToken}
    API->>DB: consume old token, issue rotated pair
    Note over API,DB: replaying a consumed token revokes every session
    API-->>C: { token, refreshToken }
```

### OCR pipeline

```mermaid
flowchart TB
    U["Upload image"] --> V{"Validate<br/>MIME · magic bytes · size · quota"}
    V -- "ok" --> ST["Stage original in a temp file"]
    ST --> X{"ml-service OCR<br/>enabled?"}
    X -- "yes" --> MLO["Preprocess · Tesseract passes<br/>optional vision pass"]
    X -- "no / failed" --> TESS["In-image Tesseract<br/>up to 3 passes<br/>(receipt-ocr cross-check if configured)"]
    MLO --> REC["Reconcile subtotal, taxes, tip,<br/>round-off and payment lines<br/>against the total"]
    TESS --> REC
    REC --> AV["Amount validator<br/>per-field confidence and decision"]
    AV --> KEEP["Keep compact review copy<br/>(no EXIF), delete the original"]
    KEEP --> R["Draft returned for review"]
    R --> CF{"User confirms?"}
    CF -- "yes" --> TX[("Transaction saved<br/>corrections stored as feedback")]
    CF -- "no / edit" --> R
```

### Deployment architecture

```mermaid
flowchart LR
    subgraph Vercel
        FE["Next.js frontend"]
    end
    subgraph Render
        BE["Spring Boot backend<br/>(Docker · /actuator/health)"]
        MLS["ml-service (optional)<br/>separate Docker service"]
        PG[("Managed PostgreSQL")]
    end
    Brevo["Brevo email API"]
    GHA["GitHub Actions<br/>keepalive ping"]
    FE -- "HTTPS / JWT" --> BE
    BE --> PG
    BE -- "private network, bearer token" --> MLS
    BE --> Brevo
    GHA -- "every 10 min" --> BE
```

### Core data relationships

```mermaid
erDiagram
    USER ||--o{ TRANSACTION : owns
    USER ||--o{ RECEIPT : uploads
    USER ||--o{ RECURRING_PATTERN : has
    USER ||--o{ BUDGET : sets
    USER ||--o{ FINANCIAL_GOAL : tracks
    USER ||--o{ REPORT_JOB : requests
    USER ||--o{ AUDIT_LOG : generates
    USER ||--o{ REFRESH_TOKEN : holds
    USER ||--o{ PASSWORD_RESET_TOKEN : requests
    RECEIPT ||--o| TRANSACTION : "confirmed into"
```

---

## Technology stack

### Frontend
| Layer | Choice |
|---|---|
| Framework | Next.js 15 (App Router) |
| Runtime | React 19 |
| Language | TypeScript 5 |
| Styling | Tailwind CSS 3.4 |
| State | Zustand 5 (with `persist`) |
| Forms | React Hook Form + Zod |
| Charts | Recharts 3 |
| Motion | Framer Motion 11 |
| UI primitives | Radix UI |
| Icons | Lucide |

### Backend
| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.5.16 |
| Language | Java 17 |
| Auth | JJWT 0.12.5 access tokens + Postgres-backed rotating refresh tokens |
| Password hashing | BCrypt cost 12 |
| PDF | OpenPDF 1.3.34 |
| CSV | Apache Commons CSV 1.10 |
| Email | Spring Mail (Gmail SMTP) / Brevo API / Resend API |
| Build | Maven |

### Database
| Layer | Choice |
|---|---|
| Engine | PostgreSQL 16 |
| Migrations | Flyway (V1..V21); fuzzy merchant matching needs the `pg_trgm` extension |
| Schema validation | Hibernate `ddl-auto: validate` — migrations are the only schema authority |
| Isolation | Per-user row ownership enforced in repository queries |

### ML service
| Layer | Choice |
|---|---|
| Framework | FastAPI on Python 3.12, one worker |
| Models | Categorizer (TF-IDF + logistic regression), merchant n-gram nearest neighbour, recurring scorer (logistic regression), forecaster priors; numpy at inference, no pickle |
| OCR | OpenCV preprocessing, Tesseract passes, reconciliation, optional vision pass |
| Model registry | Versioned artifacts on disk under `ML_MODEL_DIR`, with active and shadow status |
| Training | scikit-learn, kept out of the runtime image (`requirements-train.txt`) |
| Auth | Bearer token, constant-time compare, fails closed when no token is set |

### AI / OCR
| Layer | Choice |
|---|---|
| OCR service (optional) | FastAPI microservice ([bhimrazy/receipt-ocr](https://github.com/bhimrazy/receipt-ocr), pinned commit), now a second opinion that is cross-checked against Tesseract |
| Model | Vision LLM via Groq (OpenAI-compatible endpoint) |
| Default model | `meta-llama/llama-4-scout-17b-16e-instruct` |
| Fallback | ml-service OCR, then in-image Tesseract OCR |
| Validation | In-process amount validator and arithmetic reconciliation |
| Text LLM (optional) | Any OpenAI-compatible endpoint, for narratives, the assistant and rare merchant names; no model name is assumed |

### Desktop
| Layer | Choice |
|---|---|
| Shell | Tauri 2 (Rust) |
| UI | Vite + React 19, shared with the web frontend via an `@` alias |
| Native | Global shortcut, system tray, notifications, store-backed JWT, edge resize |

### Infrastructure & deployment
| Layer | Choice |
|---|---|
| Local orchestration | Docker Compose (`postgres`, `backend`, `frontend`, `receipt-ocr`, `ml-service`) |
| CI | GitHub Actions (backend build/test against Postgres, frontend type-check/lint/build, Docker image builds) |
| Frontend hosting | Vercel |
| Backend + DB hosting | Render (Blueprint via `render.yaml`); ml-service as its own Docker service |
| Ports (all bound to `127.0.0.1`) | 3007 web · 8080 API · 5433 db · 8001 OCR · 8002 ml-service · 1420 desktop dev |

---

## Project structure

```
flowsight/
├── backend/                         # Spring Boot API
│   ├── Dockerfile                   # Maven build → Debian JRE + Tesseract
│   └── src/
│       ├── main/java/com/flowsight/
│       │   ├── controller/          # HTTP entry points (/api/v1/*)
│       │   ├── service/             # Use-case orchestration + tax detection
│       │   ├── repository/          # Spring Data JPA
│       │   ├── entity/              # JPA entities + enums
│       │   ├── dto/                 # Request/response shapes
│       │   ├── analytics/           # Analytics, CSV ingestion, recurring, leaks
│       │   │   └── simulation/      # Decision simulator engine
│       │   ├── ocr/                 # OCR pipeline, parsers, reconciler, amount validator
│       │   ├── merchant/            # Merchant resolver cascade and per-user rules
│       │   ├── ml/                  # ml-service client, feedback, prediction log, forecasting
│       │   ├── llm/                 # Optional LLM client, grounding validator, redaction
│       │   ├── assistant/           # Ask FlowSight tools and intents
│       │   ├── insights/            # Recommendation ranking and feedback
│       │   ├── storage/             # Receipt review-image store and cleanup
│       │   ├── reports/             # PDF + intelligence report assembly
│       │   ├── email/               # Gmail / Brevo / Resend delivery
│       │   ├── security/            # JWT filter & service, rate limiter, IP resolver
│       │   ├── config/              # Security, CORS, async, startup env validation
│       │   ├── exception/           # Domain exceptions + global handler
│       │   └── util/                # Shared helpers
│       ├── main/resources/db/migration/   # Flyway V1..V21
│       └── test/java/com/flowsight/       # JUnit + Mockito suites
│
├── ml-service/                      # FastAPI model service (optional)
│   ├── Dockerfile                   # Python 3.12 + Tesseract
│   ├── app/                         # categorizer, merchant_embeddings, recurring_scorer,
│   │                                #   forecaster, ocr, model registry, auth
│   └── tests/
│
├── eval/                            # Scoring harness, synthetic generators, baselines
│
├── frontend/                        # Next.js 15 web app
│   ├── Dockerfile                   # standalone output on node:20-alpine
│   ├── app/
│   │   ├── page.tsx                 # Marketing landing page
│   │   ├── auth/                    # Login, register, forgot/reset password
│   │   ├── dashboard/               # Authenticated product surface
│   │   ├── privacy/  terms/         # Legal pages
│   │   └── globals.css              # Design tokens (light + dark)
│   ├── components/                  # layout (incl. ⌘K menu), motion, ui, legal
│   ├── features/                    # Per-domain API clients + types
│   ├── lib/                         # Fetch wrapper with refresh-and-retry
│   └── store/                       # Zustand auth store
│
├── desktop/                         # Tauri 2 desktop companion
│   ├── src/                         # React UI (reuses ../frontend via @ alias)
│   └── src-tauri/                   # Rust shell: window modes, tray, shortcut
│
├── screenshots/                     # Images used by this README
├── docker-compose.yml               # Local 5-service stack
├── render.yaml                      # Render Blueprint (backend + Postgres)
├── DEPLOYMENT.md                    # Vercel + Render deployment guide
└── .github/workflows/
    ├── ci.yml                       # Build, test, lint, Docker images
    └── keepalive.yml                # Pings the free-tier backend every 10 min
```

---

## Installation

### Prerequisites
- Docker Engine + Docker Compose (the only requirement for the full stack).
- A Groq API key if you want the high-quality vision-LLM receipt path.
- For the desktop companion only: Node 20+ and the Rust toolchain
  (see [Desktop companion](#desktop-companion)).

### 1. Clone

```bash
git clone <repository-url> flowsight
cd flowsight
```

### 2. Configure environment

```bash
cp .env.example .env
```

`POSTGRES_PASSWORD` and `JWT_SECRET` are mandatory — Compose refuses to start
without the first, and the backend fails fast at startup without the second.
Generate the secret with `openssl rand -base64 64`. Add `GROQ_API_KEY` if you
want LLM OCR. The local stack starts ml-service with its bundled baseline models
and turns the model features on; `ML_SERVICE_TOKEN` defaults to a local-only
value. See [Environment variables](#environment-variables) and
[Machine learning and feature flags](#machine-learning-and-feature-flags).

### 3. Start the stack

```bash
docker compose up -d
```

Flyway runs the database migrations automatically on backend startup.

| Service | URL |
|---|---|
| Frontend | http://localhost:3007 |
| Backend | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Database | localhost:5433 (Postgres) |
| OCR | http://localhost:8001 |
| ml-service | http://localhost:8002 (`/health` is open; everything else needs the token) |

### Running components individually

**Backend** (requires JDK 17 + a running Postgres):

```bash
cd backend
mvn spring-boot:run
```

**Frontend**:

```bash
cd frontend
npm install
npm run dev          # http://localhost:3007
npm run type-check
npm run lint
```

**Backend tests** (from the repository root):

```bash
cd backend && mvn verify
```

Without a local JDK, run the suite in a container:

```bash
docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn test
```

The unit suites need no database. `FlowsightApplicationTests.contextLoads` boots
the full Spring context and therefore expects a reachable Postgres — CI supplies
one as a service container.

---

## Environment variables

Backend variables are read by `docker-compose.yml`; the frontend reads only
`NEXT_PUBLIC_API_URL`. Never commit real secrets — `.env` is gitignored and only
`.env.example` is tracked.

| Variable | Required | Purpose | Default |
|---|---|---|---|
| `POSTGRES_PASSWORD` | Yes | Postgres password; Compose aborts if unset | — |
| `JWT_SECRET` | Yes | Base64 HMAC signing key, ≥256 bits, validated at startup | — |
| `JWT_EXPIRATION` | No | Access-token lifetime (ms) | `900000` (15 min) |
| `REFRESH_TOKEN_EXPIRATION` | No | Refresh-token lifetime (ms) | `1209600000` (14 days) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Yes\* | Backend JDBC connection | set by Compose |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed origins; no wildcard outside dev | web + Tauri origins |
| `TRUSTED_PROXY_COUNT` | No | Reverse-proxy hops used to pick the client IP from `X-Forwarded-For` | `1` |
| `NEXT_PUBLIC_API_URL` | Yes | Backend URL inlined into the browser bundle at build time | `http://localhost:8080` |
| `GROQ_API_KEY` | No\*\* | Vision-LLM key used by the OCR microservice | — |
| `RECEIPT_OCR_URL` | No | Internal OCR service URL; empty disables the LLM path | `http://receipt-ocr:8000` |
| `RECEIPT_OCR_MODEL` | No | Vision model id for the OCR microservice | `meta-llama/llama-4-scout-17b-16e-instruct` |
| `RECEIPT_DEFAULT_LIMIT` | No | Receipt quota granted to new users | `50` |
| `UPLOAD_DIR` | No | Receipt storage root | `/tmp/flowsight-receipts` |
| `EMAIL_PROVIDER` | No | `gmail` / `brevo` / `resend` | `gmail` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | No | Gmail SMTP delivery | `smtp.gmail.com` / `587` |
| `BREVO_API_KEY` / `BREVO_SENDER_EMAIL` / `BREVO_SENDER_NAME` | No | Brevo HTTPS email delivery | — |
| `RESEND_API_KEY` / `RESEND_FROM_EMAIL` / `RESEND_REPLY_TO` | No | Resend email delivery | — |
| `FRONTEND_URL` | No | Base URL used to build password-reset links | `http://localhost:3007` |
| `PASSWORD_RESET_EXPIRATION_MINUTES` | No | Reset-token lifetime | `30` |
| `PASSWORD_RESET_DEV_EXPOSE_LINK` | No | Dev only: returns the reset link in the API response | `false` |
| `PORT` | No | Backend listen port (set by the host on Render) | `8080` |

Machine learning, LLM and receipt-image variables (all optional; the backend
defaults leave every model feature off, while Compose turns the ml-service ones
on for local development):

| Variable | Where | Purpose | Default |
|---|---|---|---|
| `ML_SERVICE_URL` | backend | ml-service address; empty means never call it | empty (Compose: `http://ml-service:8000`) |
| `ML_SERVICE_TOKEN` | backend, ml-service | Shared bearer token; ml-service rejects everything but `/health` without it | empty (Compose: `local-dev-ml-token`) |
| `ML_<CAP>_ENABLED` | backend, ml-service | Serve a capability: `CATEGORIZER`, `MERCHANT_EMBEDDINGS`, `RECURRING_SCORER`, `FORECASTER`, `OCR`, `NARRATIVE` | `false` |
| `ML_<CAP>_SHADOW` | backend, ml-service | Serve rules, log the model's answer | `false` |
| `ML_<CAP>_TIMEOUT` | backend | Wait before falling back to rules | `400ms` (`60s` for OCR) |
| `ML_CONNECT_TIMEOUT` / `ML_CIRCUIT_FAILURE_THRESHOLD` / `ML_CIRCUIT_OPEN_DURATION` | backend | ml-service client limits | `250ms` / `5` / `30s` |
| `ML_MODEL_DIR` | ml-service | Model artifact directory | `/models` in the image |
| `ML_BOOTSTRAP` | ml-service | Register bundled baseline models for capabilities the registry has not seen | `true` |
| `ML_OCR_LLM_ENABLED` / `ML_OCR_LLM_MODEL` / `ML_OCR_LLM_URL` / `ML_OCR_LLM_API_KEY` / `ML_OCR_LLM_TIMEOUT` | ml-service | Optional vision pass for OCR; no model name is assumed | off / empty / Groq URL / `GROQ_API_KEY` / `20` s |
| `LLM_ENABLED` / `LLM_MODEL` / `LLM_BASE_URL` / `LLM_API_KEY` | backend | Narratives and the Ask FlowSight model path | `false` / empty / Groq URL / `GROQ_API_KEY` |
| `LLM_CONNECT_TIMEOUT_MS` / `LLM_TIMEOUT_MS` | backend | LLM request bounds | `2000` / `8000` |
| `ASSISTANT_RATE_LIMIT_PER_HOUR` | backend | Questions per user per hour | `30` |
| `MERCHANT_LLM_ENABLED` / `MERCHANT_LLM_MODEL` / `MERCHANT_LLM_URL` / `MERCHANT_LLM_TIMEOUT_MS` | backend | LLM step of the merchant resolver | `false` / empty / Groq URL / `3000` |
| `MERCHANT_EMBEDDING_MIN_SIMILARITY` | backend | Floor for an ml-service merchant neighbour | `0.75` |
| `GROQ_MERCHANT_MODEL` | backend | Preferred text models for ambiguous receipt merchants | `openai/gpt-oss-20b,llama-3.1-8b-instant` |
| `OCR_TESSERACT_LANGUAGES` | backend | Tesseract language packs in the backend image | `eng` |
| `RECEIPT_IMAGE_STORE` / `RECEIPT_IMAGE_MAX_LONG_SIDE` / `RECEIPT_IMAGE_JPEG_QUALITY` | backend | Review-image store, size and quality | `local` / `2000` / `0.8` |
| `RECEIPT_IMAGE_DRAFT_RETENTION` / `RECEIPT_IMAGE_REVIEWED_RETENTION` / `RECEIPT_IMAGE_CLEANUP_INTERVAL` | backend | How long review images are kept | `P7D` / `PT24H` / `PT1H` |

\* Provided automatically by Docker Compose in local development.
\*\* Without it, the OCR microservice cannot reach Groq and the backend falls back
to the in-image Tesseract engine. Likewise, with no email credentials configured
the reset link is written to the logs instead of being sent.

---

## Running with Docker

```bash
# Start (detached)
docker compose up -d

# Rebuild after code changes
docker compose up -d --build

# Follow logs
docker compose logs -f backend

# Stop
docker compose down

# Stop and wipe the database volume
docker compose down -v
```

All published ports bind to `127.0.0.1`, so nothing in the stack is reachable
from the LAN.

**Troubleshooting**

- **Compose refuses to start** — `POSTGRES_PASSWORD` is not set in `.env`. It has
  no default on purpose.
- **Backend exits at startup** — `JWT_SECRET` is missing, not Base64, or under
  256 bits. `SecurityEnvironmentValidator` fails fast rather than minting weak
  tokens.
- **Backend won't connect to the DB** — Postgres must be healthy first; Compose
  gates the backend on `pg_isready`. Check `docker compose logs postgres`.
- **OCR service slow to become healthy** — it has a 90s `start_period`; the first
  build clones the pinned upstream commit from GitHub.
- **Frontend calls `localhost:8080` in production** — `NEXT_PUBLIC_API_URL` is
  inlined at build time. Set it before building and redeploy after changes.
- **CORS errors** — the requesting origin must be in `CORS_ALLOWED_ORIGINS`.

---

## Desktop companion

The `desktop/` app is a Tauri 2 shell that reuses the web frontend's components,
DTO types, and design tokens (via an `@` alias pointing at `../frontend`). The
backend stays the single source of truth; only two additive backend changes exist
for it (a current-flexibility endpoint and the Tauri CORS origins).

```bash
cd desktop
npm install
npm run tauri:dev        # native window with hot reload
npm run tauri:build      # per-OS installers in src-tauri/target/release/bundle
```

Requires the Rust toolchain (and, on Linux, WebKitGTK dev libraries — see
`desktop/README.md`). The backend must be running; the API base defaults to
`http://localhost:8080` (override with `VITE_API_URL`). The backend CORS
allow-list already includes the Tauri origins. To validate the UI without the
native toolchain: `npm run type-check && npm run build`.

---

## API overview

All endpoints are under `/api/v1`. Authentication is a `Bearer` access token
except where noted. `admin/**` and `actuator/**` (other than `health`) require
the `ADMIN` role.

| Group | Base path | Highlights |
|---|---|---|
| Auth | `/auth` | `register`, `login`, `refresh`, `logout`, `forgot-password`, `reset-password` (public); `me` |
| Transactions | `/transactions` | CRUD, `POST /import/csv` bulk import, `GET /review-queue` |
| Merchant rules | `/merchant-rules` | List, delete, `POST /{id}/preview`, `POST /{id}/apply` |
| Receipts | `/receipts` | `POST /upload`, list, `GET /{id}/image`, `POST /{id}/confirm`, delete |
| Analytics | `/analytics` | `overview`, `trend`, `activity-bounds` |
| Forecast | `/forecast` | `GET ?months=1..6` with intervals per series |
| Insights | `/insights` | Behavioral profile, patterns, recommendations, consequences; `POST /recommendations/{id}/accept` and `/dismiss` |
| Assistant | `/assistant` | `POST /ask`, grounded answers with citations (rate limited) |
| Leaks | `/leaks` | Ranked leak findings with recoverable amounts and evidence |
| Recurring | `/recurring` | List, `POST /detect`, `confirm`, `unconfirm`, dismiss, `restore` |
| Simulation | `/simulate` | `POST` scenario projection with Monte Carlo fan, `GET /flexibility` |
| Budgets | `/budgets` | Per-category and overall budgets with summaries |
| Goals | `/goals` | Savings goals, `contribute`, `complete`, `abandon` |
| Reports | `/reports` | `transactions.csv`, `monthly`, `tax-summary` |
| Intelligence reports | `/intelligence-reports` | Async narrative PDF generation + `download` |
| Account | `/account` | Profile, receipt quota, `audit-log` |
| Admin | `/admin/quota` | Quota administration (ADMIN only) |

---

## Security

- **Authentication** — 15-minute access tokens plus rotating refresh tokens.
  Refresh tokens are stored as SHA-256 hashes, single-use, revoked on logout,
  and replaying a consumed token revokes every live session for that user.
- **Passwords** — BCrypt cost 12; account-enumeration suppression on login and
  password reset; reset tokens are hashed, single-use, and expire in 30 minutes.
- **Authorization** — every by-id lookup is scoped with `findByIdAndUserId`, so a
  cross-user id resolves to 404 rather than leaking data. Role check on
  `admin/**` and non-health actuator endpoints.
- **DTO isolation** — controllers return response DTOs; entities never cross the
  service boundary.
- **Input validation** — Bean Validation on every request DTO, bounded numeric
  and pagination parameters, and explicit 400 handlers for type-mismatch,
  missing-parameter, and malformed-JSON failures.
- **Startup validation** — `SecurityEnvironmentValidator` fails fast on a
  missing, non-Base64, or undersized JWT secret, and on a blank or wildcard CORS
  list outside dev profiles.
- **File uploads** — CSV is gated on size, extension, and content type; receipt
  images are additionally checked by magic bytes so a spoofed `Content-Type`
  cannot smuggle a non-image to the OCR pipeline. Storage paths are built from
  server-generated UUIDs, never the client filename.
- **CSV export** — leading `=`, `+`, `-`, `@`, tab, and CR in free-text columns
  are neutralized so exports cannot execute as spreadsheet formulas (CWE-1236).
- **Rate limiting** — fixed-window counters in Postgres, shared across instances
  and surviving restarts: auth 5/min/IP, uploads 30/hour/user, password-reset
  requests 5/hour/IP, reset confirmations 10/hour/IP, assistant questions
  30/hour/user, review images 60/min/user, simulations 120/min/user.
- **ml-service**: bearer token with constant-time comparison that fails closed
  when unset, model files loaded without pickle, an image-size cap before
  decoding uploads, and no public route other than `/health`. Production must
  set a real `ML_SERVICE_TOKEN` and keep the port private.
- **Privacy**: personal identifiers are redacted before text goes to an
  external LLM, LLM answers must cite tool results and contain only numbers from
  them, and receipt images are re-encoded without EXIF and deleted on a schedule.
- **HTTP hardening** — `Content-Security-Policy: default-src 'none'`,
  `frame-ancestors 'none'`, HSTS over HTTPS, `X-Frame-Options: DENY`,
  `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`.
- **Error handling** — standardized `ApiError` responses with stack traces and
  exception details suppressed; actuator health shows no details.
- **Audit log** — append-only, written with `REQUIRES_NEW` propagation so entries
  survive caller rollback.

**Known limitation**: the web client stores its tokens in `localStorage`, which
is XSS-exposable. The short access-token lifetime limits the blast radius;
moving to httpOnly cookies requires CSRF handling and is tracked in the roadmap.

---

## Deployment

The frontend deploys to Vercel and the backend plus database to Render via the
`render.yaml` Blueprint. `DEPLOYMENT.md` has the full procedure, the exact
environment variables to set in each dashboard, and post-deploy verification
steps.

Deployment notes worth knowing up front:

- Render blocks outbound SMTP on entry tiers, so production email uses the Brevo
  HTTPS API (`EMAIL_PROVIDER=brevo`). Gmail SMTP works locally.
- ml-service deploys as its own Docker service and is optional. The backend
  works without it, and every model feature is off until its flag is set.
- Migrations V15 to V21 are additive, and V16 needs the `pg_trgm` extension.
- Receipt images are a short-lived review copy on the container filesystem,
  which is ephemeral on Render. A redeploy can remove them early; the review
  page then says the image is no longer available.

---

## Roadmap

**Completed**
- Authentication, refresh-token rotation and revocation, password reset
- CSV + manual transaction ingestion with header-driven column mapping and
  balance reconstruction
- Receipt OCR with fused passes, reconciliation, per-field confidence and
  review-first confirmation
- Model-backed categorization with a personal layer, merchant resolver with
  per-user rules, and a learned recurring-payment scorer, all behind flags with
  rule-based fallbacks
- Forecast with intervals, forecast-based budget pace, Monte Carlo decision
  simulator
- Analytics, behavioral insights, leak detection with evidence
- Optional grounded narratives and the Ask FlowSight assistant
- Budgets and goals
- Narrative PDF reports, tax-deduction surfacing (80C/80D/80E), CSV export
- Receipt quota, audit log, shared Postgres-backed rate limiting
- Security hardening: HTTP headers, startup env validation, upload sniffing,
  CSV formula-injection neutralization
- Tauri 2 desktop companion with widget and expanded window modes
- Command palette, light/dark token system, responsive layout down to 320px

**In progress**
- Broader bank statement header coverage
- Rolling the AI-signals design language across the remaining dashboard surfaces
- Measuring the models on real statements and receipts; every figure so far is
  synthetic
- A simulator fan chart and a screen to manage merchant rules in the web app
  (the API and types exist; the fan chart is not rendered yet)

**Planned**
- Move tokens from `localStorage` to httpOnly cookies with CSRF handling
- Cloud receipt storage with signed URLs (review images are on local disk, which
  is ephemeral on Render)
- Retraining from stored user feedback (it is recorded, but no job uses it yet)
- Bank SMS ingestion (the `SMS` transaction source exists; parsing is not built)
- Operational monitoring (metrics, traces, structured logs)
- Multi-account households

---

## Contributing

Contributions are welcome. Open an issue to discuss substantial changes before
submitting a pull request. Match the existing code style, add tests where logic
is non-trivial (the backend suite runs via `mvn verify`), and keep frontend
changes consistent with the design token, motion, and signal systems already in
place. CI runs backend build/test against a live Postgres, frontend
type-check/lint/build, and Docker image builds on every push and PR.

---

## License

MIT. See [LICENSE](LICENSE).

---

Built with Next.js, React, Spring Boot, PostgreSQL, Tauri, and a Groq vision LLM.
