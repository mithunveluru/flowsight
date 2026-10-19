"""Seeded synthetic transaction histories for the forecasting backtest.

  python3 generate.py --out out/forecast/histories.jsonl [--users 160] [--seed 20261019]

One JSON line per user: {"user", "tags", "start", "end", "origins", "tx": [[date, type, category, amount], ...]}.
"origins" are the backtest's "today" dates: one per month whose next HORIZON months are complete
inside the history, on a seeded day of that month.
Archetypes mix salaried, freelance and gig income, fixed obligations (rent, EMI, subscriptions),
day-to-day categories with noise, and per-user seasonality (festival shopping, summer utilities and
travel, school fees, an annual insurance premium), lifestyle drift, and shocks (job loss, medical
bills, bonuses, one-off purchases). History lengths range from a few weeks to three years.
Standard library only, so the output is byte-identical on every machine for a given seed.
"""
import argparse
import calendar
import hashlib
import json
import math
import random
from datetime import date, timedelta
from pathlib import Path

END_MIN = date(2025, 6, 30)
END_SPAN_DAYS = 400
HORIZON = 3


def months_between(start: date, end: date):
    y, m = start.year, start.month
    while (y, m) <= (end.year, end.month):
        yield y, m
        y, m = (y + 1, 1) if m == 12 else (y, m + 1)


def day_in(rng, y, m, lo=1, hi=None):
    last = calendar.monthrange(y, m)[1]
    return date(y, m, rng.randint(lo, min(hi or last, last)))


def money(x: float) -> float:
    return round(max(x, 1.0), 2)


