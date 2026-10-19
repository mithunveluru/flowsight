"""Train the categorizer on the synthetic corpus and write a registry artifact.

  pip install -r requirements-train.txt
  python -m app.categorizer.train --out app/categorizer/artifact

The same seed and pinned libraries give byte-identical model.npz and meta.json. Validation
is grouped 5-fold cross-validation that holds out whole merchants; its out-of-fold logits
also fit the softmax temperature. `python -m app.categorizer.install` registers the result.
"""

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import sklearn
from scipy.optimize import minimize_scalar
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold

from app.categorizer import corpus
from app.categorizer.model import CategoryModel, features, save, softmax

VERSION = "tfidf-lr-1"
SEED = 7
C_GRID = (2.0, 8.0, 32.0)
FOLDS = 5
MIN_DF = 2


def fit(rows: list[dict], c: float) -> tuple[TfidfVectorizer, LogisticRegression]:
    vectorizer = TfidfVectorizer(analyzer=features, sublinear_tf=True, min_df=MIN_DF, dtype=np.float64)
    x = vectorizer.fit_transform(rows)
    clf = LogisticRegression(C=c, solver="saga", max_iter=3000, random_state=0)
    clf.fit(x, [r["category"] for r in rows])
    return vectorizer, clf


def out_of_fold_logits(rows: list[dict], c: float, classes: list[str], folds: int) -> np.ndarray:
    groups = [r["group"] for r in rows]
    logits = np.zeros((len(rows), len(classes)))
    for train_idx, val_idx in GroupKFold(n_splits=folds).split(rows, groups=groups):
        vectorizer, clf = fit([rows[i] for i in train_idx], c)
        z = clf.decision_function(vectorizer.transform([rows[i] for i in val_idx]))
        # a fold can miss a rare class; keep the column order fixed
        full = np.full((len(val_idx), len(classes)), -1e9)
        for k, label in enumerate(clf.classes_):
            full[:, classes.index(label)] = z[:, k]
        logits[val_idx] = full
    return logits


def fit_temperature(logits: np.ndarray, labels: np.ndarray) -> float:
    def nll(t: float) -> float:
        p = softmax(logits / t)
        return float(-np.mean(np.log(p[np.arange(len(labels)), labels] + 1e-12)))
    return float(minimize_scalar(nll, bounds=(0.05, 20.0), method="bounded", options={"xatol": 1e-4}).x)


def scores(logits: np.ndarray, labels: np.ndarray, temperature: float) -> dict[str, float]:
    p = softmax(logits / temperature)
    pred = p.argmax(axis=1)
    conf = p.max(axis=1)
    correct = pred == labels
    f1s = []
    for k in range(logits.shape[1]):
        tp = np.sum((pred == k) & (labels == k))
        denom = np.sum(pred == k) + np.sum(labels == k)
        f1s.append(2 * tp / denom if denom else 0.0)
    return {
        "accuracy": round(float(correct.mean()), 4),
        "macro_f1": round(float(np.mean(f1s)), 4),
        "ece": round(ece(conf, correct), 4),
        "nll": round(float(-np.mean(np.log(p[np.arange(len(labels)), labels] + 1e-12))), 4),
    }


def ece(confidence: np.ndarray, correct: np.ndarray, bins: int = 10) -> float:
    edges = np.minimum((confidence * bins).astype(int), bins - 1)
    total = 0.0
    for b in range(bins):
        mask = edges == b
        if mask.any():
            total += mask.sum() * abs(confidence[mask].mean() - correct[mask].mean())
    return float(total / len(confidence))


def train(out_dir: Path, seed: int = SEED, version: str = VERSION, c_grid=C_GRID,
          rows: list[dict] | None = None, folds: int = FOLDS) -> dict:
    """rows defaults to the corpus for `seed`; tests pass a slice of it."""
    rows = corpus.build(seed) if rows is None else rows
    classes = sorted({r["category"] for r in rows})
    labels = np.array([classes.index(r["category"]) for r in rows])

    candidates = {}
    for c in c_grid:
        logits = out_of_fold_logits(rows, c, classes, folds)
        t = fit_temperature(logits, labels)
        candidates[c] = (logits, t, scores(logits, labels, t))
    best_c = min(candidates, key=lambda c: (candidates[c][2]["nll"], c))
    logits, temperature, validation = candidates[best_c]
    uncalibrated = scores(logits, labels, 1.0)

    vectorizer, clf = fit(rows, best_c)
    order = [list(clf.classes_).index(c) for c in classes]
    arrays = {
        "vocabulary": np.array(vectorizer.get_feature_names_out(), dtype=str),
        "idf": vectorizer.idf_.astype(np.float32),
        "coef": clf.coef_[order].astype(np.float32),
        "intercept": clf.intercept_[order].astype(np.float32),
        "classes": np.array(classes, dtype=str),
        "temperature": np.array(temperature),
    }
    corpus_hash = hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()
    meta = {
        "version": version,
        "model": f"sublinear tf-idf (min_df={MIN_DF}) + multinomial logistic regression (saga), C={best_c}, "
                 f"softmax temperature {round(temperature, 4)}",
        "features": "merchant words, description words and word pairs, character 2-5-grams of both, direction",
        "training_data": {
            "source": "synthetic corpus (app/categorizer/corpus.py)",
            "seed": seed,
            "rows": len(rows),
            "merchant_groups": len({r["group"] for r in rows}),
            "sha256": corpus_hash,
            "per_category": dict(sorted(Counter(r["category"] for r in rows).items())),
            "per_region": dict(sorted(Counter(r["region"] for r in rows).items())),
        },
        "validation": {
            "method": f"GroupKFold({folds}) over merchants on the synthetic corpus",
            "c_grid": {str(c): candidates[c][2] for c in c_grid},
            "chosen": validation,
            "before_temperature": uncalibrated,
        },
        "vocabulary_size": int(len(arrays["vocabulary"])),
        "libraries": {"scikit-learn": sklearn.__version__, "numpy": np.__version__},
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
    model = CategoryModel.load(args.out)
    print(f"wrote {args.out} ({meta['training_data']['rows']} rows, {meta['vocabulary_size']} features, "
          f"{len(model.classes)} classes)")
    print(json.dumps(meta["validation"]["chosen"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
