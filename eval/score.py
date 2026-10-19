"""Score eval predictions, print metric tables and gate on the stored baseline.

  score.py manifest       --truth T --private DIR --out DIR [--synthetic-only]
  score.py receipts       --suite S --truth T --pred P [--private-truth T2] --baseline B [--update-baseline]
  score.py categorization --suite S --cases C --pred P --baseline B [--update-baseline]
  score.py merchants      --suite S --cases C --pred P --baseline B [--update-baseline]

Exit status: 0 ok, 1 regression against the baseline, 2 baseline missing or stale.
"""
import argparse
import csv
import hashlib
import json
import sys
from collections import Counter, defaultdict
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
    "merchants": {"overall": 0.01, "slice_items": 1},
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

    print(f"\n== {args.suite}: CategorizationService ==")
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
    for name in ("receipts", "categorization", "merchants"):
        p = sub.add_parser(name)
        p.add_argument("--suite", required=True, choices=sorted(DEFAULT_TOLERANCE))
        p.add_argument("--pred", required=True)
        p.add_argument("--baseline", required=True)
        p.add_argument("--update-baseline", action="store_true")
        if name == "receipts":
            p.add_argument("--truth", required=True)
            p.add_argument("--private-truth")
        else:
            p.add_argument("--cases", required=True)
    args = ap.parse_args(argv)
    return {"manifest": cmd_manifest, "receipts": cmd_receipts, "categorization": cmd_categorization,
            "merchants": cmd_merchants}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
