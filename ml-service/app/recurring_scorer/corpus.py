"""Seeded synthetic training corpus: one merchant's charge history per row, labeled.

Positives are obligations billed on a schedule (weekly to annual) with the usual noise:
billing-day jitter, weekend shifts, month-end clamping, skipped or failed charges, price
steps, metered amounts, late starts, cancellations and one service billed on two accounts.
Negatives are spending that happens often or at random: Poisson visits with weekday or
weekend habits, a weekly shop on a favourite day with a variable basket, renewal processes
such as refuelling, purchases that happen to land about a month apart, and sparse one-off
purchases. Categories are drawn per side with noise
and gaps so the model cannot lean on them alone. Amounts are currency-free (log-uniform
scale); every feature is relative.

Independent of the evaluation set (eval/recurring), which is built by separate code.
"""

import calendar
import math
import random
from datetime import date, timedelta

PERIOD_DAYS = {"WEEKLY": 7, "BIWEEKLY": 14, "MONTHLY": None, "QUARTERLY": 3, "ANNUAL": 12}
POSITIVE_PERIODS = (("MONTHLY", 0.6), ("WEEKLY", 0.12), ("BIWEEKLY", 0.08), ("QUARTERLY", 0.12), ("ANNUAL", 0.08))
POSITIVE_CATEGORIES = (("SUBSCRIPTIONS", 25), ("UTILITIES", 20), ("ENTERTAINMENT", 14), ("FINANCE", 15),
                       ("EDUCATION", 7), ("OTHER", 8), ("HEALTHCARE", 3), ("TRANSFER", 2), ("SHOPPING", 2),
                       ("TRANSPORTATION", 2), ("FOOD_DINING", 1), ("GROCERIES", 1))
NEGATIVE_CATEGORIES = (("FOOD_DINING", 30), ("GROCERIES", 20), ("SHOPPING", 15), ("TRANSPORTATION", 12),
                       ("ENTERTAINMENT", 6), ("HEALTHCARE", 5), ("TRAVEL", 4), ("OTHER", 5), ("TRANSFER", 3),
                       ("SUBSCRIPTIONS", 1), ("UTILITIES", 1), ("EDUCATION", 1), ("FINANCE", 1))
ALL_CATEGORIES = sorted({c for c, _ in POSITIVE_CATEGORIES + NEGATIVE_CATEGORIES})


def _pick(rng: random.Random, weighted):
    names, weights = zip(*weighted)
    return rng.choices(names, weights)[0]


def _add_months(d: date, months: int, day: int) -> date:
    y, m = divmod(d.month - 1 + months, 12)
    y, m = d.year + y, m + 1
    return date(y, m, min(day, calendar.monthrange(y, m)[1]))


def _category(rng: random.Random, weighted) -> str | None:
    r = rng.random()
    if r < 0.12:
        return None
    if r < 0.18:
        return rng.choice(ALL_CATEGORIES)
    return _pick(rng, weighted)


def _base_amount(rng: random.Random) -> float:
    return math.exp(rng.uniform(math.log(1.0), math.log(50000.0)))


def _schedule(rng: random.Random, period: str, first: date, until: date) -> list[date]:
    out = []
    if period in ("WEEKLY", "BIWEEKLY"):
        d = first
        while d <= until:
            out.append(d)
            d += timedelta(days=PERIOD_DAYS[period])
        return out
    step = 1 if period == "MONTHLY" else PERIOD_DAYS[period]
    day = first.day if rng.random() < 0.85 else rng.randint(28, 31)
    k = 0
    while True:
        d = _add_months(first, k * step, day)
        if d > until:
            return out
        out.append(d)
        k += 1


def _jitter(rng: random.Random, period: str, d: date, shift_weekends: bool) -> date:
    if period in ("WEEKLY", "BIWEEKLY"):
        d += timedelta(days=0 if rng.random() < 0.8 else rng.choice([-1, 1]))
    else:
        r = rng.random()
        off = 0 if r < 0.55 else rng.choice([-1, 1]) if r < 0.8 else rng.choice([-3, -2, 2, 3]) if r < 0.95 \
            else rng.choice([-5, -4, 4, 5])
        d += timedelta(days=off)
    if shift_weekends and d.weekday() >= 5:
        d += timedelta(days=7 - d.weekday())
    return d


