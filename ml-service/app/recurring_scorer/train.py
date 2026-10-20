"""Train the recurring scorer on the synthetic corpus and write a registry artifact.

  python -m app.recurring_scorer.train --out app/recurring_scorer/artifact

numpy only. L2-regularised logistic regression fitted by Newton's method on standardized
features; the penalty is picked by 5-fold cross-validated log loss, Platt scaling is fitted
on the out-of-fold logits, and the decision threshold maximises out-of-fold F1. The
next-date interval is widened until it covers 80% of held-out next charges in the corpus.
Same seed, same numpy: byte-identical model.npz and meta.json.
"""

import argparse
import hashlib
import json
import sys
from datetime import date
from pathlib import Path

import numpy as np

from app.recurring_scorer import corpus
from app.recurring_scorer.model import FEATURES, PERIODS, RecurringModel, analyze, save, sigmoid

VERSION = "logreg-1"
SEED = 11
L2_GRID = (0.01, 0.1, 1.0, 10.0)
FOLDS = 5
TARGET_COVERAGE = 0.8
# smallest half-width of the next-date interval per period, in days
MIN_HALFWIDTH = {"WEEKLY": 1, "BIWEEKLY": 1, "MONTHLY": 2, "QUARTERLY": 4, "ANNUAL": 7}
WIDEN_GRID = (1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0)


def featurize(rows: list[dict]) -> tuple[np.ndarray, list[dict]]:
    infos = [analyze(date.fromisoformat(r["as_of"]), r["days"], r["amounts"], r["category"]) for r in rows]
    return np.vstack([i["x"] for i in infos]), infos


def fit_logreg(x: np.ndarray, y: np.ndarray, l2: float, iterations: int = 50) -> tuple[np.ndarray, float]:
    n, d = x.shape
    xb = np.hstack([x, np.ones((n, 1))])
    w = np.zeros(d + 1)
    penalty = np.full(d + 1, l2)
    penalty[-1] = 0.0
    for _ in range(iterations):
        p = sigmoid(xb @ w)
        grad = xb.T @ (p - y) + penalty * w
        hess = (xb * (p * (1 - p))[:, None]).T @ xb + np.diag(penalty) + 1e-9 * np.eye(d + 1)
        step = np.linalg.solve(hess, grad)
        w -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return w[:-1], float(w[-1])


def standardize(x: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    mean = x.mean(axis=0)
    scale = x.std(axis=0)
    scale[scale < 1e-9] = 1.0
    return mean, scale


def folds(n: int, k: int, seed: int) -> np.ndarray:
    return np.random.default_rng(seed).permutation(n) % k


def log_loss(p: np.ndarray, y: np.ndarray) -> float:
    p = np.clip(p, 1e-12, 1 - 1e-12)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def oof_logits(x, y, l2, fold_of) -> np.ndarray:
    out = np.zeros(len(y))
    for f in range(FOLDS):
        train, test = fold_of != f, fold_of == f
        mean, scale = standardize(x[train])
        coef, b = fit_logreg((x[train] - mean) / scale, y[train], l2)
        out[test] = ((x[test] - mean) / scale) @ coef + b
    return out


def fit_platt(z: np.ndarray, y: np.ndarray) -> tuple[float, float]:
    coef, b = fit_logreg(z[:, None], y, 1e-6)
    return float(coef[0]), b


def ece(p: np.ndarray, y: np.ndarray, bins: int = 10) -> float:
    edges = np.minimum((p * bins).astype(int), bins - 1)
    return float(sum(abs(p[edges == b].mean() - y[edges == b].mean()) * np.sum(edges == b)
                     for b in range(bins) if np.any(edges == b)) / len(p))


def classification(p: np.ndarray, y: np.ndarray, threshold: float) -> dict:
    pred = p >= threshold
    tp = float(np.sum(pred & (y == 1)))
    precision = tp / max(1.0, float(pred.sum()))
    recall = tp / max(1.0, float(y.sum()))
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"precision": round(precision, 4), "recall": round(recall, 4), "f1": round(f1, 4),
            "log_loss": round(log_loss(p, y), 4), "ece": round(ece(p, y), 4)}


