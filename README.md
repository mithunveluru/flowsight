# FlowSight

A behavioral financial intelligence platform for individuals.

FlowSight turns everyday transactions and receipts into a clear picture of how money actually moves. It surfaces recurring commitments, recoverable spending, behavioral patterns, and the long-term consequences of recurring decisions, without ever connecting to a bank.

---

## Overview

Most expense trackers tell you *what* you spent. FlowSight tries to explain *why* it matters.

The product ingests transactions (CSV or manual entry) and receipts (image upload with structured extraction), then layers on a set of analytical engines: behavioral signals, recurring pattern detection, leak detection, decision simulation, and narrative report generation.

It is built for individuals who already know their numbers and want a calmer, more honest read on the patterns behind them.

## Problem Statement

Traditional expense trackers stop at categorization. They answer:

- How much did I spend on food this month?
- What were my biggest expenses?

They rarely answer:

- Are my spending habits drifting upward over time?
- Which recurring commitments could I quietly retire?
- If I take this EMI, how much flexibility do I give up over the next five years?
- What does my behavior on Friday nights cost me annually?

The gap is behavioral and consequential, not transactional. FlowSight focuses on closing it.

## Solution

FlowSight is composed of a small set of focused engines:

- **Transaction ingestion**: CSV (HDFC, SBI, generic), manual entry, with normalization and confidence-scored categorization.
- **Receipt analysis**: image upload, structured extraction via a vision LLM, amount validation that recognises subtotals and taxes, review-first confirmation before any transaction is saved.
- **Behavioral analytics**: weekend overspend, lifestyle inflation, category concentration, ticket size, frequency.
- **Recurring detection**: identifies subscriptions and recurring bills, surfaces cancellation candidates.
- **Leak detection**: duplicate services, price creep, silent drains, and bank fees, each with a confidence score.
- **Decision simulator**: models the cashflow, flexibility, and ten-year opportunity cost of a hypothetical decision against a real baseline.
- **Financial review reports**: narrative PDFs covering patterns, commitments, recoverable spend, consequences, and prioritized recommendations.

## Key Features

### Financial Tracking
- Multi-format CSV import with per-row error recovery
- Manual entry with auto-categorization preview
- Receipt-driven entry with editable extraction and review-first confirmation
- Auditable, user-isolated data with row-level filtering

### Receipt Intelligence
- Image upload (JPEG / PNG / WebP / TIFF, 5 MB cap)
- Structured extraction (merchant, amount, date, line items)
- Amount validator that detects sub-total / VAT misidentification
- Confidence tiering with explicit review prompts when needed

### Behavioral Analytics
- Spend by category, monthly cashflow, top merchants
- Weekend overspend, lifestyle inflation, category concentration
- Curated observations rather than generic alerts

### Financial Leak Detection
- Duplicate subscriptions, price creep, silent drains, bank fees
- Confidence-scored, ranked by recoverable amount

### Simulation Engine
- One-time purchase, EMI, recurring expense, and income change scenarios
- Projected monthly, yearly, and ten-year impact
- Financial flexibility score before and after the decision
- Future-value-of-annuity at an 8 percent assumed return

### Reports
- Monthly summary with category breakdown and tax-eligible deductions
- Indian Income Tax section 80C / 80D / 80E detection from transaction text
- Narrative review PDFs generated asynchronously
- CSV export for spreadsheets, accountants, and tax filing

## Product Screens

Screenshots can live under `docs/screens/` and be linked here:

- Dashboard
- Receipt scan and review
- Financial overview (analytics)
- Observations (behavioral insights)
- Decision simulator
- Financial review report

## Architecture

