# Eval harness

Measures the receipt OCR path, transaction categorization and merchant normalization with
the real backend code, and fails when a change makes any of them worse than the stored
baseline.

## Run

```
eval/run.sh                    # offline: scorer tests, Tesseract OCR, categorization, merchants, recurring
eval/run.sh --update-baseline  # same, then rewrite eval/baselines/*.json from this run
```

Requires Docker only. Everything runs in the image built from `eval/Dockerfile` (base image
pinned by digest, apt packages pinned to an Ubuntu archive snapshot), so local and CI numbers
are identical. Maven dependencies are cached in `~/.m2` (override with `EVAL_M2`). A run takes
about 1.5 minutes on 8 cores; outputs land in `eval/out/` (ignored by git).

Exit status is non-zero when a metric regresses, when the generated receipts no longer match
`eval/receipts/truth.jsonl`, or when a baseline is missing.

The other modes call a running service and score synthetic receipts only:

```
RECEIPT_OCR_URL=http://127.0.0.1:8001 GROQ_API_KEY=... eval/run.sh --mode llm
ML_SERVICE_URL=http://127.0.0.1:8000 ML_SERVICE_TOKEN=... eval/run.sh --mode ml-service
```

`llm` calls the receipt-ocr vision service exactly as the backend does (its answer is
cross-checked by the Tesseract pipeline). `EVAL_DELAY_MS` (default 2000) spaces out requests
for the provider's rate limit. `ml-service` calls the `ocr` capability (start ml-service with
`ML_OCR_ENABLED=true`).

## What is measured

| Suite | Code under test | Data |
|---|---|---|
| `ocr-tesseract`, `ocr-llm`, `ocr-ml-service` | `CurrentReceiptPipeline`: `ReceiptExtractionService` as `ReceiptService` calls it, without the database (ml-service OCR, else receipt-ocr cross-checked by Tesseract, else the multi-pass `TesseractReceiptPipeline`) | 200 synthetic receipts from `receipts/generate.py`, plus private real receipts if present |
| `categorization` | the ml-service categorizer artifact (`ml-service/app/categorizer/artifact`, numpy only) on the items the backend sends it: normalized description and resolved merchant, no direction | `categorization/cases.csv` (476 rows, India / KSA / UAE / US) |
| `categorization-rules` | `CategorizationService.categorize` (the fallback) with the inputs `TransactionIngestionPipeline.processCsvRow` gives it | same |
| `categorization-personal` | categorizer + `PersonalBlend`; each region is one user split in two halves by resolved merchant, the other half is their reviewed history, so no row sees its own merchant's label | same |
| `categorization-personal-repeat` | as above, split by row id: the user has already corrected other rows of the same merchant | same |
| `merchants` | `MerchantNormalizationService.normalize`, `MerchantText.clean` and `MerchantResolver` (offline) | `merchants/cases.csv` (352 rows) |
| `recurring-rules` | `RecurringDetectionService.detect` and `LeakDetectionService.analyze` (the rules, the fallback) as of each user's date | `recurring/generate.py`: 120 synthetic users (30 each IN/US/AE/SA, 3-18 months, about 55k debits) |
| `recurring-model` | the ml-service recurring-scorer artifact (`ml-service/app/recurring_scorer/artifact`, numpy only, via `recurring/predict_model.py`) on the requests `RecurringDetectionService.scorerRequest` builds, mapped by `fromScores`, then `LeakDetectionService.analyzeWithModel` with each user's personal baseline | same |
| `forecast-current` | the trend forecast as `AnalyticsService` shipped it: mean of the last three trend points (current partial month included), per series | `forecast/generate.py`: 160 seeded synthetic users, rolling origins |
| `budget-pace-current` | the month-end projection as `BudgetService` shipped it: spend so far * days in month / days elapsed | same |

To score a new receipt pipeline, change `CurrentReceiptPipeline` only; the runner, data and
scorer stay the same.

Receipt metrics (fraction of receipts unless noted):

- `merchant_exact`: prediction equals the canonical merchant after case, punctuation and `&`
  folding.
- `merchant_fuzzy`: `difflib` similarity of at least 0.85 to the canonical or the printed name,
  after also dropping store numbers and legal words (Pvt, Ltd, LLC, ...).
- `total`: equal to the cent. `date`: same calendar date. `currency`: same ISO code.
- `line_item_f1`: micro F1 over items; a pair matches when names are at least 0.80 similar and
  the price equals the line amount or unit price.
- `ece`: expected calibration error of `confidence` against total correctness, 10 equal-width
  bins, with a reliability table. `n/a` when the pipeline returns no confidence.

Slices: currency, number format (`us` 1,234.50, `eu` 1.234,50, `indian` 1,23,456.00,
`arabic_indic` ١٬٢٣٤٫٥٠), perturbation (`clean`, `background`, `rotation`, `perspective`,
`blur`, `glare`, `crumple`, `low_contrast`) and trap (header above the merchant, merchant
names containing label words, Total Items / Tendered / Cash / Change lines, tip after total).
`clean`, `blur`, `glare`, `crumple` and `low_contrast` are cropped receipts; the other three
are photos with the table visible around the paper.

