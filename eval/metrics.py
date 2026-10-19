"""Metric primitives shared by the receipt, categorization and merchant scorers."""
import re
import unicodedata
from decimal import Decimal
from difflib import SequenceMatcher

# Similarity at or above this counts as a fuzzy match. 0.85 tolerates a couple of OCR
# character errors in a 15-20 character name but not a different word.
FUZZY_THRESHOLD = 0.85
ITEM_NAME_THRESHOLD = 0.80
BINS = 10

LEGAL_WORDS = {"pvt", "ltd", "llc", "inc", "co", "corp", "company", "the", "plc", "llp", "fze", "fzco", "est"}
# Metrics where a smaller value is better; everything else is higher-is-better.
LOWER_IS_BETTER = {"ece", "alias_wrong", "irregular_fp_rate", "fp_rate"}


def normalize_name(text) -> str:
    if not text:
        return ""
    s = unicodedata.normalize("NFKC", str(text)).casefold()
    s = s.replace("&", " and ")
    s = re.sub(r"['’`]", "", s)
    s = re.sub(r"[^\w]+|_", " ", s)
    return " ".join(s.split())


def fuzzy_key(text) -> str:
    """normalize_name minus store numbers and legal-form words."""
    return " ".join(t for t in normalize_name(text).split() if not t.isdigit() and t not in LEGAL_WORDS)


def similarity(a, b) -> float:
    ka, kb = fuzzy_key(a), fuzzy_key(b)
    if not ka or not kb:
        return 0.0
    return SequenceMatcher(None, ka, kb).ratio()


def names_equal(a, b) -> bool:
    return bool(normalize_name(a)) and normalize_name(a) == normalize_name(b)


def to_decimal(value):
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value))
    except ArithmeticError:
        return None


def amount_equal(a, b) -> bool:
    da, db = to_decimal(a), to_decimal(b)
    return da is not None and db is not None and abs(da - db) < Decimal("0.005")


def match_line_items(predicted, truth):
    """Greedy one-to-one matching; a pair matches when the names are similar and the
    predicted price equals the line amount or the unit price. Returns matched count."""
    used = set()
    matched = 0
    for t in truth:
        for i, p in enumerate(predicted):
            if i in used:
                continue
            price_ok = amount_equal(p.get("price"), t["amount"]) or amount_equal(p.get("price"), t["unit_price"])
            if price_ok and similarity(p.get("name"), t["name"]) >= ITEM_NAME_THRESHOLD:
                used.add(i)
                matched += 1
                break
    return matched


def f1(tp, n_pred, n_true):
    precision = tp / n_pred if n_pred else 0.0
    recall = tp / n_true if n_true else 0.0
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def calibration(pairs):
    """pairs: (confidence, correct). Returns (ece, reliability rows) over equal-width bins;
    (None, []) when nothing carries a confidence."""
    pairs = [(float(c), bool(ok)) for c, ok in pairs if c is not None]
    if not pairs:
        return None, []
    rows, ece = [], 0.0
    for b in range(BINS):
        lo, hi = b / BINS, (b + 1) / BINS
        members = [(c, ok) for c, ok in pairs if lo <= c < hi or (b == BINS - 1 and c == 1.0)]
        if not members:
            continue
        conf = sum(c for c, _ in members) / len(members)
        acc = sum(ok for _, ok in members) / len(members)
        ece += len(members) / len(pairs) * abs(acc - conf)
        rows.append({"bin": f"{lo:.1f}-{hi:.1f}", "n": len(members), "mean_conf": round(conf, 4), "accuracy": round(acc, 4)})
    return round(ece, 4), rows


def macro_f1(truth, predicted):
    labels = sorted(set(truth))
    per_class = {}
    for label in labels:
        tp = sum(1 for t, p in zip(truth, predicted) if t == label and p == label)
        per_class[label] = round(f1(tp, sum(1 for p in predicted if p == label), sum(1 for t in truth if t == label)), 4)
    return round(sum(per_class.values()) / len(labels), 4) if labels else 0.0, per_class


def compare(current, baseline):
    """List of human-readable regressions of `current` against `baseline` (both score dicts)."""
    tol = baseline.get("tolerance", {})
    overall_tol = tol.get("overall", 0.0)
    slice_items = tol.get("slice_items")
    problems = []

    def check(scope, name, new, old, allowed):
        if new is None or old is None:
            if old is not None and new is None:
                problems.append(f"{scope} {name}: missing (baseline {old})")
            return
        drop = (new - old) if name in LOWER_IS_BETTER else (old - new)
        if drop > allowed + 1e-9:
            problems.append(f"{scope} {name}: {new:.4f} vs baseline {old:.4f} (allowed {allowed:.4f})")

    for name, old in baseline["metrics"].items():
        check("overall", name, current["metrics"].get(name), old, overall_tol)
    if slice_items is not None:
        for key, old_slice in baseline.get("slices", {}).items():
            new_slice = current.get("slices", {}).get(key)
            if new_slice is None:
                problems.append(f"slice {key}: missing")
                continue
            for name in current.get("slice_gated", []):
                if name in old_slice:
                    check(f"slice {key}", name, new_slice.get(name), old_slice[name], slice_items / old_slice["n"])
    return problems


def format_table(rows, columns):
    """Plain fixed-width table; floats shown with 3 decimals, None as n/a."""
    def cell(v):
        if v is None:
            return "n/a"
        return f"{v:.3f}" if isinstance(v, float) else str(v)

    data = [[cell(r.get(c)) for c in columns] for r in rows]
    widths = [max(len(c), *(len(d[i]) for d in data)) if data else len(c) for i, c in enumerate(columns)]
    out = ["  ".join(c.ljust(w) for c, w in zip(columns, widths)), "  ".join("-" * w for w in widths)]
    out += ["  ".join(v.ljust(w) for v, w in zip(d, widths)) for d in data]
    return "\n".join(out)