```
        ┌─────────────────────────────────────────────────────────────┐
        │                         Next.js Frontend                     │
        │      app/  features/  components/  motion primitives         │
        └────────────────────────────┬────────────────────────────────┘
                                     │ HTTPS / JWT
                                     ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                    Spring Boot Backend                       │
        │                                                              │
        │   ingest → normalize → categorize → store                    │
        │                              │                               │
        │                              ▼                               │
        │   analytics │ recurring │ leaks │ simulation │ insights      │
        │                              │                               │
        │                              ▼                               │
        │           reports (PDF + tax detection) │ audit log          │
        └────────┬───────────────────────────────────────┬────────────┘
                 │                                       │
                 ▼                                       ▼
        ┌──────────────────┐                ┌────────────────────────┐
        │  PostgreSQL 16   │                │ Receipt OCR (FastAPI)  │
        │  Flyway V1..V11  │                │ Vision LLM via Groq    │
        └──────────────────┘                └────────────────────────┘
```

The pipeline composes around a single transaction record. Every analytical surface (analytics, recurring, leaks, simulation, insights, reports) reads from that record set, never from raw bank infrastructure.

## Technology Stack

### Frontend
| Layer | Choice |
|---|---|
| Framework | Next.js 15 (App Router) |
| Language | TypeScript |
| Styling | Tailwind CSS 3 |
| State | Zustand (with `persist`) |
| Forms | React Hook Form + Zod |
| Charts | Recharts 3 |
| Motion | Framer Motion 11 |
| UI primitives | Radix UI |

### Backend
| Layer | Choice |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 17 |
| Auth | JWT (JJWT 0.12, HMAC-SHA512) |
| Password hashing | BCrypt cost 12 |
| Mapping | MapStruct |
| PDF | OpenPDF 1.3.34 |
| Build | Maven |

### Database
| Layer | Choice |
|---|---|
| Engine | PostgreSQL 16 |
| Migrations | Flyway (V1..V11) |
| Isolation | Per-user row ownership; DTO boundaries between layers |

### OCR
| Layer | Choice |
|---|---|
| Service | FastAPI microservice (bhimrazy/receipt-ocr) |
| Model | Vision LLM via Groq, OpenAI-compatible endpoint |
| Default model | `meta-llama/llama-4-scout-17b-16e-instruct` |
| Validator | In-process amount validator with line-item cross-check |

### DevOps
| Layer | Choice |
|---|---|
| Orchestration | Docker Compose |
| Services | `postgres`, `backend`, `frontend`, `receipt-ocr` |
| Ports | 5433 (db), 8080 (api), 3007 (web), 8001 (ocr) |

## Project Structure

```
flowsight/
├── backend/
│   └── src/main/java/com/flowsight/
│       ├── controller/    # HTTP entry points
│       ├── service/       # Use-case orchestration
│       ├── repository/    # Spring Data JPA
│       ├── entity/        # JPA entities
│       ├── dto/           # Request and response shapes
│       ├── mapper/        # MapStruct converters
│       ├── analytics/     # Behavioral analytics engine
│       ├── insights/      # Observation and recommendation generation
│       ├── ocr/           # Receipt OCR client and validators
│       ├── reports/       # PDF report assembly
│       ├── simulation/    # Decision simulator
│       ├── security/      # JWT filter, security config
│       ├── config/        # CORS, beans, rate limiter
│       └── exception/     # Domain exceptions
├── frontend/
│   ├── app/
│   │   ├── auth/          # Login and signup experience
│   │   └── dashboard/     # Authenticated product surface
│   ├── components/
│   │   ├── layout/        # Shell, sidebar, header
│   │   ├── motion/        # Reusable motion primitives
│   │   ├── ui/            # Radix-based primitives
│   │   └── providers.tsx  # App-level providers
│   └── features/
│       ├── account/  analytics/  auth/  budgets/  goals/
│       ├── insights/  leaks/  receipts/  recurring/
│       ├── reports/  simulation/  transactions/
└── docker-compose.yml
```

## Core Capabilities

### OCR Pipeline
1. File received by the backend, persisted under a per-user storage path.
2. Multipart upload to the OCR microservice, which calls a vision LLM for structured extraction.
3. Amount validator scans line items, identifies misclassified subtotals or taxes, assigns a confidence tier.
4. Result returned to the user for review; transactions are saved only after explicit confirmation.

### Behavioral Intelligence
Analytical signals derived from transaction history: weekend overspend, lifestyle inflation, category concentration, ticket size, frequency. Results are framed as observations, not judgments.