Categorization: accuracy, macro F1 over the labeled classes, ECE of the returned confidence.
The categorizer is trained only on the synthetic corpus in `ml-service/app/categorizer/corpus.py`;
this set is never used for training.
Merchants: `alias_exact` (canonical name correct), `alias_wrong` (an alias fired and named the
wrong merchant, lower is better), `fuzzy` (canonical name, or the grouping key when no alias
matched, at least 0.85 similar), `key_match` (raw and expected strings get the same grouping
key), `extract_exact` / `extract_fuzzy` for the descriptor cleaner `MerchantText.clean`, and
`resolved_exact` / `resolved_fuzzy` for the name `MerchantResolver` stores on a transaction
(built-in aliases, in-memory trigram matching, no user rules, ML or LLM steps).

Recurring and leaks: the histories are regenerated on every run and checked against
`recurring/cases.sha256`. Each merchant group has a truth record (recurring obligation or
not, period, parallel charges, the first charge after the as-of date, price step, habit).
A stored pattern is matched to the group that owns most of its key's transactions.
`group_*` count stored patterns against recurring groups with at least two charges;
`period_accuracy` and `next_within_3d` (next expected date within 3 days of the real next
charge) are over matched groups; `irregular_fp_rate` (lower is better) is the share of
frequent irregular merchants (food delivery, coffee, groceries, fuel, rides) stored as
recurring. Leaks: `creep_*` against price steps (2-4 payments after the step), `dup_*`
against one service billed on two accounts, `overlap_*` against the leak's own definition
applied to the truth (recurring subscriptions in the category that has the most of them),
`drain_*` against coffee habits with at least 8 visits in the 3-month window (that last
definition is a judgment, read it as indicative: the model path's personal baseline also
flags frequent food delivery, which this truth does not call a drain). The scorer is trained
only on its own corpus (`ml-service/app/recurring_scorer/corpus.py`), never on these users,
but both generators were written by the same hand, so expect real histories to score lower. Slices: region, history length (<= 6
months is short), recall per recurring kind and false-positive rate per negative kind. The
info table also has the share shown as detected (confidence >= 0.5), next-date MAE and,
where a pattern has one, the coverage and width of its next-date interval.
Forecasts: `ForecastEvalTest` replays each synthetic user's history. At every origin (one seeded
day per month, written by the generator) only transactions up to that day are visible; the
forecast for each of the next three calendar months is scored against what happened, for income,
spend, net and every spending category the user has had. The budget pace suites score the
projected month-end spend of the origin month (overall and per category). Users mix salaried,
freelance and gig income, rent/EMI/subscriptions, festival, summer and school-fee seasonality,
lifestyle drift and shocks (job loss, medical bills, bonuses, one-off purchases); a fifth of them
have under five months of history, so every user also contributes short-history origins.

- `wape`: sum of absolute errors over sum of actuals, for income, spend and categories.
- `mape`: mean absolute percentage error of the income and spend totals (months with a positive actual).
- `net_smae`: absolute error of net cash flow over the month's income plus spend.
- `cov80`, `cov95`: share of actuals inside the 80% / 95% prediction intervals; `cov80_gap` and
  `cov95_gap` are the distance to nominal (gated, lower is better). `n/a` without intervals.

Slices: history at the origin (`history=0`, `1-3`, `4-11`, `12+` complete months), series kind,
horizon `h`, and user tags (`seasonal`, `shock`, `volatile`, `drift`).

## Regression gate

A run fails when an overall metric drops by more than the baseline's `tolerance.overall`
(0.01 for the deterministic suites, 0.05 for LLM modes), or when any slice loses more than
`tolerance.slice_items` cases (1; LLM modes do not gate slices) or, for the forecast suites,
more than `tolerance.slice` (0.01) on a gated slice metric. ECE and `alias_wrong` fail
when they rise. Real receipts are reported but never gated.

## Updating baselines

`--update-baseline` is the only way to change `eval/baselines/` or `eval/receipts/truth.jsonl`.
Use it when a change is meant to move the numbers, and commit the new files with that change.
The gate also refuses to compare against a baseline recorded on a different eval set.

## Private real receipts

Put images in `eval/fixtures/private/` (ignored by git, never commit them) with a JSON file of
the same name next to each:

```
eval/fixtures/private/lunch-0412.jpg
eval/fixtures/private/lunch-0412.json
{"merchant_canonical": "Ravi Restaurant", "merchant_raw": "RAVI RESTAURANT", "total": "48.50",
 "date": "2025-04-12", "currency": "AED",
 "items": [{"name": "Chicken Biryani", "qty": 1, "unit_price": "28.00", "amount": "28.00"}]}
```

Tesseract mode scores them as a separate `real` row. LLM modes never send them.

## Labeling rules (categorization)

Restaurants, cafes, food delivery: FOOD_DINING. Supermarkets and grocery delivery: GROCERIES.
Retail and marketplaces: SHOPPING. Fuel, rides, transit, tolls, parking: TRANSPORTATION.
Power, water, gas, telecom, internet, TV: UTILITIES. Media streaming, cinema, games, events:
ENTERTAINMENT. Pharmacies, clinics, labs: HEALTHCARE. Insurance, loans, card bills, BNPL,
investing, bank fees: FINANCE. Schools, courses, textbooks: EDUCATION. Flights, hotels,
intercity rail, booking sites: TRAVEL. Software, cloud, news and gym memberships:
SUBSCRIPTIONS. Salary, refunds, interest, cashback, dividends: INCOME. Person-to-person,
own-account and wallet transfers, remittances: TRANSFER. Cash withdrawals, government fees,
fines, donations, salons, postage: OTHER.

## Tests

`cd eval && python3 -m pytest tests` covers the generator and scorer; `run.sh` runs it inside
the image first.
