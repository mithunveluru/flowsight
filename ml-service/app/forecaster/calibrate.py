"""Fit the forecaster's global interval priors on synthetic histories.

  python3 eval/forecast/generate.py --out /tmp/train.jsonl --users 400 --seed 7
  python3 -m app.forecaster.calibrate /tmp/train.jsonl --out app/forecaster/artifact

Replays every user at the generator's rolling origins and records the scaled forecast error
(actual - point) / scale per (series kind, months of history, step). Pass one stores the median
absolute error per key; pass two applies each user's own shrunk error factor and stores the
2.5/10/90/97.5% quantiles of the remaining error. The eval set uses a different generator seed.
Writes priors.json and meta.json; copy priors.json to the backend's
src/main/resources/com/flowsight/ml/forecast/ as well.
"""

import argparse
import hashlib
import json
import sys
from datetime import date
from pathlib import Path

from app.forecaster.model import MAX_STEP, PRIORS, Priors, forecast_history, history_from_transactions

STEPS = 4
KINDS = ("income", "spend", "category", "net")
BUCKETS = ("0", "1", "2", "3", "4-6", "7-12", "13+")
QUANTILES = (0.025, 0.1, 0.9, 0.975)


def _month_totals(tx):
    out = {}
    for d, typ, cat, amount in tx:
        m = out.setdefault(d.year * 12 + d.month - 1, {})
        if typ == "CREDIT":
            m["income"] = m.get("income", 0.0) + amount
        else:
            m["spend"] = m.get("spend", 0.0) + amount
            m[cat] = m.get(cat, 0.0) + amount
    for m in out.values():
        m["net"] = m.get("income", 0.0) - m.get("spend", 0.0)
    return out


def errors(histories, priors=None):
    """[(key, scaled error / user factor)] over all users, origins, series and steps."""
    out = []
    for user in histories:
        tx = [(date.fromisoformat(d), typ, cat, float(a)) for d, typ, cat, a in user["tx"]]
        totals = _month_totals(tx)
        for o in user["origins"]:
            today = date.fromisoformat(o)
            h = history_from_transactions(tx, today)
            current = today.year * 12 + today.month - 1
            for s in forecast_history(h, STEPS, priors):
                for step in s["steps"]:
                    actual = totals.get(current + step["k"] - 1, {}).get(s["name"], 0.0)
                    e = (actual - step["point"]) / s["scale"] / s["interval_ratio"]
                    out.append((Priors.key(s["kind"], s["data_months"], step["k"]), e))
    return out


def quantile(sorted_values, q):
    pos = q * (len(sorted_values) - 1)
    lo = int(pos)
    hi = min(lo + 1, len(sorted_values) - 1)
    return sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * (pos - lo)


def grouped(errs):
    by = {}
    for key, e in errs:
        by.setdefault(key, []).append(e)
    missing = [f"{k}|{b}|{s}" for k in KINDS for b in BUCKETS for s in range(1, MAX_STEP + 1)
               if f"{k}|{b}|{s}" not in by]
    if missing:
        raise SystemExit(f"no calibration data for {missing[:5]}... ({len(missing)} keys)")
    return {k: sorted(v) for k, v in sorted(by.items())}


def calibrate(histories, version):
    first = grouped(errors(histories))
    mad = {k: round(quantile(sorted(abs(e) for e in v), 0.5), 6) for k, v in first.items()}
    second = grouped(errors(histories, Priors({"version": version, "mad": mad})))
    q = {k: {str(p): round(quantile(v, p), 6) for p in QUANTILES} for k, v in second.items()}
    counts = {k: len(v) for k, v in second.items()}
    return {"version": version, "q": q, "mad": mad}, counts


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("histories")
    ap.add_argument("--out", required=True)
    ap.add_argument("--version", default="priors-2026-10")
    ap.add_argument("--source", default="eval/forecast/generate.py --users 400 --seed 7 (synthetic)")
    args = ap.parse_args(argv)
    raw = Path(args.histories).read_bytes()
    histories = [json.loads(line) for line in raw.decode("utf-8").splitlines() if line.strip()]
    priors, counts = calibrate(histories, args.version)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / PRIORS).write_text(json.dumps(priors, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    meta = {"version": args.version, "training_data": {
        "source": args.source, "users": len(histories),
        "origins": sum(len(h["origins"]) for h in histories), "errors": sum(counts.values()),
        "sha256": hashlib.sha256(raw).hexdigest()}}
    (out / "meta.json").write_text(json.dumps(meta, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"priors {args.version}: {len(counts)} keys from {meta['training_data']['errors']} errors")
    return 0


if __name__ == "__main__":
    sys.exit(main())
