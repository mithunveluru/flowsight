"""Monthly cash-flow forecasts with prediction intervals.

Mirrors com.flowsight.ml.forecast.SeriesForecaster / CashFlowForecaster in the backend, which
serves the same forecasts when ml-service is off; the eval backtest scores both on the same
histories. Plain Python on purpose: sums run in the same order as the Java code, so the two
agree to rounding. Free of app.* imports so the eval and calibration can load it by path.

Step k is k months after the last complete month (k = 1 is the current calendar month).
"""

import calendar
import json
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

PRIORS = "priors.json"
MAX_MONTHS = 24
FIRST_MONTH_GRACE_DAYS = 7
SHRINK_PSEUDO_COUNT = 6.0
MIN_SELECTION_MONTHS = 6
SELECTION_ORIGINS = 9
MIN_PROFILE_SHARE = 0.2
SES_ALPHA = 0.3
TREND_DAMPING = 0.8
FLOOR_SHARE = 0.02
# With little history a series' own month-to-date or single month is too noisy a yardstick for its
# errors: income and spend errors are scaled by at least the user's whole monthly flow (income plus
# spend, extrapolated from the month so far) with no complete month, and a quarter of it with one.
NO_HISTORY_FLOOR_SHARE = 1.0
ONE_MONTH_FLOOR_SHARE = 0.25
MAX_STEP = 4
CANDIDATES = ("recent-mean", "recent-median", "exponential-smoothing", "seasonal-deviation", "damped-trend")


def _sum(y, lo, hi):
    s = 0.0
    for i in range(lo, hi):
        s += y[i]
    return s