### Recurring Payment Detection
Pattern detection over transaction history. Patterns can be confirmed or dismissed by the user; confirmations sharpen future detection.

### Financial Leak Detection
Identifies duplicate services, price creep, silent drains, and bank fees. Each finding carries a confidence score and a recoverable-amount estimate.

### Consequence Simulation
A scenario is evaluated against the user's last three months of transactions and active recurring patterns. The simulator returns monthly, yearly, five-year, and ten-year impact, alongside a financial flexibility delta. The ten-year opportunity cost assumes an 8 percent annual return (future value of an annuity).

### Report Generation
Reports are produced asynchronously. The status surface rotates through analysis steps so users see intentional progress rather than a spinner. The output is a multi-section PDF covering patterns, commitments, recoverable spend, consequences, and prioritized next steps.

## Local Development Setup

### Prerequisites
- Docker Engine and Docker Compose
- A Groq API key (for receipt analysis)

### One-step bootstrap
```bash
cp .env.example .env   # fill in JWT_SECRET and GROQ_API_KEY
docker compose up -d
```

The stack will be reachable at:

| Service | URL |
|---|---|
| Frontend | http://localhost:3007 |
| Backend  | http://localhost:8080 |
| Database | localhost:5433 (Postgres) |
| OCR      | http://localhost:8001 |

### Backend only
```bash
cd backend
./mvnw spring-boot:run
```

### Frontend only
```bash
cd frontend
npm install
npm run dev
```

### Database
Flyway migrations run automatically on backend startup. Migration files live under `backend/src/main/resources/db/migration/`.

## Environment Variables

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC signing key for JWT tokens (required) |
| `JWT_EXPIRATION` | Token lifetime in milliseconds (default 86400000) |
| `POSTGRES_PASSWORD` | Database password |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Backend connection (compose-provided) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins (default `http://localhost:3007`) |
| `GROQ_API_KEY` | Required for receipt analysis |
| `RECEIPT_OCR_URL` | Internal URL of the OCR microservice (default `http://receipt-ocr:8000`) |
| `RECEIPT_OCR_MODEL` | Vision model identifier (default `meta-llama/llama-4-scout-17b-16e-instruct`) |
| `NEXT_PUBLIC_API_URL` | Backend URL exposed to the browser |

## Security Considerations

- **Authentication**: JWT with HMAC-SHA512, stateless sessions, hydration-safe client guard.
- **Passwords**: BCrypt cost 12, with email enumeration suppression on login failure.
- **Authorization**: per-user row ownership at the repository layer; entities never returned directly through controllers.
- **Validation**: Bean Validation on every request DTO; field-level error mapping back to the frontend.
- **DTO isolation**: entities live behind MapStruct converters; raw entity graphs are never exposed.
- **File uploads**: size and content-type validated; per-user storage paths; OCR service runs in an isolated container.
- **Rate limiting**: in-process token bucket on sensitive endpoints (login, receipt upload).
- **Audit log**: append-only, `REQUIRES_NEW` transaction propagation so audit writes survive caller rollback.

## Current Status

**Implemented**
- Authentication and user management
- CSV and manual transaction ingestion with categorization
- Receipt analysis with review-first confirmation and amount validation
- Analytics, observations, recurring detection, leak detection
- Decision simulator and intelligence-style PDF reports
- Tax-eligible deduction surfacing (80C / 80D / 80E)
- Receipt processing quota and rate limiting
- Audit log
- Refined motion design and section-aware visual system

**In progress**
- Mobile companion app
- Additional bank statement formats
- Bank SMS ingestion pipeline

## Roadmap

- Mobile companion (React Native)
- Bank SMS ingestion at scale
- Additional statement formats (international banks)
- Cloud receipt storage with signed URLs
- Operational monitoring (metrics, traces, structured logs)
- Multi-account households

## Contributing

Contributions are welcome. Open an issue to discuss substantial changes before submitting a pull request. Match the existing code style, write tests where logic is non-trivial, and keep frontend changes consistent with the motion and color systems already in place.

## License

MIT