class User:
    def __init__(self, rng: random.Random, idx: int):
        self.rng = rng
        self.id = f"u{idx:03d}"
        r = rng.random()
        self.income_kind = "salaried" if r < 0.6 else ("freelance" if r < 0.85 else "mixed")
        self.scale = math.exp(rng.gauss(0, 0.5))
        self.salary = 60000 * self.scale
        self.seasonal = rng.random() < 0.45
        self.drift = rng.choice([0.0, 0.0, 0.0, 0.01, 0.02, -0.01])  # monthly change in discretionary spend
        self.shocky = rng.random() < 0.35
        self.volatile = self.income_kind != "salaried" or rng.random() < 0.15
        self.rent = self.salary * rng.uniform(0.15, 0.3) if rng.random() < 0.7 else 0.0
        self.emi = self.salary * rng.uniform(0.08, 0.2) if rng.random() < 0.45 else 0.0
        self.emi_months = rng.randint(6, 60)
        self.subs = [round(rng.choice([149, 199, 299, 499, 649, 999]) * (1 + 0.3 * (self.scale - 1)), 0)
                     for _ in range(rng.randint(0, 4))]
        self.insurance_month = rng.randint(1, 12) if rng.random() < 0.5 else None
        self.school = rng.random() < 0.25
        self.festival = rng.choice([10, 11]) if self.seasonal else None
        self.summer = self.seasonal and rng.random() < 0.7
        self.base = {
            "GROCERIES": self.salary * rng.uniform(0.06, 0.14),
            "FOOD_DINING": self.salary * rng.uniform(0.03, 0.1),
            "TRANSPORTATION": self.salary * rng.uniform(0.02, 0.07),
            "SHOPPING": self.salary * rng.uniform(0.03, 0.12),
            "ENTERTAINMENT": self.salary * rng.uniform(0.0, 0.04),
            "UTILITIES": self.salary * rng.uniform(0.02, 0.05),
            "HEALTHCARE": self.salary * rng.uniform(0.0, 0.02),
            "OTHER": self.salary * rng.uniform(0.0, 0.03),
        }
        r = rng.random()
        if r < 0.2:
            length_days = rng.randint(20, 130)
        elif r < 0.5:
            length_days = rng.randint(150, 480)
        else:
            length_days = rng.randint(500, 1100)
        self.end = END_MIN + timedelta(days=rng.randint(0, END_SPAN_DAYS))
        self.start = self.end - timedelta(days=length_days)
        self.job_loss = set()
        self.bonus = set()
        self.big_medical = set()
        self.big_purchase = set()
        months = list(months_between(self.start, self.end))
        if self.shocky and len(months) > 4:
            for _ in range(rng.randint(1, 3)):
                kind = rng.choice(["job_loss", "medical", "purchase", "bonus"])
                y, m = rng.choice(months)
                if kind == "job_loss":
                    k = months.index((y, m))
                    self.job_loss.update(months[k:k + rng.randint(2, 4)])
                elif kind == "medical":
                    self.big_medical.add((y, m))
                elif kind == "purchase":
                    self.big_purchase.add((y, m))
                else:
                    self.bonus.add((y, m))

    def tags(self):
        t = [self.income_kind]
        if self.seasonal:
            t.append("seasonal")
        if self.shocky:
            t.append("shock")
        if self.volatile:
            t.append("volatile")
        if self.drift:
            t.append("drift")
        return t

    def seasonal_factor(self, cat: str, m: int) -> float:
        f = 1.0
        if self.festival and cat in ("SHOPPING", "FOOD_DINING", "GROCERIES") and m == self.festival:
            f *= {"SHOPPING": 2.6, "FOOD_DINING": 1.5, "GROCERIES": 1.3}[cat]
        if self.summer and cat == "UTILITIES" and m in (4, 5, 6):
            f *= 1.5
        if self.seasonal and cat == "SHOPPING" and m == 12:
            f *= 1.4
        return f

    def generate(self):
        rng = self.rng
        tx = []
        months = list(months_between(self.start, self.end))
        for k, (y, m) in enumerate(months):
            def add(d: date, typ: str, cat: str, amount: float):
                if self.start <= d <= self.end:
                    tx.append([d.isoformat(), typ, cat, money(amount)])

            lost = (y, m) in self.job_loss
            raise_factor = 1.0 + 0.08 * sum(1 for (yy, mm) in months[:k + 1] if mm == 4 and (yy, mm) != months[0])
            # income
            if self.income_kind in ("salaried", "mixed") and not lost:
                pay = self.salary * raise_factor * (1 if self.income_kind == "salaried" else 0.6)
                add(day_in(rng, y, m, 1, 3), "CREDIT", "INCOME", pay * rng.uniform(0.995, 1.005))
                if (y, m) in self.bonus:
                    add(day_in(rng, y, m, 20, 28), "CREDIT", "INCOME", pay * rng.uniform(0.8, 1.5))
            if self.income_kind in ("freelance", "mixed") and not lost:
                target = self.salary * (1.0 if self.income_kind == "freelance" else 0.5)
                for _ in range(rng.choice([0, 1, 1, 2, 2, 3, 4])):
                    add(day_in(rng, y, m), "CREDIT", "INCOME", target / 2 * math.exp(rng.gauss(0, 0.45)))
            if rng.random() < 0.2:
                add(day_in(rng, y, m), "CREDIT", "INCOME", self.salary * 0.01 * math.exp(rng.gauss(0, 0.6)))
            # fixed obligations
            if self.rent:
                add(day_in(rng, y, m, 1, 5), "DEBIT", "TRANSFER", self.rent)
            if self.emi and k < self.emi_months:
                add(day_in(rng, y, m, 2, 7), "DEBIT", "FINANCE", self.emi)
            for i, s in enumerate(self.subs):
                add(date(y, m, min(5 + 3 * i, 28)), "DEBIT", "SUBSCRIPTIONS", s)
            if self.insurance_month == m:
                add(day_in(rng, y, m, 5, 25), "DEBIT", "FINANCE", self.salary * rng.uniform(0.25, 0.6))
            if self.school and m in (1, 6):
                add(day_in(rng, y, m, 1, 15), "DEBIT", "EDUCATION", self.salary * rng.uniform(0.4, 0.8))
            # day-to-day spending, a few purchases per category per month
            tighten = 0.7 if lost else 1.0
            drift = (1 + self.drift) ** k
            for cat, base in self.base.items():
                if base <= 0:
                    continue
                expected = base * drift * tighten * self.seasonal_factor(cat, m)
                noise = 0.12 if not self.volatile else 0.3
                monthly = expected * math.exp(rng.gauss(0, noise))
                n = {"GROCERIES": 5, "FOOD_DINING": 8, "TRANSPORTATION": 10, "SHOPPING": 3,
                     "ENTERTAINMENT": 2, "UTILITIES": 2, "HEALTHCARE": 1, "OTHER": 2}[cat]
                if cat in ("HEALTHCARE", "ENTERTAINMENT", "OTHER") and rng.random() < 0.3:
                    continue
                weights = [rng.random() + 0.2 for _ in range(n)]
                total = sum(weights)
                for w in weights:
                    add(day_in(rng, y, m), "DEBIT", cat, monthly * w / total)
            if self.summer and m in (5, 12) and rng.random() < 0.8:
                add(day_in(rng, y, m, 5, 25), "DEBIT", "TRAVEL", self.salary * rng.uniform(0.3, 0.8))
            elif rng.random() < 0.08:
                add(day_in(rng, y, m), "DEBIT", "TRAVEL", self.salary * rng.uniform(0.1, 0.4))
            if (y, m) in self.big_medical:
                add(day_in(rng, y, m), "DEBIT", "HEALTHCARE", self.salary * rng.uniform(0.5, 1.5))
            if (y, m) in self.big_purchase:
                add(day_in(rng, y, m), "DEBIT", "SHOPPING", self.salary * rng.uniform(0.6, 2.0))
        tx.sort(key=lambda t: (t[0], t[1], t[2], t[3]))
        return {"user": self.id, "tags": self.tags(), "start": self.start.isoformat(),
                "end": self.end.isoformat(), "origins": self.origins(months), "tx": tx}

    def origins(self, months):
        out = []
        for k, (y, m) in enumerate(months):
            if k + HORIZON >= len(months):
                break
            ty, tm = months[k + HORIZON]
            if date(ty, tm, calendar.monthrange(ty, tm)[1]) > self.end:
                break
            day = 1 + hashlib.sha256(f"{self.id}:{y}-{m:02d}".encode()).digest()[0] % 28
            today = date(y, m, day)
            if today >= self.start:
                out.append(today.isoformat())
        return out


def build(users: int, seed: int):
    rng = random.Random(seed)
    return [User(rng, i).generate() for i in range(users)]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--users", type=int, default=160)
    ap.add_argument("--seed", type=int, default=20261019)
    args = ap.parse_args(argv)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as f:
        for record in build(args.users, args.seed):
            f.write(json.dumps(record, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