def positive(rng: random.Random, as_of: date, history_days: int) -> dict:
    period = _pick(rng, POSITIVE_PERIODS)
    start = as_of - timedelta(days=history_days)
    nominal = {"WEEKLY": 7, "BIWEEKLY": 14, "MONTHLY": 30.44, "QUARTERLY": 91.3, "ANNUAL": 365.25}[period]
    lead = rng.uniform(0, nominal * 6) if rng.random() < 0.8 else -rng.uniform(0, max(1, history_days - nominal))
    first = start - timedelta(days=int(lead))
    ended = rng.random() < 0.1
    until = as_of - timedelta(days=int(rng.uniform(1.5, 4) * nominal)) if ended else as_of + timedelta(days=500)
    due = _schedule(rng, period, first, until)
    shift = rng.random() < 0.3 and period != "WEEKLY"
    skip = rng.choice([0.0, 0.0, 0.03, 0.08])
    base = _base_amount(rng)
    metered = period in ("MONTHLY", "QUARTERLY") and rng.random() < 0.3
    sigma = rng.uniform(0.04, 0.35) if metered else 0.0
    season = rng.uniform(0, 0.3) if metered else 0.0
    step_at = rng.uniform(0, 1) if not metered and rng.random() < 0.2 else None
    step = rng.uniform(1.04, 1.4)
    parallel = period == "MONTHLY" and not metered and rng.random() < 0.04

    def amounts_for(dates: list[date]) -> list[float]:
        out = []
        for i, d in enumerate(dates):
            if metered:
                a = base * (1 + season * math.sin(2 * math.pi * d.month / 12)) * math.exp(rng.gauss(0, sigma))
            else:
                a = base * (step if step_at is not None and i >= step_at * len(dates) else 1.0)
            out.append(round(a, 2))
        return out

    series = [due]
    if parallel:
        offset = rng.randint(7, 15)
        series.append([d + timedelta(days=offset) for d in due])
    charges = []
    for s in series:
        dates = [_jitter(rng, period, d, shift) for d in s if rng.random() >= skip or d > as_of]
        charges += list(zip(dates, amounts_for(dates)))
    return _row(as_of, start, charges, 1, _category(rng, POSITIVE_CATEGORIES), active=not ended)


def negative(rng: random.Random, as_of: date, history_days: int) -> dict:
    start = as_of - timedelta(days=history_days)
    base = _base_amount(rng)
    kind = rng.choices(["poisson", "weekly_shop", "renewal", "sparse", "habit", "lookalike"],
                       [35, 15, 15, 15, 10, 10])[0]
    charges = []
    if kind in ("poisson", "habit"):
        rate = math.exp(rng.uniform(math.log(0.05), math.log(7.0))) / 7
        profile = rng.choice(["flat", "weekend", "weekday"])
        sigma = rng.uniform(0.0, 0.08) if kind == "habit" else rng.uniform(0.15, 0.9)
        d = start
        while d <= as_of:
            w = 1.0 if profile == "flat" else (2.0 if d.weekday() >= 4 else 0.5) if profile == "weekend" \
                else (0.0 if d.weekday() >= 5 else 1.4)
            for _ in range(2 if rng.random() < 0.05 else 1):
                if rng.random() < rate * w:
                    charges.append((d, round(base * math.exp(rng.gauss(0, sigma)), 2)))
            d += timedelta(days=1)
    elif kind == "weekly_shop":
        show = rng.uniform(0.55, 0.95)
        sigma = rng.uniform(0.1, 0.7)
        d = start + timedelta(days=rng.randrange(7))
        while d <= as_of:
            if rng.random() < show:
                charges.append((d + timedelta(days=rng.choice([0, 0, 0, -1, 1, 2])),
                                round(base * math.exp(rng.gauss(0, sigma)), 2)))
            d += timedelta(days=7)
    elif kind == "renewal":
        mean = rng.uniform(4, 45)
        shape = rng.uniform(1.5, 12)
        sigma = rng.uniform(0.1, 0.6)
        d = start + timedelta(days=rng.randrange(int(mean) + 1))
        while d <= as_of:
            charges.append((d, round(base * math.exp(rng.gauss(0, sigma)), 2)))
            d += timedelta(days=max(1, round(rng.gammavariate(shape, mean / shape))))
    elif kind == "lookalike":
        # a few purchases that happen to fall about a month apart, at different prices
        d = start + timedelta(days=rng.randrange(max(1, history_days)))
        for _ in range(rng.randint(2, 4)):
            if d <= as_of:
                charges.append((d, round(base * math.exp(rng.gauss(0, rng.uniform(0.2, 0.8))), 2)))
            d += timedelta(days=rng.randint(24, 38))
    else:
        for _ in range(rng.randint(2, 6)):
            d = start + timedelta(days=rng.randrange(max(1, history_days)))
            charges.append((d, round(base * math.exp(rng.gauss(0, rng.uniform(0.3, 1.0))), 2)))
    return _row(as_of, start, charges, 0, _category(rng, NEGATIVE_CATEGORIES), active=False)


def _row(as_of, start, charges, label, category, active) -> dict:
    past = sorted((d, a) for d, a in charges if start <= d <= as_of)
    future = sorted(d for d, _ in charges if d > as_of)
    return {
        "as_of": as_of.isoformat(),
        "days": [(d - as_of).days for d, _ in past],
        "amounts": [max(0.01, a) for _, a in past],
        "category": category,
        "label": label,
        "next_in_days": (future[0] - as_of).days if active and future else None,
    }


def build(seed: int, rows: int = 16000) -> list[dict]:
    rng = random.Random(seed)
    out = []
    while len(out) < rows:
        as_of = date(2023, 1, 1) + timedelta(days=rng.randrange(0, 1400))
        history = rng.randint(45, 730)
        row = positive(rng, as_of, history) if rng.random() < 0.5 else negative(rng, as_of, history)
        if len(row["days"]) >= 2:
            out.append(row)
    return out
