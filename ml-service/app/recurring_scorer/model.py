"""Recurring-payment scoring for one merchant's charges: features, schedule and inference.

Everything here needs numpy only and imports nothing from app.*, so the offline eval can
load this file by path. Training lives in app.recurring_scorer.train.
"""

import calendar
import io
import json
import math
import zipfile
from datetime import date, timedelta
from pathlib import Path

import numpy as np

WEIGHTS = "model.npz"
META = "meta.json"

PERIODS = (("WEEKLY", 7.0), ("BIWEEKLY", 14.0), ("MONTHLY", 30.44), ("QUARTERLY", 91.31), ("ANNUAL", 365.25))
MONTH = 30.44
# beyond this log-distance from every known period the cadence is called irregular
SNAP_LIMIT = math.log(1.35)
CATEGORIES = ("SUBSCRIPTIONS", "UTILITIES", "FINANCE", "ENTERTAINMENT", "EDUCATION", "HEALTHCARE",
              "FOOD_DINING", "GROCERIES", "SHOPPING", "TRANSPORTATION", "TRAVEL", "OTHER", "TRANSFER")
FEATURES = (
    ["log_count", "log_span", "snap_error", "regular", "gaps", "interval_rcv", "interval_cv",
     "amount_rcv", "amount_cv", "amount_repeat", "amount_trend", "calendar", "log_per_month",
     "same_day", "recency", "variable_short", "irregular_frequent"]
    + [f"period_{name.lower()}" for name, _ in PERIODS] + ["period_none"]
    + [f"category_{c.lower()}" for c in CATEGORIES] + ["category_unknown"]
)


def _mad(x: np.ndarray) -> float:
    return float(np.median(np.abs(x - np.median(x)))) if len(x) else 0.0


def _add_months(d: date, months: int, day: int) -> date:
    y, m = divmod(d.month - 1 + months, 12)
    y, m = d.year + y, m + 1
    return date(y, m, min(day, calendar.monthrange(y, m)[1]))


def _concentration(values: np.ndarray, cycle: float) -> float:
    """Mean resultant length of values on a circle of the given length (1 = all the same)."""
    if len(values) < 2:
        return 0.0
    angles = 2 * np.pi * values / cycle
    return float(np.hypot(np.cos(angles).mean(), np.sin(angles).mean()))


def snap(interval: float) -> tuple[str | None, float, float]:
    """(period name or None, its nominal days, log-distance to it)."""
    if interval <= 0:
        return None, 0.0, 1.0
    name, days = min(PERIODS, key=lambda p: abs(math.log(interval / p[1])))
    err = abs(math.log(interval / days))
    return (name if err <= SNAP_LIMIT else None), days, err


class Series:
    """One run of charges: dates relative to as_of (days <= 0) and amounts, sorted by date."""

    def __init__(self, as_of: date, days, amounts):
        order = np.argsort(np.asarray(days, dtype=np.int64), kind="stable")
        self.as_of = as_of
        self.days = np.asarray(days, dtype=np.int64)[order]
        self.amounts = np.asarray(amounts, dtype=np.float64)[order]
        self.dates = [as_of + timedelta(days=int(d)) for d in self.days]
        gaps = np.diff(self.days).astype(np.float64)
        self.same_day = float(np.mean(gaps == 0)) if len(gaps) else 0.0
        self.intervals = gaps[gaps > 0]
        self.median_interval = float(np.median(self.intervals)) if len(self.intervals) else 0.0
        self.period, self.nominal, self.snap_error = snap(self.median_interval)


def split_parallel(s: Series) -> tuple[Series, Series] | None:
    """Two monthly series of one price interleaved (one service billed on two accounts)."""
    if len(s.days) < 6 or not 8 <= s.median_interval <= 22:
        return None
    # a steady 14-day cycle is one biweekly charge, not two monthly ones
    if np.mean(np.abs(s.intervals - 14) <= 1) >= 0.75:
        return None
    doms = np.array([d.day for d in s.dates], dtype=np.float64)
    order = np.argsort(doms, kind="stable")
    best = None
    for cut in range(2, len(order) - 1):
        a, b = order[:cut], order[cut:]
        spread = float(np.sum((doms[a] - doms[a].mean()) ** 2) + np.sum((doms[b] - doms[b].mean()) ** 2))
        if best is None or spread < best[0]:
            best = (spread, a, b)
    _, a, b = best
    parts = []
    for idx in (np.sort(a), np.sort(b)):
        part = Series(s.as_of, s.days[idx], s.amounts[idx])
        if part.period != "MONTHLY" or len(part.intervals) == 0:
            return None
        # billing on two accounts keeps each account's day of month; a 14-day cycle drifts through the month
        if len(idx) < 3 or _mad(doms[idx]) > 1:
            return None
        if np.mean(np.abs(part.intervals - MONTH) <= 4) < 0.75:
            return None
        parts.append(part)
    m0, m1 = np.median(parts[0].amounts), np.median(parts[1].amounts)
    if abs(m0 - m1) > 0.05 * max(m0, m1):
        return None
    if abs(np.median(doms[a]) - np.median(doms[b])) < 6:
        return None
    return parts[0], parts[1]


