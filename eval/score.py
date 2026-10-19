"""Score eval predictions, print metric tables and gate on the stored baseline.

  score.py manifest       --truth T --private DIR --out DIR [--synthetic-only]
  score.py receipts       --suite S --truth T --pred P [--private-truth T2] --baseline B [--update-baseline]
  score.py categorization --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py merchants      --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py recurring      --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py validator      --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py intents        --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py forecast       --suite S --histories H --pred P --baseline B [--reference R] [--update-baseline]

Exit status: 0 ok, 1 regression against the baseline, 2 baseline missing or stale.
"""
import argparse
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
from datetime import date
from decimal import Decimal
from pathlib import Path

from metrics import (FUZZY_THRESHOLD, amount_equal, calibration, compare, f1, format_table, macro_f1,
                     match_line_items, names_equal, similarity, to_decimal)

IMAGE_TYPES = {".jpg", ".jpeg", ".png", ".webp"}
# Deterministic suites tolerate a 1-point drop overall and one flipped case per slice;
# the LLM is sampled at temperature 0.2, so only a coarse overall gate makes sense there.
DEFAULT_TOLERANCE = {
    "ocr-tesseract": {"overall": 0.01, "slice_items": 1},
    "ocr-llm": {"overall": 0.05, "slice_items": None},
    # ml-service OCR with its LLM pass off is deterministic like the Java path
    "ocr-ml-service": {"overall": 0.01, "slice_items": 1},
    "categorization": {"overall": 0.01, "slice_items": 1},
    "categorization-rules": {"overall": 0.01, "slice_items": 1},
    "categorization-personal": {"overall": 0.01, "slice_items": 1},
    "categorization-personal-repeat": {"overall": 0.01, "slice_items": 1},
    "merchants": {"overall": 0.01, "slice_items": 1},
    "recurring-rules": {"overall": 0.01, "slice_items": 1},
    "recurring-model": {"overall": 0.01, "slice_items": 1},
    # both are pure functions of committed inputs: any change is a real change
    "insights-validator": {"overall": 0.0, "slice_items": 0},
    "assistant-intents": {"overall": 0.0, "slice_items": 0},
    # forecasts are deterministic; slices are gated on an absolute drop, not a case count
    "forecast-current": {"overall": 0.005, "slice": 0.01},
    "budget-pace-current": {"overall": 0.005, "slice": 0.01},
}


def read_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line, parse_float=Decimal) for line in f if line.strip()]