def fit_interval(rows: list[dict], infos: list[dict]) -> tuple[float, dict]:
    """Smallest widening factor whose interval covers TARGET_COVERAGE of real next charges."""
    cases = [(r["next_in_days"], i) for r, i in zip(rows, infos)
             if r["label"] == 1 and r["next_in_days"] is not None and i["next"] is not None]
    names = [p for p, _ in PERIODS]
    floors = np.array([MIN_HALFWIDTH[n] for n in names], dtype=np.float64)
    probe = RecurringModel(np.zeros(1), np.ones(1), np.zeros(1), 0, (1, 0), 0.5, 1.0, floors)
    for widen in WIDEN_GRID:
        probe.widen = widen
        hits, width = 0, 0
        for truth, info in cases:
            lo, hi = probe.halfwidths(info["period"], info["early"], info["late"])
            hits += info["next"] - lo <= truth <= info["next"] + hi
            width += lo + hi
        coverage = hits / len(cases)
        if coverage >= TARGET_COVERAGE or widen == WIDEN_GRID[-1]:
            point = float(np.mean([abs(t - i["next"]) for t, i in cases]))
            return widen, {"cases": len(cases), "coverage": round(coverage, 4),
                           "mean_width_days": round(width / len(cases), 2), "point_mae_days": round(point, 3)}
    raise AssertionError("unreachable")


def train(out_dir: Path, seed: int = SEED, version: str = VERSION, rows: list[dict] | None = None,
          l2_grid=L2_GRID) -> dict:
    rows = corpus.build(seed) if rows is None else rows
    x, infos = featurize(rows)
    y = np.array([r["label"] for r in rows], dtype=np.float64)
    fold_of = folds(len(rows), FOLDS, seed)

    grid = {}
    for l2 in l2_grid:
        z = oof_logits(x, y, l2, fold_of)
        grid[l2] = (z, log_loss(sigmoid(z), y))
    best = min(grid, key=lambda l2: (grid[l2][1], l2))
    z = grid[best][0]
    platt = fit_platt(z, y)
    p = sigmoid(platt[0] * z + platt[1])
    if log_loss(p, y) >= log_loss(sigmoid(z), y):
        platt = (1.0, 0.0)  # scaling did not help out of fold; keep the raw logits
        p = sigmoid(z)
    thresholds = np.round(np.arange(0.2, 0.81, 0.05), 2)
    threshold = float(max(thresholds, key=lambda t: (classification(p, y, t)["f1"], -abs(t - 0.5))))

    mean, scale = standardize(x)
    coef, intercept = fit_logreg((x - mean) / scale, y, best)
    widen, interval = fit_interval(rows, infos)
    names = [n for n, _ in PERIODS]
    arrays = {
        "mean": mean, "scale": scale, "coef": coef, "intercept": np.array(intercept),
        "platt": np.array(platt), "threshold": np.array(threshold), "widen": np.array(widen),
        "min_halfwidth": np.array([MIN_HALFWIDTH[n] for n in names], dtype=np.float64),
    }
    corpus_hash = hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()
    meta = {
        "version": version,
        "model": f"logistic regression (Newton, L2={best}) on {len(FEATURES)} standardized series features, "
                 f"Platt-calibrated, threshold {threshold}",
        "features": list(FEATURES),
        "weights": {name: round(float(w), 4) for name, w in zip(FEATURES, coef)},
        "training_data": {
            "source": "synthetic corpus (app/recurring_scorer/corpus.py)", "seed": seed, "rows": len(rows),
            "positives": int(y.sum()), "sha256": corpus_hash,
        },
        "validation": {
            "method": f"{FOLDS}-fold cross-validation on the synthetic corpus (out-of-fold, calibrated)",
            "l2_grid_log_loss": {str(k): round(v[1], 4) for k, v in grid.items()},
            "chosen": classification(p, y, threshold),
            "before_calibration": classification(sigmoid(z), y, 0.5),
            "next_date_interval": {"target_coverage": TARGET_COVERAGE, "widen": widen, **interval},
        },
        "libraries": {"numpy": np.__version__},
    }
    save(out_dir, arrays, meta)
    return meta


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--version", default=VERSION)
    args = ap.parse_args(argv)
    meta = train(args.out, args.seed, args.version)
    print(f"wrote {args.out} ({meta['training_data']['rows']} rows)")
    print(json.dumps(meta["validation"], indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