def _median(values):
    v = sorted(values)
    n = len(v)
    return v[n // 2] if n % 2 == 1 else (v[n // 2 - 1] + v[n // 2]) / 2


def predict(method, y, length, k):
    """Forecast k months after y[length - 1], fitted on y[:length]."""
    if method == "recent-mean":
        lo = max(0, length - 3)
        return _sum(y, lo, length) / min(3, length)
    if method == "recent-median":
        return _median(y[max(0, length - 6):length])
    if method == "exponential-smoothing":
        level = y[0]
        for i in range(1, length):
            level = SES_ALPHA * y[i] + (1 - SES_ALPHA) * level
        return level
    if method == "seasonal-deviation":
        if length < 13:
            return predict("recent-median", y, length, k)
        t = length - 1 + k
        level = predict("recent-median", y, length, k)
        devs, count = 0.0, 0
        if t - 12 <= length - 1:
            devs += y[t - 12] - _sum(y, length - 12, length) / 12
            count += 1
        if length >= 24 and t - 24 >= 0:
            devs += y[t - 24] - _sum(y, length - 24, length - 12) / 12
            count += 1
        if count == 0:
            return level
        phi = 0.5 if count == 1 else 0.7
        return max(0.0, level + phi * devs / count)
    if method == "damped-trend":
        if length < 9:
            return predict("recent-median", y, length, k)
        lo = max(0, length - 12)
        m = length - lo
        slopes = [(y[lo + j] - y[lo + i]) / (j - i) for i in range(m) for j in range(i + 1, m)]
        b = _median(slopes)
        level = _median([y[lo + i] - b * (i - (m - 1)) for i in range(m)])
        damp, phi = 0.0, 1.0
        for _ in range(k):
            phi *= TREND_DAMPING
            damp += phi
        return max(0.0, level + b * damp)
    raise ValueError(method)


def _origins(n, steps):
    for j in range(max(3, n - SELECTION_ORIGINS), n):
        for k in range(1, steps + 1):
            if j - 1 + k >= n:
                break
            yield j, k


def select(y, steps):
    n = len(y)
    err = [0.0] * len(CANDIDATES)
    count = 0
    for j, k in _origins(n, steps):
        actual = y[j - 1 + k]
        for m, method in enumerate(CANDIDATES):
            err[m] += abs(actual - predict(method, y, j, k))
        count += 1
    if count == 0:
        return "recent-mean"
    best = 0
    for m in range(1, len(CANDIDATES)):
        if err[m] / count < err[best] / count:
            best = m
    return CANDIDATES[best]


def _mean_abs_recent(y):
    n = len(y)
    lo = max(0, n - 12)
    return _sum([abs(v) for v in y], lo, n) / (n - lo)


class Priors:
    """Global scaled-error quantiles per (kind, history bucket, step); see calibrate.py."""

    def __init__(self, raw: dict):
        self.version = raw.get("version", "unknown")
        self.q = raw.get("q", {})
        self.mad = raw.get("mad", {})

    @classmethod
    def load(cls, path: Path) -> "Priors":
        return cls(json.loads(Path(path).read_text(encoding="utf-8")))

    @staticmethod
    def key(kind, months, step):
        if months <= 3:
            bucket = str(months)
        elif months <= 6:
            bucket = "4-6"
        elif months <= 12:
            bucket = "7-12"
        else:
            bucket = "13+"
        return f"{kind}|{bucket}|{min(step, MAX_STEP)}"


def _with_intervals(name, kind, method, n, scale, point, residuals, bt_abs, bt_actual, priors):
    steps, ratios = [], []
    ratio = 1.0
    for k in range(1, len(point) + 1):
        key = Priors.key(kind, n, k)
        p = point[k - 1]
        step = {"k": k, "point": p}
        mad = priors.mad.get(key) if priors else None
        if mad and residuals:
            own = _median([abs(e) for e in residuals])
            ratio = ((len(residuals) * own + SHRINK_PSEUDO_COUNT * mad) / (len(residuals) + SHRINK_PSEUDO_COUNT)) / mad
        q = priors.q.get(key) if priors else None
        if q:
            w = scale * ratio
            lo80, hi80, lo95, hi95 = (p + w * q["0.1"], p + w * q["0.9"], p + w * q["0.025"], p + w * q["0.975"])
            if kind != "net":
                lo80, lo95, hi80, hi95 = max(0.0, lo80), max(0.0, lo95), max(0.0, hi80), max(0.0, hi95)
            step.update(lower80=lo80, upper80=hi80, lower95=lo95, upper95=hi95)
        ratios.append(ratio)
        steps.append(step)
    points = len(residuals)
    return {
        "name": name, "kind": kind, "method": method, "data_months": n, "scale": scale, "steps": steps,
        "backtest_points": points,
        "backtest_mae": bt_abs / points if points else None,
        "backtest_wape": bt_abs / bt_actual if points and bt_actual > 0 else None,
        "interval_ratio": ratio, "own_weight": points / (points + SHRINK_PSEUDO_COUNT),
    }


def forecast_series(name, kind, y, month_to_date, same_days, steps, floor, priors=None):
    n = len(y)
    residuals, bt_abs, bt_actual = [], 0.0, 0.0
    if n == 0:
        method = "month-to-date"
        point = [month_to_date] * steps
        scale = max(month_to_date, floor)
    elif n < MIN_SELECTION_MONTHS:
        total = _sum(y, 0, n)
        share = same_days / total if total > 0 else 0.0
        lo = max(0, n - 3)
        recent = _sum(y, lo, n)
        count = n - lo
        if share >= MIN_PROFILE_SHARE:
            method = "recent-mean+month-to-date"
            estimate = month_to_date / share
            weight = min(1.0, share)
            point = [(recent + weight * estimate) / (count + weight)] * steps
            scale = max((recent + estimate) / (count + 1), floor)
        else:
            method = "recent-mean"
            point = [recent / count] * steps
            scale = max(_mean_abs_recent(y), floor)
    else:
        method = select(y, steps)
        point = [predict(method, y, n, k) for k in range(1, steps + 1)]
        scale = max(_mean_abs_recent(y), floor)
        for j, k in _origins(n, steps):
            actual = y[j - 1 + k]
            f = predict(method, y, j, k)
            residuals.append((actual - f) / scale)
            bt_abs += abs(actual - f)
            bt_actual += abs(actual)
    return _with_intervals(name, kind, method, n, scale, point, residuals, bt_abs, bt_actual, priors)


def forecast_net(income, spend, income_y, spend_y, floor, priors=None):
    steps = len(income["steps"])
    n = len(income_y)
    point = [income["steps"][k]["point"] - spend["steps"][k]["point"] for k in range(steps)]
    scale = max(income["scale"] + spend["scale"], floor)
    residuals, bt_abs, bt_actual = [], 0.0, 0.0
    if n >= MIN_SELECTION_MONTHS:
        mi, ms = select(income_y, steps), select(spend_y, steps)
        for j, k in _origins(n, steps):
            actual = income_y[j - 1 + k] - spend_y[j - 1 + k]
            f = predict(mi, income_y, j, k) - predict(ms, spend_y, j, k)
            residuals.append((actual - f) / scale)
            bt_abs += abs(actual - f)
            bt_actual += abs(income_y[j - 1 + k]) + abs(spend_y[j - 1 + k])
    return _with_intervals("net", "net", "income-minus-spend", n, scale, point, residuals, bt_abs, bt_actual, priors)


@dataclass
class History:
    """Complete months (oldest first) plus the current month so far, as MonthlyHistory builds them."""
    today: date
    months: list
    income: list
    spend: list
    categories: dict = field(default_factory=dict)
    income_to_date: float = 0.0
    spend_to_date: float = 0.0
    categories_to_date: dict = field(default_factory=dict)
    income_same_days: float = 0.0
    spend_same_days: float = 0.0
    categories_same_days: dict = field(default_factory=dict)


def day_fraction(today: date) -> float:
    return today.day / calendar.monthrange(today.year, today.month)[1]


def _month_index(d):
    return d.year * 12 + d.month - 1


def history_from_transactions(txns, today: date, earliest: date | None = None) -> History:
    """txns: iterable of (date, "DEBIT"|"CREDIT", category, amount); only those up to today count."""
    visible = [t for t in txns if t[0] <= today]
    if earliest is None and visible:
        earliest = min(t[0] for t in visible)
    current = _month_index(today)
    months = []
    if earliest is not None and earliest <= today:
        first = _month_index(earliest) + (0 if earliest.day <= FIRST_MONTH_GRACE_DAYS else 1)
        months = list(range(max(first, current - MAX_MONTHS), current))
    n = len(months)
    start = months[0] if months else current
    h = History(today=today, months=months, income=[0.0] * n, spend=[0.0] * n)
    for d, typ, cat, amount in visible:
        m = _month_index(d)
        credit = typ == "CREDIT"
        if m == current:
            if credit:
                h.income_to_date += amount
            else:
                h.spend_to_date += amount
                if cat is not None:
                    h.categories_to_date[cat] = h.categories_to_date.get(cat, 0.0) + amount
                    h.categories.setdefault(cat, [0.0] * n)
            continue
        if m < start:
            continue
        i = m - start
        early = d.day <= today.day
        if credit:
            h.income[i] += amount
            if early:
                h.income_same_days += amount
        else:
            h.spend[i] += amount
            if early:
                h.spend_same_days += amount
            if cat is not None:
                h.categories.setdefault(cat, [0.0] * n)[i] += amount
                if early:
                    h.categories_same_days[cat] = h.categories_same_days.get(cat, 0.0) + amount
    return h


MIN_ELAPSED_SHARE = 0.35


def floor_of(income, spend, income_to_date, spend_to_date, day_fraction, kind="category"):
    n = len(income)
    if n > 0:
        lo = max(0, n - 12)
        level = (_sum(income, lo, n) + _sum(spend, lo, n)) / (n - lo)
    else:
        level = (income_to_date + spend_to_date) / max(day_fraction, MIN_ELAPSED_SHARE)
    share = FLOOR_SHARE
    if kind in ("income", "spend") and n <= 1:
        share = NO_HISTORY_FLOOR_SHARE if n == 0 else ONE_MONTH_FLOOR_SHARE
    return max(1.0, share * level)


def forecast_history(h: History, steps: int, priors: Priors | None = None) -> list[dict]:
    """income, spend, net, then categories in the order given."""
    frac = day_fraction(h.today)
    floor = floor_of(h.income, h.spend, h.income_to_date, h.spend_to_date, frac)
    totals_floor = floor_of(h.income, h.spend, h.income_to_date, h.spend_to_date, frac, "income")
    income = forecast_series("income", "income", h.income, h.income_to_date, h.income_same_days, steps, totals_floor,
                             priors)
    spend = forecast_series("spend", "spend", h.spend, h.spend_to_date, h.spend_same_days, steps, totals_floor, priors)
    out = [income, spend, forecast_net(income, spend, h.income, h.spend, floor, priors)]
    for cat, y in h.categories.items():
        out.append(forecast_series(cat, "category", y, h.categories_to_date.get(cat, 0.0),
                                   h.categories_same_days.get(cat, 0.0), steps, floor, priors))
    return out