def sha256(path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def rate(values):
    values = list(values)
    return round(sum(values) / len(values), 4) if values else None


# ---------------------------------------------------------------- manifest

def load_private(private_dir: Path):
    """Real receipts: <name>.<jpg|png|...> next to <name>.json holding the truth fields."""
    records = []
    if not private_dir.is_dir():
        return records
    for image in sorted(p for p in private_dir.iterdir() if p.suffix.lower() in IMAGE_TYPES):
        sidecar = image.with_suffix(".json")
        if not sidecar.exists():
            print(f"warning: {image.name} has no {sidecar.name}; skipped", file=sys.stderr)
            continue
        truth = json.loads(sidecar.read_text(encoding="utf-8"), parse_float=Decimal)
        truth.update(id=f"real-{image.stem}", image=str(image.resolve()), source="real")
        records.append(truth)
    return records


def cmd_manifest(args):
    truth_path = Path(args.truth).resolve()
    rows = [{"id": r["id"], "image": str(truth_path.parent / r["image"])} for r in read_jsonl(truth_path)]
    private = [] if args.synthetic_only else load_private(Path(args.private))
    rows += [{"id": r["id"], "image": r["image"]} for r in private]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    with open(out / "manifest.jsonl", "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r) + "\n" for r in rows)
    with open(out / "private-truth.jsonl", "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r, default=str, ensure_ascii=False) + "\n" for r in private)
    print(f"manifest: {len(rows) - len(private)} synthetic + {len(private)} real receipts")
    return 0


# ---------------------------------------------------------------- receipts

def score_receipt(truth, pred):
    pred = pred or {}
    merchant = pred.get("merchant")
    items = pred.get("line_items") or []
    fuzzy = max(similarity(merchant, truth.get("merchant_canonical")), similarity(merchant, truth.get("merchant_raw")))
    return {
        "merchant_exact": names_equal(merchant, truth.get("merchant_canonical")),
        "merchant_fuzzy": fuzzy >= FUZZY_THRESHOLD,
        "total": amount_equal(pred.get("total"), truth.get("total")),
        "date": pred.get("date") is not None and pred.get("date") == truth.get("date"),
        "currency": pred.get("currency") == truth.get("currency"),
        "items_matched": match_line_items(items, truth.get("items") or []),
        "items_pred": len(items),
        "items_true": len(truth.get("items") or []),
        "confidence": pred.get("confidence"),
        "provider": pred.get("provider", "MISSING"),
    }


RECEIPT_RATES = ("merchant_exact", "merchant_fuzzy", "total", "date", "currency")


def aggregate_receipts(scores):
    metrics = {k: rate(s[k] for s in scores) for k in RECEIPT_RATES}
    metrics["line_item_f1"] = round(f1(sum(s["items_matched"] for s in scores), sum(s["items_pred"] for s in scores),
                                       sum(s["items_true"] for s in scores)), 4)
    ece, reliability = calibration((s["confidence"], s["total"]) for s in scores)
    metrics["ece"] = ece
    info = {
        "n": len(scores),
        "confidence_coverage": rate(s["confidence"] is not None for s in scores),
        "providers": dict(Counter(s["provider"] for s in scores)),
        "reliability": reliability,
    }
    return metrics, info


def receipt_slices(truth):
    keys = [f"currency={truth.get('currency')}", f"number_format={truth.get('number_format', 'unknown')}",
            f"perturbation={truth.get('perturbation', 'unknown')}"]
    return keys + [f"trap={t}" for t in truth.get("traps", [])]


def cmd_receipts(args):
    synthetic = read_jsonl(args.truth)
    private = read_jsonl(args.private_truth) if args.private_truth and Path(args.private_truth).exists() else []
    preds = {p["id"]: p for p in read_jsonl(args.pred)}
    scored = {t["id"]: score_receipt(t, preds.get(t["id"])) for t in synthetic + private}

    syn_scores = [scored[t["id"]] for t in synthetic]
    metrics, info = aggregate_receipts(syn_scores)
    if set(info["providers"]) <= {"FAILED", "MISSING"}:
        print(f"FAIL {args.suite}: no receipt produced a prediction {info['providers']}; check the runner log")
        return 2
    by_slice = defaultdict(list)
    for t in synthetic:
        for key in receipt_slices(t):
            by_slice[key].append(scored[t["id"]])
    slices = {}
    for key in sorted(by_slice):
        m, _ = aggregate_receipts(by_slice[key])
        slices[key] = {"n": len(by_slice[key]), **{k: m[k] for k in (*RECEIPT_RATES, "line_item_f1")}}
    current = {"suite": args.suite, "truth_sha256": sha256(args.truth), "count": len(synthetic), "metrics": metrics,
               "slices": slices, "slice_gated": list(RECEIPT_RATES), "info": info}

    columns = ["set", "n", *RECEIPT_RATES, "line_item_f1", "ece"]
    rows = [{"set": "synthetic", "n": len(syn_scores), **metrics}]
    if private:
        real_metrics, real_info = aggregate_receipts([scored[t["id"]] for t in private])
        rows.append({"set": "real (not gated)", "n": len(private), **real_metrics})
        current["real"] = {"metrics": real_metrics, "info": real_info}
    print(f"\n== {args.suite}: receipts (merchant fuzzy threshold {FUZZY_THRESHOLD}) ==")
    print(format_table(rows, columns))
    print(f"providers: {info['providers']}  confidence coverage: {info['confidence_coverage']}")
    print("\nslices (synthetic):")
    print(format_table([{"slice": k, **v} for k, v in slices.items()], ["slice", "n", *RECEIPT_RATES, "line_item_f1"]))
    if info["reliability"]:
        print("\nreliability (confidence vs total correct):")
        print(format_table(info["reliability"], ["bin", "n", "mean_conf", "accuracy"]))
    return finish(args, current)


# ---------------------------------------------------------------- text sets

def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return [row for row in csv.DictReader(f) if row.get("id") and not row["id"].startswith("#")]


CATEGORIZATION_SUITES = {
    "categorization": "ml-service categorizer (global model), as served when ML_CATEGORIZER_ENABLED",
    "categorization-rules": "CategorizationService keyword rules, the fallback",
    "categorization-personal": "categorizer + personal layer; history = the region's other merchants (no label leak)",
    "categorization-personal-repeat": "categorizer + personal layer; history = other rows, same merchants allowed",
}


def cmd_categorization(args):
    cases = read_csv(args.cases)
    preds = {p["id"]: p for p in read_jsonl(args.pred)}
    truth = [c["category"] for c in cases]
    predicted = [preds.get(c["id"], {}).get("category", "MISSING") for c in cases]
    confidence = [preds.get(c["id"], {}).get("confidence") for c in cases]

    def summarize(idx):
        t = [truth[i] for i in idx]
        p = [predicted[i] for i in idx]
        mf1, per_class = macro_f1(t, p)
        ece, reliability = calibration((confidence[i], truth[i] == predicted[i]) for i in idx)
        return {"accuracy": rate(a == b for a, b in zip(t, p)), "macro_f1": mf1, "ece": ece}, per_class, reliability

    all_idx = list(range(len(cases)))
    metrics, per_class, reliability = summarize(all_idx)
    regions = defaultdict(list)
    for i, c in enumerate(cases):
        regions[f"region={c['region']}"].append(i)
    slices = {k: {"n": len(v), **{m: summarize(v)[0][m] for m in ("accuracy", "macro_f1")}} for k, v in sorted(regions.items())}
    info = {"n": len(cases), "uncategorized_rate": rate(p == "UNCATEGORIZED" for p in predicted),
            "per_class_f1": per_class, "reliability": reliability}
    current = {"suite": args.suite, "truth_sha256": sha256(args.cases), "count": len(cases), "metrics": metrics,
               "slices": slices, "slice_gated": ["accuracy"], "info": info}

    print(f"\n== {args.suite}: {CATEGORIZATION_SUITES[args.suite]} ==")
    print(format_table([{"set": "all", "n": len(cases), **metrics, "uncategorized": info["uncategorized_rate"]}],
                       ["set", "n", "accuracy", "macro_f1", "ece", "uncategorized"]))
    print(format_table([{"slice": k, **v} for k, v in slices.items()], ["slice", "n", "accuracy", "macro_f1"]))
    print(format_table([{"class": k, "n": truth.count(k), "f1": v} for k, v in per_class.items()], ["class", "n", "f1"]))
    print("reliability (confidence vs category correct):")
    print(format_table(reliability, ["bin", "n", "mean_conf", "accuracy"]))
    return finish(args, current)


MERCHANT_METRICS = ("alias_exact", "alias_wrong", "fuzzy", "key_match", "extract_exact", "extract_fuzzy",
                    "resolved_exact", "resolved_fuzzy")


def score_merchant(case, pred):
    expected = case["expected"]
    canonical = pred.get("canonical")
    # the grouping key stands in for the name when no alias matched
    shown = canonical if canonical else pred.get("key")
    return {
        "alias_exact": names_equal(canonical, expected),
        "alias_wrong": canonical is not None and not names_equal(canonical, expected),
        "fuzzy": similarity(shown, expected) >= FUZZY_THRESHOLD,
        "key_match": bool(pred.get("key")) and pred.get("key") == pred.get("expected_key"),
        "extract_exact": names_equal(pred.get("extracted"), expected),
        "extract_fuzzy": similarity(pred.get("extracted"), expected) >= FUZZY_THRESHOLD,
        "resolved_exact": names_equal(pred.get("resolved"), expected),
        "resolved_fuzzy": similarity(pred.get("resolved"), expected) >= FUZZY_THRESHOLD,
    }


def cmd_merchants(args):
    cases = read_csv(args.cases)
    preds = {p["id"]: p for p in read_jsonl(args.pred)}
    scored = [score_merchant(c, preds.get(c["id"], {})) for c in cases]

    def summarize(rows):
        return {m: rate(r[m] for r in rows) for m in MERCHANT_METRICS}

    metrics = summarize(scored)
    regions = defaultdict(list)
    for c, s in zip(cases, scored):
        regions[f"region={c['region']}"].append(s)
    slices = {k: {"n": len(v), **summarize(v)} for k, v in sorted(regions.items())}
    current = {"suite": args.suite, "truth_sha256": sha256(args.cases), "count": len(cases), "metrics": metrics,
               "slices": slices, "slice_gated": [m for m in MERCHANT_METRICS if m != "alias_wrong"], "info": {"n": len(cases)}}

    print(f"\n== {args.suite}: MerchantNormalizationService (alias_*, fuzzy, key_match), "
          f"MerchantText.clean (extract_*) and MerchantResolver offline (resolved_*) ==")
    print(format_table([{"set": "all", "n": len(cases), **metrics}, *({"set": k, **v} for k, v in slices.items())],
                       ["set", "n", *MERCHANT_METRICS]))
    return finish(args, current)


RECURRING_SUITES = {
    "recurring-rules": "RecurringDetectionService and LeakDetectionService rules (the fallback)",
    "recurring-model": "recurring-scorer model with the model-path leak detectors",
}
RECURRING_METRICS = ("group_precision", "group_recall", "group_f1", "period_accuracy", "next_within_3d",
                     "irregular_fp_rate", "creep_precision", "creep_recall", "creep_f1", "dup_precision",
                     "dup_recall", "overlap_precision", "overlap_recall", "drain_precision", "drain_recall")
OVERLAP_CATEGORIES = {"SUBSCRIPTIONS", "ENTERTAINMENT", "EDUCATION"}
SHOWN_CONFIDENCE = 0.5  # the Subscriptions page lists POSSIBLE-tier patterns separately


def ratio(num, den):
    return round(num / den, 4) if den else None


def overlap_truth(groups):
    """The leak's definition applied to the truth: recurring subscriptions sharing the category
    with the most of them (at least two)."""
    by_category = defaultdict(list)
    for g in groups:
        if g["recurring"] and g["count"] >= 2 and g["category"] in OVERLAP_CATEGORIES:
            by_category[g["category"]].append(g["id"])
    best = sorted(by_category.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    return set(best[0][1]) if best and len(best[0][1]) >= 2 else set()


def score_recurring_user(case, pred):
    groups = case["groups"]
    owner = {}
    for key, idx in pred["keys"].items():
        owner[key] = Counter(case["tx"][i][1] for i in idx).most_common(1)[0][0]

    out = Counter()
    matched, shown, flagged = {}, set(), set()
    for p in pred["patterns"]:
        g = owner.get(p["key"])
        flagged.add(g)
        hit = g is not None and groups[g]["recurring"] and g not in matched
        out["pred"] += 1
        out["tp"] += hit
        conf = p.get("confidence")
        is_shown = conf is not None and float(conf) >= SHOWN_CONFIDENCE
        out["shown_pred"] += is_shown
        if hit:
            matched[g] = p
            if is_shown:
                shown.add(g)
    detail = []
    for g in groups:
        if g["recurring"] and g["count"] >= 2:
            out["true"] += 1
            out["shown_tp"] += g["id"] in shown
            detail.append(("recurring", g, g["id"] in matched))
        elif not g["recurring"] and g["count"] >= 2:
            detail.append(("negative", g, g["id"] in flagged))
        if g.get("frequent_irregular"):
            out["irregular"] += 1
            out["irregular_fp"] += g["id"] in flagged
    for g, p in matched.items():
        truth = groups[g]
        out["period_ok"] += p["period"] == truth["period"]
        if truth["next_date"] and p.get("next"):
            out["next_n"] += 1
            err = abs((date.fromisoformat(p["next"]) - date.fromisoformat(truth["next_date"])).days)
            out["next_abs"] += err
            out["next_3d"] += err <= 3
            if p.get("low") and p.get("high"):
                out["interval_n"] += 1
                out["interval_hit"] += p["low"] <= truth["next_date"] <= p["high"]
                out["interval_width"] += (date.fromisoformat(p["high"]) - date.fromisoformat(p["low"])).days

    def leak(kind, is_true, truth_ids):
        flagged = {owner[k] for k in pred["leaks"].get(kind, []) if k in owner}
        out[f"{kind}_pred"] += len(flagged)
        out[f"{kind}_tp"] += sum(1 for g in flagged if is_true(g))
        out[f"{kind}_true"] += len(truth_ids)
        out[f"{kind}_hit"] += len(flagged & truth_ids)

    leak("creep", lambda g: groups[g]["creep"] is not None, {g["id"] for g in groups if g["creep"]})
    leak("duplicate", lambda g: groups[g]["series"] > 1, {g["id"] for g in groups if g["series"] > 1})
    overlap = overlap_truth(groups)
    leak("overlap", lambda g: g in overlap, overlap)
    leak("drain", lambda g: groups[g]["drain"], {g["id"] for g in groups if g["drain"]})
    return out, detail


def recurring_metrics(c):
    p, r = ratio(c["tp"], c["pred"]), ratio(c["tp"], c["true"])
    m = {"group_precision": p, "group_recall": r,
         "group_f1": round(2 * p * r / (p + r), 4) if p and r else 0.0,
         "period_accuracy": ratio(c["period_ok"], c["tp"]), "next_within_3d": ratio(c["next_3d"], c["next_n"]),
         "irregular_fp_rate": ratio(c["irregular_fp"], c["irregular"])}
    for kind, name in (("creep", "creep"), ("duplicate", "dup"), ("overlap", "overlap"), ("drain", "drain")):
        m[f"{name}_precision"] = ratio(c[f"{kind}_tp"], c[f"{kind}_pred"])
        m[f"{name}_recall"] = ratio(c[f"{kind}_hit"], c[f"{kind}_true"])
    cp, cr = m["creep_precision"], m["creep_recall"]
    m["creep_f1"] = round(2 * cp * cr / (cp + cr), 4) if cp and cr else 0.0
    return m


def cmd_recurring(args):
    cases = read_jsonl(args.cases)
    preds = {p["user"]: p for p in read_jsonl(args.pred)}
    total = Counter()
    by_slice = defaultdict(Counter)
    kinds = defaultdict(lambda: [0, 0])
    for case in cases:
        counts, detail = score_recurring_user(case, preds[case["user"]])
        total += counts
        by_slice[f"region={case['region']}"] += counts
        by_slice["history=" + ("short" if case["months"] <= 6 else "long")] += counts
        for role, g, hit in detail:
            kinds[(role, g["kind"])][0] += 1
            kinds[(role, g["kind"])][1] += hit

    metrics = recurring_metrics(total)
    slices = {}
    for name, c in sorted(by_slice.items()):
        sm = recurring_metrics(c)
        slices[name] = {"n": c["true"], "group_precision": sm["group_precision"], "group_recall": sm["group_recall"],
                        "group_f1": sm["group_f1"], "creep_recall": sm["creep_recall"]}
    for (role, kind), (n, hit) in sorted(kinds.items()):
        slices[f"{role}={kind}"] = {"n": n, ("recall" if role == "recurring" else "fp_rate"): ratio(hit, n)}
    info = {"users": len(cases), "true_groups": total["true"], "stored_patterns": total["pred"],
            "shown_precision": ratio(total["shown_tp"], total["shown_pred"]),
            "shown_recall": ratio(total["shown_tp"], total["true"]),
            "next_mae_days": ratio(total["next_abs"], total["next_n"]),
            "next_interval_coverage": ratio(total["interval_hit"], total["interval_n"]),
            "next_interval_width_days": ratio(total["interval_width"], total["interval_n"]),
            "creep_true": total["creep_true"], "duplicate_true": total["duplicate_true"],
            "overlap_true": total["overlap_true"], "drain_true": total["drain_true"]}
    current = {"suite": args.suite, "truth_sha256": sha256(args.cases), "count": len(cases), "metrics": metrics,
               "slices": slices, "slice_gated": ["group_precision", "group_recall", "recall", "fp_rate"],
               "info": info}

    print(f"\n== {args.suite}: {RECURRING_SUITES[args.suite]} ==")
    print(format_table([{"set": "all", "n": total["true"], **metrics}],
                       ["set", "n", "group_precision", "group_recall", "group_f1", "period_accuracy",
                        "next_within_3d", "irregular_fp_rate"]))
    print(format_table([{"leak": k, "true": total[f"{t}_true"], "precision": metrics[f"{k}_precision"],
                         "recall": metrics[f"{k}_recall"]}
                        for k, t in (("creep", "creep"), ("dup", "duplicate"), ("overlap", "overlap"),
                                     ("drain", "drain"))], ["leak", "true", "precision", "recall"]))
    print(format_table([{"slice": k, **v} for k, v in slices.items() if "group_f1" in v],
                       ["slice", "n", "group_precision", "group_recall", "group_f1", "creep_recall"]))
    print(format_table([{"slice": k, **v} for k, v in slices.items() if "group_f1" not in v],
                       ["slice", "n", "recall", "fp_rate"]))
    print(format_table([info], [k for k in info if k not in ("users",)]))
    return finish(args, current)


# ---------------------------------------------------------------- insights

REFUSALS = {"WRITE_REQUEST", "OTHER_USERS", "UNSUPPORTED"}


def score_validator(cases, preds):
    """accuracy over all cases; false_accept = invented values let through; false_reject = grounded text refused."""
    rows = []
    for c in cases:
        accepted = bool(preds.get(c["id"], {}).get("grounded", False))
        rows.append({"variant": c["variant"], "expected": c["expected"], "correct": accepted == (c["expected"] == "accept"),
                     "accepted": accepted})
    rejects = [r for r in rows if r["expected"] == "reject"]
    accepts = [r for r in rows if r["expected"] == "accept"]
    metrics = {"accuracy": rate(r["correct"] for r in rows),
               "false_accept_rate": rate(r["accepted"] for r in rejects),
               "false_reject_rate": rate(not r["accepted"] for r in accepts)}
    by_variant = defaultdict(list)
    for r in rows:
        by_variant[f"variant={r['variant']}"].append(r)
    slices = {k: {"n": len(v), "accuracy": rate(r["correct"] for r in v)} for k, v in sorted(by_variant.items())}
    return metrics, slices


def score_intents(cases, preds):
    rows = []
    for c in cases:
        p = preds.get(c["id"], {})
        rows.append({"intent": c["intent"], "intent_ok": p.get("intent") == c["intent"],
                     "period_ok": p.get("period") == c["period"],
                     "category_ok": (p.get("category") or "") == (c["category"] or ""),
                     "refused": p.get("intent") in REFUSALS})
    refusals = [r for r in rows if r["intent"] in REFUSALS]
    answerable = [r for r in rows if r["intent"] not in REFUSALS]
    metrics = {"intent_accuracy": rate(r["intent_ok"] for r in rows),
               "period_accuracy": rate(r["period_ok"] for r in rows),
               "category_accuracy": rate(r["category_ok"] for r in rows),
               "refusal_recall": rate(r["refused"] for r in refusals),
               "answerable_recall": rate(not r["refused"] for r in answerable)}
    by_intent = defaultdict(list)
    for r in rows:
        by_intent[f"intent={r['intent']}"].append(r)
    slices = {k: {"n": len(v), "intent_accuracy": rate(r["intent_ok"] for r in v)} for k, v in sorted(by_intent.items())}
    return metrics, slices


def cmd_validator(args):
    cases = read_jsonl(args.cases)
    preds = {p["id"]: p for p in read_jsonl(args.pred)}
    metrics, slices = score_validator(cases, preds)
    current = {"suite": args.suite, "truth_sha256": sha256(args.cases), "count": len(cases), "metrics": metrics,
               "slices": slices, "slice_gated": ["accuracy"], "info": {"n": len(cases)}}
    print(f"\n== {args.suite}: GroundingValidator on seeded grounded / invented-number sentences ==")
    print(format_table([{"set": "all", "n": len(cases), **metrics}], ["set", "n", *metrics]))
    print(format_table([{"slice": k, **v} for k, v in slices.items()], ["slice", "n", "accuracy"]))
    return finish(args, current)


def cmd_intents(args):
    cases = read_csv(args.cases)
    preds = {p["id"]: p for p in read_jsonl(args.pred)}
    metrics, slices = score_intents(cases, preds)
    current = {"suite": args.suite, "truth_sha256": sha256(args.cases), "count": len(cases), "metrics": metrics,
               "slices": slices, "slice_gated": ["intent_accuracy"], "info": {"n": len(cases)}}
    print(f"\n== {args.suite}: IntentMatcher (the assistant's no-LLM path) ==")
    print(format_table([{"set": "all", "n": len(cases), **metrics}], ["set", "n", *metrics]))
    print(format_table([{"slice": k, **v} for k, v in slices.items()], ["slice", "n", "intent_accuracy"]))
# ---------------------------------------------------------------- forecasts

FORECAST_SUITES = {
    "forecast-current": "flat mean of the last three trend points (current month included), as AnalyticsService shipped",
    "budget-pace-current": "linear month-end pace spend * days / elapsed, as BudgetService shipped",
}
FORECAST_METRICS = ("wape", "mape", "net_smae", "cov80_gap", "cov95_gap")


def history_bucket(n):
    return "history=0" if n == 0 else "history=1-3" if n <= 3 else "history=4-11" if n <= 11 else "history=12+"


def monthly_actuals(histories):
    """{user: {"YYYY-MM": {series: total}}} with income, spend, net and cat:<CATEGORY>."""
    out = {}
    for h in histories:
        months = defaultdict(lambda: defaultdict(float))
        for d, typ, cat, amount in h["tx"]:
            m = months[d[:7]]
            if typ == "CREDIT":
                m["income"] += float(amount)
            else:
                m["spend"] += float(amount)
                m["cat:" + cat] += float(amount)
        for m in months.values():
            m["net"] = m["income"] - m["spend"]
        out[h["user"]] = months
    return out


def forecast_rows(histories, preds):
    actuals = monthly_actuals(histories)
    tags = {h["user"]: h["tags"] for h in histories}
    rows = []
    for p in preds:
        month = actuals[p["user"]].get(p["target"], {})
        row = {k: (float(v) if isinstance(v, Decimal) else v) for k, v in p.items()}
        row.update(actual=month.get(p["series"], 0.0), income=month.get("income", 0.0),
                   spend=month.get("spend", 0.0), tags=tags[p["user"]])
        rows.append(row)
    return rows


def forecast_metrics(rows):
    positive = [r for r in rows if r["series"] != "net"]
    totals = [r for r in rows if r["series"] in ("income", "spend") and r["actual"] > 0]
    net = [r for r in rows if r["series"] == "net"]
    ranged = [r for r in rows if r.get("lo80") is not None]

    def share(rs, lo, hi):
        return round(sum(r[lo] <= r["actual"] <= r[hi] for r in rs) / len(rs), 4) if rs else None

    abs_err = sum(abs(r["actual"] - r["point"]) for r in positive)
    out = {
        "wape": round(abs_err / sum(abs(r["actual"]) for r in positive), 4) if positive else None,
        "mape": round(sum(abs(r["actual"] - r["point"]) / r["actual"] for r in totals) / len(totals), 4) if totals else None,
        "net_smae": round(sum(abs(r["actual"] - r["point"]) for r in net) / sum(r["income"] + r["spend"] for r in net), 4)
        if net and sum(r["income"] + r["spend"] for r in net) > 0 else None,
        "cov80": share(ranged, "lo80", "hi80"),
        "cov95": share(ranged, "lo95", "hi95"),
    }
    out["cov80_gap"] = round(abs(out["cov80"] - 0.80), 4) if out["cov80"] is not None else None
    out["cov95_gap"] = round(abs(out["cov95"] - 0.95), 4) if out["cov95"] is not None else None
    return out


def forecast_slices(row):
    kind = "category" if row["series"].startswith("cat:") else row["series"]
    keys = [history_bucket(row["n"]), f"series={kind}", f"h={row['h']}"]
    return keys + [f"tag={t}" for t in row["tags"] if t in ("seasonal", "shock", "volatile", "drift")]


def cmd_forecast(args):
    histories = [json.loads(line) for line in open(args.histories, encoding="utf-8") if line.strip()]
    rows = forecast_rows(histories, read_jsonl(args.pred))
    metrics = forecast_metrics(rows)
    groups = defaultdict(list)
    for r in rows:
        for key in forecast_slices(r):
            groups[key].append(r)
    slices = {k: {"n": len(v), **forecast_metrics(v)} for k, v in sorted(groups.items())}
    info = {"n": len(rows), "users": len(histories), "origins": len({(r["user"], r["origin"]) for r in rows}),
            "cov80": metrics.pop("cov80"), "cov95": metrics.pop("cov95"),
            "methods": dict(sorted(Counter(r.get("method", "n/a") for r in rows).items()))}
    current = {"suite": args.suite, "truth_sha256": sha256(args.histories), "count": len(rows), "metrics": metrics,
               "slices": slices, "slice_gated": ["wape", "net_smae", "cov80_gap"], "info": info}

    columns = ["set", "n", "wape", "mape", "net_smae", "cov80", "cov95"]
    table = [{"set": "all", "n": len(rows), **metrics, "cov80": info["cov80"], "cov95": info["cov95"]}]
    table += [{"set": k, **v} for k, v in slices.items()]
    print(f"\n== {args.suite}: {FORECAST_SUITES[args.suite]} ==")
    print(f"{info['users']} users, {info['origins']} origins; methods {info['methods']}")
    print(format_table(table, columns))
    if args.reference and Path(args.reference).exists():
        ref = json.loads(Path(args.reference).read_text())
        print(f"against {ref['suite']}:")
        rows_ref = [{"set": "all", **{f"{m}": metrics.get(m) for m in ("wape", "mape", "net_smae")},
                     **{f"ref_{m}": ref["metrics"].get(m) for m in ("wape", "mape", "net_smae")}}]
        rows_ref += [{"set": k, **{m: v.get(m) for m in ("wape", "mape", "net_smae")},
                      **{f"ref_{m}": ref["slices"].get(k, {}).get(m) for m in ("wape", "mape", "net_smae")}}
                     for k, v in slices.items()]
        print(format_table(rows_ref, ["set", "wape", "ref_wape", "mape", "ref_mape", "net_smae", "ref_net_smae"]))
    return finish(args, current)


# ---------------------------------------------------------------- baseline gate

def finish(args, current):
    report_dir = Path(args.pred).parent
    (report_dir / f"{args.suite}-report.json").write_text(json.dumps(current, indent=2, default=str) + "\n")
    baseline_path = Path(args.baseline)
    if args.update_baseline:
        old = json.loads(baseline_path.read_text()) if baseline_path.exists() else {}
        stored = {k: current[k] for k in ("suite", "truth_sha256", "count", "metrics", "slices")}
        stored["tolerance"] = old.get("tolerance", DEFAULT_TOLERANCE[args.suite])
        stored["info"] = {k: v for k, v in current["info"].items() if k != "reliability"}
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(json.dumps(stored, indent=2, sort_keys=True, default=str) + "\n")
        print(f"baseline written: {baseline_path}")
        return 0
    if not baseline_path.exists():
        print(f"FAIL {args.suite}: no baseline at {baseline_path}; run with --update-baseline")
        return 2
    baseline = json.loads(baseline_path.read_text())
    if baseline.get("truth_sha256") != current["truth_sha256"]:
        print(f"FAIL {args.suite}: the eval set changed since the baseline was recorded; "
              "rerun with --update-baseline if that is intended")
        return 2
    problems = compare(current, baseline)
    deltas = ", ".join(f"{k} {v:+.4f}" for k, v in
                       ((k, current["metrics"][k] - b) for k, b in baseline["metrics"].items()
                        if b is not None and current["metrics"].get(k) is not None))
    print(f"vs baseline: {deltas}")
    if problems:
        print(f"FAIL {args.suite}: regression against {baseline_path}")
        for p in problems:
            print(f"  - {p}")
        return 1
    print(f"PASS {args.suite}")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("manifest")
    m.add_argument("--truth", required=True)
    m.add_argument("--private", required=True)
    m.add_argument("--out", required=True)
    m.add_argument("--synthetic-only", action="store_true")
    for name in ("receipts", "categorization", "merchants", "recurring", "validator", "intents", "forecast"):
        p = sub.add_parser(name)
        p.add_argument("--suite", required=True, choices=sorted(DEFAULT_TOLERANCE))
        p.add_argument("--pred", required=True)
        p.add_argument("--baseline", required=True)
        p.add_argument("--update-baseline", action="store_true")
        if name == "receipts":
            p.add_argument("--truth", required=True)
            p.add_argument("--private-truth")
        elif name == "forecast":
            p.add_argument("--histories", required=True)
            p.add_argument("--reference")
        else:
            p.add_argument("--cases", required=True)
    args = ap.parse_args(argv)
    return {"manifest": cmd_manifest, "receipts": cmd_receipts, "categorization": cmd_categorization,
            "merchants": cmd_merchants, "recurring": cmd_recurring, "validator": cmd_validator,
            "intents": cmd_intents, "forecast": cmd_forecast}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