def features(s: Series, category: str | None, series_count: int, total_count: int) -> np.ndarray:
    n = total_count
    iv = s.intervals
    span = float(s.days[-1] - s.days[0]) if len(s.days) else 0.0
    nominal = s.nominal or max(s.median_interval, 1.0)
    if len(iv):
        k = np.maximum(1, np.round(iv / nominal))
        near = np.abs(iv - k * nominal) <= np.maximum(2.0, 0.15 * k * nominal)
        regular = float(np.mean(near & (k == 1)))
        gaps = float(np.mean(near & (k >= 2)))
        interval_rcv = _mad(iv) / max(s.median_interval, 1.0)
        interval_cv = min(3.0, float(np.std(iv) / max(np.mean(iv), 1.0)))
    else:
        regular = gaps = 0.0
        interval_rcv, interval_cv = 1.0, 3.0
    a = s.amounts
    med = float(np.median(a))
    amount_rcv = min(3.0, _mad(a) / med) if med > 0 else 3.0
    amount_cv = min(3.0, float(np.std(a) / np.mean(a))) if np.mean(a) > 0 else 3.0
    rounded = np.round(a / max(med, 1e-9), 3)
    _, counts = np.unique(rounded, return_counts=True)
    amount_repeat = float(counts.max() / len(a))
    if len(a) >= 3 and med > 0:
        t = np.arange(len(a), dtype=np.float64)
        slope = np.polyfit(t, np.log(np.maximum(a, 1e-9)), 1)[0]
        amount_trend = float(min(1.0, abs(slope) * len(a)))
    else:
        amount_trend = 0.0
    if s.nominal >= 25:
        calendar_conc = _concentration(np.array([d.day for d in s.dates], dtype=np.float64), MONTH)
    elif s.nominal > 0:
        calendar_conc = _concentration(np.array([d.weekday() for d in s.dates], dtype=np.float64), 7.0)
    else:
        calendar_conc = 0.0
    per_month = n / max(span / MONTH, 1.0)
    recency = min(4.0, float(-s.days[-1]) / nominal) if len(s.days) else 4.0
    short = s.period in ("WEEKLY", "BIWEEKLY")
    row = [math.log1p(n), math.log1p(span), min(1.0, s.snap_error), regular, gaps, min(3.0, interval_rcv),
           interval_cv, amount_rcv, amount_cv, amount_repeat, amount_trend, calendar_conc,
           math.log(per_month / max(series_count, 1)), s.same_day, recency,
           amount_rcv if short else 0.0, (1.0 - regular) * min(1.0, per_month / 4)]
    row += [1.0 if s.period == name else 0.0 for name, _ in PERIODS] + [1.0 if s.period is None else 0.0]
    cat = category if category in CATEGORIES else None
    row += [1.0 if cat == c else 0.0 for c in CATEGORIES] + [1.0 if cat is None else 0.0]
    return np.asarray(row, dtype=np.float64)


def next_charge(s: Series) -> int | None:
    """Days from as_of to the next charge on this series' schedule (may be negative)."""
    if s.period is None or not len(s.days):
        return None
    last = s.dates[-1]
    if s.nominal >= 25:
        months = int(round(s.nominal / MONTH))
        day = int(np.median([d.day for d in s.dates[-6:]]))
        nxt = _add_months(last, months, day)
        if (nxt - last).days < 0.6 * s.nominal:
            nxt = _add_months(last, months + 1, day)
    else:
        nxt = last + timedelta(days=int(round(s.nominal)))
    return (nxt - s.as_of).days


