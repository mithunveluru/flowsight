# Eval harness

Measures the receipt OCR path, transaction categorization and merchant normalization with
the real backend code, and fails when a change makes any of them worse than the stored
baseline.

## Run

```
eval/run.sh                    # offline: scorer tests, Tesseract OCR, categorization, merchants
eval/run.sh --update-baseline  # same, then rewrite eval/baselines/*.json from this run
```

Requires Docker only. Everything runs in the image built from `eval/Dockerfile` (base image
pinned by digest, apt packages pinned to an Ubuntu archive snapshot), so local and CI numbers
are identical. Maven dependencies are cached in `~/.m2` (override with `EVAL_M2`). A run takes
about 1.5 minutes on 8 cores; outputs land in `eval/out/` (ignored by git).

Exit status is non-zero when a metric regresses, when the generated receipts no longer match
`eval/receipts/truth.jsonl`, or when a baseline is missing.

LLM modes call the receipt-ocr service and score synthetic receipts only:

```
RECEIPT_OCR_URL=http://127.0.0.1:8001 GROQ_API_KEY=... eval/run.sh --mode llm
RECEIPT_OCR_URL=http://127.0.0.1:8001 GROQ_API_KEY=... eval/run.sh --mode llm-http11
```

`llm` calls the service exactly as the backend does. `llm-http11` is a diagnostic: the
backend's `HttpClient` defaults to HTTP/2, whose cleartext upgrade the service rejects, so
it never gets an LLM answer. The diagnostic forces HTTP/1.1 to measure the model itself.
`EVAL_DELAY_MS` (default 2000) spaces out requests for the provider's rate limit.

## What is measured

| Suite | Code under test | Data |
|---|---|---|
| `ocr-tesseract`, `ocr-llm` | `CurrentReceiptPipeline`, a copy of `ReceiptService.extractWithFallback` without the database: `ReceiptOcrClientService` + `ReceiptOcrMapper`, else `OcrService` + `ReceiptParserService` | 200 synthetic receipts from `receipts/generate.py`, plus private real receipts if present |
| `categorization` | `CategorizationService.categorize` with the inputs `TransactionIngestionPipeline.processCsvRow` gives it | `categorization/cases.csv` (476 rows, India / KSA / UAE / US) |
| `merchants` | `MerchantNormalizationService.normalize`, `MerchantText.clean` and `MerchantResolver` (offline) | `merchants/cases.csv` (352 rows) |

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
  bins, with a reliability table. `n/a` when the pipeline returns no confidence (Tesseract).

Slices: currency, number format (`us` 1,234.50, `eu` 1.234,50, `indian` 1,23,456.00,
`arabic_indic` ١٬٢٣٤٫٥٠), perturbation (`clean`, `background`, `rotation`, `perspective`,
`blur`, `glare`, `crumple`, `low_contrast`) and trap (header above the merchant, merchant
names containing label words, Total Items / Tendered / Cash / Change lines, tip after total).
`clean`, `blur`, `glare`, `crumple` and `low_contrast` are cropped receipts; the other three
are photos with the table visible around the paper.

Categorization: accuracy, macro F1 over the labeled classes, ECE of the returned confidence.
Merchants: `alias_exact` (canonical name correct), `alias_wrong` (an alias fired and named the
wrong merchant, lower is better), `fuzzy` (canonical name, or the grouping key when no alias
matched, at least 0.85 similar), `key_match` (raw and expected strings get the same grouping
key), `extract_exact` / `extract_fuzzy` for the descriptor cleaner `MerchantText.clean`, and
`resolved_exact` / `resolved_fuzzy` for the name `MerchantResolver` stores on a transaction
(built-in aliases, in-memory trigram matching, no user rules, ML or LLM steps).

## Regression gate

A run fails when an overall metric drops by more than the baseline's `tolerance.overall`
(0.01 for the deterministic suites, 0.05 for LLM modes), or when any slice loses more than
`tolerance.slice_items` cases (1; LLM modes do not gate slices). ECE and `alias_wrong` fail
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