def interval_spread(s: Series) -> tuple[float, float]:
    """Early and late deviation of past intervals from the nominal period (10th/90th pct)."""
    iv = s.intervals
    if s.period is None or not len(iv):
        return 0.0, 0.0
    k = np.maximum(1, np.round(iv / s.nominal))
    dev = iv - k * s.nominal
    dev = dev[np.abs(dev) <= np.maximum(2.0, 0.25 * k * s.nominal)]
    if not len(dev):
        return 0.0, 0.0
    return max(0.0, -float(np.percentile(dev, 10))), max(0.0, float(np.percentile(dev, 90)))


def analyze(as_of: date, days, amounts, category: str | None) -> dict:
    """Series features plus the deterministic parts of the answer (period, schedule, amount)."""
    whole = Series(as_of, days, amounts)
    parts = split_parallel(whole)
    primary = max(parts, key=lambda p: len(p.days)) if parts else whole
    x = features(primary, category, 2 if parts else 1, len(whole.days))
    nexts = [n for n in (next_charge(p) for p in (parts or (whole,))) if n is not None]
    early, late = interval_spread(primary)
    return {"x": x, "series": primary, "parts": parts, "period": primary.period,
            "interval_days": round(primary.median_interval, 2), "next": min(nexts) if nexts else None,
            "early": early, "late": late, "amount": float(np.median(primary.amounts[-3:])),
            "charges_per_period": 2 if parts else 1}


class RecurringModel:
    """Standardized logistic regression with Platt calibration and a widened next-date interval."""

    def __init__(self, mean, scale, coef, intercept, platt, threshold, widen, min_halfwidth, meta=None):
        self.mean = np.asarray(mean, dtype=np.float64)
        self.scale = np.asarray(scale, dtype=np.float64)
        self.coef = np.asarray(coef, dtype=np.float64)
        self.intercept = float(intercept)
        self.platt = np.asarray(platt, dtype=np.float64)
        self.threshold = float(threshold)
        self.widen = float(widen)
        self.min_halfwidth = np.asarray(min_halfwidth, dtype=np.float64)
        self.meta = meta or {}

    @classmethod
    def load(cls, directory: Path) -> "RecurringModel":
        directory = Path(directory)
        with np.load(directory / WEIGHTS, allow_pickle=False) as z:
            a = {k: z[k] for k in z.files}
        meta = json.loads((directory / META).read_text(encoding="utf-8"))
        return cls(a["mean"], a["scale"], a["coef"], float(a["intercept"]), a["platt"], float(a["threshold"]),
                   float(a["widen"]), a["min_halfwidth"], meta)

    def logit(self, x: np.ndarray) -> np.ndarray:
        return ((np.atleast_2d(x) - self.mean) / self.scale) @ self.coef + self.intercept

    def probability(self, x: np.ndarray) -> np.ndarray:
        return sigmoid(self.platt[0] * self.logit(x) + self.platt[1])

    def halfwidths(self, period: str | None, early: float, late: float) -> tuple[int, int]:
        names = [p for p, _ in PERIODS]
        floor = float(self.min_halfwidth[names.index(period)]) if period in names else 3.0
        return (int(math.ceil(max(floor, self.widen * early))), int(math.ceil(max(floor, self.widen * late))))

    def score(self, as_of: date, groups: list[dict]) -> list[dict]:
        out = []
        for g in groups:
            info = analyze(as_of, g["days"], g["amounts"], g.get("category"))
            p = float(self.probability(info["x"])[0])
            nxt = info["next"]
            lo, hi = self.halfwidths(info["period"], info["early"], info["late"])
            recurring = p >= self.threshold and info["period"] is not None
            out.append({
                "probability": round(p, 4), "recurring": recurring, "period": info["period"],
                "interval_days": info["interval_days"], "next_in_days": nxt,
                "next_low_in_days": None if nxt is None else nxt - lo,
                "next_high_in_days": None if nxt is None else nxt + hi,
                "amount": round(info["amount"], 4), "charges_per_period": info["charges_per_period"],
            })
        return out


def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-np.clip(z, -40, 40)))


def save(directory: Path, arrays: dict, meta: dict) -> None:
    """Writes the weights and metadata byte-for-byte reproducibly (fixed zip timestamps)."""
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(directory / WEIGHTS, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name in sorted(arrays):
            buf = io.BytesIO()
            np.lib.format.write_array(buf, np.asarray(arrays[name]), allow_pickle=False)
            info = zipfile.ZipInfo(f"{name}.npy", date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, buf.getvalue())
    (directory / META).write_text(json.dumps(meta, indent=2, sort_keys=True) + "\n", encoding="utf-8")
