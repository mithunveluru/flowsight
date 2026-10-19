"""Seeded synthetic transaction histories with ground truth for recurring payments and leaks.

  generate.py --out DIR      # writes DIR/cases.jsonl

One JSON line per user: region, currency, as-of date, months of history, debits up to the
as-of date ([date, merchant, amount, category, group]) and one truth record per merchant
group: whether it is a recurring obligation, its period, how many parallel charges it has,
the first charge after the as-of date, any price step, and whether it is a small-purchase
habit. Output is a pure function of the seed and the Python version (stdlib only).

This set is only for evaluation; the recurring-scorer is trained on its own corpus
(ml-service/app/recurring_scorer/corpus.py), which shares no code with this file.
"""
import argparse
import calendar
import json
import math
import random
from datetime import date, timedelta
from pathlib import Path

SEED = 20261019
USERS_PER_REGION = 30

REGIONS = {
    # currency, decimals, price scale against INR, typical prices in local currency
    "IN": {"currency": "INR", "decimals": 0, "scale": 1.0},
    "US": {"currency": "USD", "decimals": 2, "scale": 1 / 83},
    "AE": {"currency": "AED", "decimals": 2, "scale": 1 / 22.6},
    "SA": {"currency": "SAR", "decimals": 2, "scale": 1 / 22.2},
}

# kind -> region -> names. Within one user every name gets its own grouping key.
NAMES = {
    "video": {"IN": ["Netflix", "Amazon Prime", "SonyLIV", "Zee5"],
              "US": ["Netflix", "Hulu", "Peacock", "Paramount Plus"],
              "AE": ["Netflix", "OSN Plus", "Shahid", "Amazon Prime"],
              "SA": ["Netflix", "Shahid", "Amazon Prime", "OSN Plus"]},
    "music": {"IN": ["Spotify", "Apple Music", "Gaana", "JioSaavn"],
              "US": ["Spotify", "Apple Music", "Pandora", "Tidal"],
              "AE": ["Spotify", "Anghami", "Apple Music"],
              "SA": ["Spotify", "Anghami", "Apple Music"]},
    "software": {"IN": ["Microsoft 365", "Google One", "Canva", "Notion"],
                 "US": ["Microsoft 365", "Dropbox", "Adobe", "Google One"],
                 "AE": ["Microsoft 365", "Google One", "Canva"],
                 "SA": ["Microsoft 365", "Google One", "Canva"]},
    "gym": {"IN": ["Cult.fit", "Gold's Gym"], "US": ["Planet Fitness", "Equinox"],
            "AE": ["Fitness First", "GymNation"], "SA": ["Fitness Time", "Leejam"]},
    "telecom": {"IN": ["Airtel", "Jio"], "US": ["Verizon", "T-Mobile"], "AE": ["Etisalat", "du"],
                "SA": ["STC", "Mobily"]},
    "internet": {"IN": ["ACT Fibernet", "Hathway"], "US": ["Comcast", "Spectrum"], "AE": ["Starlink"],
                 "SA": ["Zain Fiber"]},
    "electricity": {"IN": ["BESCOM", "Tata Power"], "US": ["PG&E", "ConEd"], "AE": ["DEWA", "ADDC"],
                    "SA": ["Saudi Electricity"]},
    "gas": {"IN": ["Mahanagar Gas", "Indraprastha Gas"], "US": ["SoCalGas"], "AE": ["Emirates Gas"],
            "SA": ["Gasco"]},
    "rent": {"IN": ["Greenview Apartments", "Lakeside Residency"], "US": ["Oakwood Apartments", "Maple Court"],
             "AE": ["Marina Heights Leasing", "Barsha Residences"], "SA": ["Olaya Towers Leasing", "Nakheel Compound"]},
    "emi": {"IN": ["Bajaj Finserv", "Home Credit"], "US": ["Toyota Financial", "SoFi"],
            "AE": ["Emirates NBD"], "SA": ["Alinma Finance"]},
    "insurance_annual": {"IN": ["Star Health", "HDFC Life"], "US": ["State Farm", "Progressive"],
                         "AE": ["AXA Gulf", "Oman Insurance"], "SA": ["Tawuniya", "Bupa Arabia"]},
    "school_quarterly": {"IN": ["Delhi Public School"], "US": ["Bright Horizons"], "AE": ["GEMS Education"],
                         "SA": ["Kingdom Schools"]},
    "tuition_weekly": {"IN": ["Kumon", "Brightminds Tuition"], "US": ["Kumon", "Mathnasium"],
                       "AE": ["Kumon", "Eton Institute"], "SA": ["Kumon", "Berlitz"]},
    "cleaning_biweekly": {"IN": ["Urban Company"], "US": ["Molly Maid"], "AE": ["Justmop"], "SA": ["Helpling"]},
    "delivery": {"IN": ["Zomato", "Swiggy"], "US": ["DoorDash", "Grubhub"], "AE": ["Talabat", "Deliveroo"],
                 "SA": ["HungerStation", "Jahez"]},
    "coffee": {"IN": ["Third Wave Coffee", "Chai Point", "Blue Tokai"], "US": ["Starbucks", "Dunkin", "Peet's Coffee"],
               "AE": ["Starbucks", "Tim Hortons", "Costa Coffee"], "SA": ["Barn's", "Dr.Cafe", "Starbucks"]},
    "grocery": {"IN": ["BigBasket", "DMart", "Reliance Fresh"], "US": ["Whole Foods", "Trader Joe's", "Kroger"],
                "AE": ["Carrefour", "Lulu Hypermarket", "Spinneys"], "SA": ["Panda", "Tamimi Markets", "Danube"]},
    "fuel": {"IN": ["Indian Oil", "Bharat Petroleum"], "US": ["Shell", "Chevron"], "AE": ["ENOC", "ADNOC"],
             "SA": ["Aldrees", "Sasco"]},
    "rides": {"IN": ["Uber", "Ola", "Rapido"], "US": ["Uber", "Lyft"], "AE": ["Careem", "Uber"],
              "SA": ["Careem", "Uber"]},
    "shopping": {"IN": ["Amazon", "Flipkart", "Myntra"], "US": ["Amazon", "Target", "Walmart"],
                 "AE": ["Noon", "Amazon", "IKEA"], "SA": ["Noon", "Jarir", "Amazon"]},
    "pharmacy": {"IN": ["Apollo Pharmacy", "Tata 1mg"], "US": ["CVS", "Walgreens"], "AE": ["Aster Pharmacy"],
                 "SA": ["Nahdi"]},
    "dining": {"IN": ["Barbeque Nation", "Saravana Bhavan", "Haldiram's"], "US": ["Chipotle", "Olive Garden", "Panera"],
               "AE": ["Shake Shack", "Operation Falafel"], "SA": ["Al Baik", "Kudu"]},
    "cinema": {"IN": ["PVR", "INOX"], "US": ["AMC Theatres", "Regal"], "AE": ["VOX Cinemas", "Reel Cinemas"],
               "SA": ["Muvi Cinemas"]},
}

# kind -> (category, INR median price, spread) for every kind; recurring kinds also carry a period
KINDS = {
    "video": ("ENTERTAINMENT", 499, 0.0, "MONTHLY"),
    "music": ("ENTERTAINMENT", 119, 0.0, "MONTHLY"),
    "software": ("SUBSCRIPTIONS", 650, 0.0, "MONTHLY"),
    "gym": ("SUBSCRIPTIONS", 1800, 0.0, "MONTHLY"),
    "telecom": ("UTILITIES", 599, 0.0, "MONTHLY"),
    "internet": ("UTILITIES", 899, 0.0, "MONTHLY"),
    "electricity": ("UTILITIES", 1900, 0.25, "MONTHLY"),
    "gas": ("UTILITIES", 700, 0.2, "MONTHLY"),
    "rent": ("OTHER", 24000, 0.0, "MONTHLY"),
    "emi": ("FINANCE", 12500, 0.0, "MONTHLY"),
    "insurance_annual": ("FINANCE", 18000, 0.0, "ANNUAL"),
    "school_quarterly": ("EDUCATION", 30000, 0.0, "QUARTERLY"),
    "tuition_weekly": ("EDUCATION", 900, 0.0, "WEEKLY"),
    "cleaning_biweekly": ("OTHER", 1500, 0.0, "BIWEEKLY"),
    "delivery": ("FOOD_DINING", 450, 0.35, None),
    "coffee": ("FOOD_DINING", 260, 0.18, None),
    "grocery": ("GROCERIES", 1800, 0.4, None),
    "fuel": ("TRANSPORTATION", 2000, 0.2, None),
    "rides": ("TRANSPORTATION", 280, 0.4, None),
    "shopping": ("SHOPPING", 1500, 0.8, None),
    "pharmacy": ("HEALTHCARE", 600, 0.5, None),
    "dining": ("FOOD_DINING", 1400, 0.4, None),
    "cinema": ("ENTERTAINMENT", 700, 0.3, None),
}
SUBSCRIPTION_KINDS = {"video", "music", "software", "gym", "telecom", "internet"}
FREQUENT_IRREGULAR = {"delivery", "coffee", "grocery", "fuel", "rides"}
CATEGORIES = sorted({k[0] for k in KINDS.values()})
# days a monthly charge lands after its due date, by how loosely the biller keeps to it
JITTER = {0: [0], 1: [0, 0, 0, 1], 2: [0, 0, 1, 2, -1], 3: [0, 1, 2, 3, -1, -2]}
HISTORY_MONTHS = ([3, 6, 9, 12, 18], [0.15, 0.2, 0.2, 0.25, 0.2])


def add_months(d: date, months: int, day: int | None = None) -> date:
    y, m = divmod(d.month - 1 + months, 12)
    y, m = d.year + y, m + 1
    return date(y, m, min(day or d.day, calendar.monthrange(y, m)[1]))


class User:
    def __init__(self, rng: random.Random, uid: str, region: str):
        self.rng, self.uid, self.region = rng, uid, region
        self.cfg = REGIONS[region]
        self.as_of = date(2026, 1, 10) + timedelta(days=rng.randrange(0, 172))
        self.months = rng.choices(*HISTORY_MONTHS)[0]
        self.start = add_months(self.as_of, -self.months) + timedelta(days=1)
        self.end = self.as_of + timedelta(days=200)  # schedules run past as_of for the next-charge truth
        self.used: set[str] = set()
        self.groups: list[dict] = []
        self.charges: list[tuple[date, int, float]] = []

    def price(self, inr: float, spread: float = 0.0) -> float:
        value = inr * self.cfg["scale"]
        if spread:
            value *= math.exp(self.rng.gauss(0, spread))
        return self.round(value)

    def round(self, value: float) -> float:
        if self.cfg["decimals"] == 0:
            return float(max(1, round(value)))
        return max(0.5, round(value, 2))

    def list_price(self, inr: float) -> float:
        """Subscription-style price: x9 in rupees, x.99 elsewhere."""
        v = inr * self.cfg["scale"] * self.rng.uniform(0.8, 1.25)
        if self.cfg["decimals"] == 0:
            return float(max(9, int(v / 10) * 10 + 9))
        return float(max(1, int(v)) + 0.99) if v >= 3 else round(v, 2)

    def pick_name(self, kind: str) -> str | None:
        free = [n for n in NAMES[kind][self.region] if n not in self.used]
        if not free:
            return None
        name = self.rng.choice(free)
        self.used.add(name)
        return name

    def category(self, kind: str) -> str:
        r = self.rng.random()
        if r < 0.06:
            return "UNCATEGORIZED"
        if r < 0.10:
            return self.rng.choice([c for c in CATEGORIES if c != KINDS[kind][0]])
        return KINDS[kind][0]

    def group(self, kind: str, **truth) -> int | None:
        name = self.pick_name(kind)
        if name is None:
            return None
        self.groups.append({"merchant": name, "kind": kind, "category": self.category(kind),
                            "recurring": KINDS[kind][3] is not None, "period": KINDS[kind][3],
                            "series": 1, "next_date": None, "creep": None, "drain": False, **truth})
        return len(self.groups) - 1

    def charge(self, g: int, d: date, amount: float) -> None:
        if self.start <= d <= self.end:
            self.charges.append((d, g, amount))

    # ------------------------------------------------------------ recurring obligations

    def business_day(self, d: date) -> date:
        if self.region == "US" and d.weekday() >= 5:
            return d + timedelta(days=7 - d.weekday())
        return d

    def monthly(self, kind: str, amount_inr: float, fixed: bool, dom: int | None = None,
                jitter: int = 1, allow_creep: bool = True) -> None:
        rng = self.rng
        g = self.group(kind)
        if g is None:
            return
        dom = dom or rng.randint(1, 31)
        base = self.list_price(amount_inr) if kind in SUBSCRIPTION_KINDS else self.price(amount_inr)
        first = add_months(self.start, -rng.randint(0, 24), dom)
        # some subscriptions start inside the window, some stop before as_of
        if rng.random() < 0.15 and self.months >= 4:
            first = add_months(self.start, rng.randint(0, self.months - 3), dom)
        stop = None
        if rng.random() < 0.10 and self.months >= 6:
            stop = add_months(self.as_of, -rng.randint(1, 3))
        months = [add_months(first, k, dom) for k in range(0, 60)]
        months = [m for m in months if m <= self.end and (stop is None or m <= stop)]
        due = [self.business_day(m) for m in months]
        seen = [d for d in due if self.start <= d <= self.as_of]

        creep_at = None
        if fixed and allow_creep and kind in SUBSCRIPTION_KINDS and stop is None and len(seen) >= 4 \
                and rng.random() < 0.3:
            after = rng.choice([2, 3, 4])
            if len(seen) - after >= 2:
                creep_at = seen[len(seen) - after]
        new_price = self.round(base * rng.uniform(1.06, 1.35)) if creep_at else None
        season = rng.uniform(0.0, 0.35) if not fixed else 0.0
        phase = rng.uniform(0, 2 * math.pi)

        offsets = JITTER[jitter]
        for d in due:
            shifted = d + timedelta(days=rng.choice(offsets))
            if rng.random() < 0.03 and d < self.as_of - timedelta(days=40) and not (creep_at and d >= creep_at):
                continue  # a failed or skipped payment
            if fixed:
                amount = new_price if creep_at and d >= creep_at else base
            else:
                amount = self.round(base * (1 + season * math.sin(2 * math.pi * d.month / 12 + phase))
                                    * math.exp(rng.gauss(0, 0.12)))
            self.charge(g, shifted, amount)
        self.finish_recurring(g, stop)
        if creep_at:
            self.groups[g]["creep"] = {"date": creep_at.isoformat(), "before": base, "after": new_price}
        return g

    def every(self, kind: str, amount_inr: float, step_days: int, skip: float = 0.05) -> None:
        g = self.group(kind)
        if g is None:
            return
        base = self.list_price(amount_inr)
        d = self.start - timedelta(days=self.rng.randrange(0, step_days * 30))
        weekday_step = step_days % 7 == 0
        while d <= self.end:
            if self.rng.random() >= skip or d > self.as_of:
                jitter = 0 if weekday_step else self.rng.choice([-1, 0, 0, 1])
                self.charge(g, d + timedelta(days=jitter), base)
            d += timedelta(days=step_days)
        self.finish_recurring(g, None)

    def quarterly(self, kind: str, amount_inr: float) -> None:
        g = self.group(kind)
        if g is None:
            return
        base = self.price(amount_inr)
        dom = self.rng.randint(1, 15)
        first = add_months(self.start, -self.rng.randint(0, 3), dom)
        for k in range(0, 40):
            d = add_months(first, 3 * k, dom)
            if d > self.end:
                break
            self.charge(g, d + timedelta(days=self.rng.randint(-3, 3)), base)
        self.finish_recurring(g, None)

    def annual(self, kind: str, amount_inr: float) -> None:
        g = self.group(kind)
        if g is None:
            return
        base = self.price(amount_inr)
        first = self.start + timedelta(days=self.rng.randrange(0, 365))
        for k in range(-2, 3):
            self.charge(g, add_months(first, 12 * k) + timedelta(days=self.rng.randint(-2, 2)), base)
        self.finish_recurring(g, None)

    def parallel(self, kind: str) -> None:
        """One service billed on two accounts: two monthly series of the same price."""
        g = self.group(kind, series=2)
        if g is None:
            return
        base = self.list_price(KINDS[kind][1])
        dom1 = self.rng.randint(1, 14)
        dom2 = dom1 + self.rng.randint(8, 14)
        for dom in (dom1, dom2):
            first = add_months(self.start, -self.rng.randint(0, 12), dom)
            for k in range(60):
                d = add_months(first, k, dom)
                if d > self.end:
                    break
                self.charge(g, d, base)
        self.finish_recurring(g, None)

    def finish_recurring(self, g: int, stop: date | None) -> None:
        if stop is not None and stop < self.as_of:
            return
        future = sorted(d for d, gi, _ in self.charges if gi == g and d > self.as_of)
        self.groups[g]["next_date"] = future[0].isoformat() if future else None

    # ------------------------------------------------------------ discretionary spending

    def poisson(self, kind: str, per_week: float, weekday_weight=None) -> None:
        g = self.group(kind)
        if g is None:
            return
        _, inr, spread, _ = KINDS[kind]
        median = inr * self.rng.uniform(0.8, 1.25)
        d = self.start
        while d <= self.as_of:
            w = weekday_weight(d) if weekday_weight else 1.0
            if self.rng.random() < per_week / 7 * w:
                self.charge(g, d, self.price(median, spread))
            d += timedelta(days=1)

    def weekly_shop(self, kind: str) -> None:
        """A weekly shop on a preferred day, sometimes a day off, variable basket."""
        g = self.group(kind)
        if g is None:
            return
        _, inr, spread, _ = KINDS[kind]
        median = inr * self.rng.uniform(0.7, 1.3)
        d = self.start + timedelta(days=self.rng.randrange(7))
        while d <= self.as_of:
            if self.rng.random() < 0.85:
                self.charge(g, d + timedelta(days=self.rng.choice([0, 0, 0, -1, 1])), self.price(median, spread))
            d += timedelta(days=7)

    def refuel(self) -> None:
        g = self.group("fuel")
        if g is None:
            return
        _, inr, spread, _ = KINDS["fuel"]
        gap = self.rng.uniform(6, 14)
        d = self.start + timedelta(days=self.rng.randrange(10))
        while d <= self.as_of:
            self.charge(g, d, self.price(inr, spread))
            d += timedelta(days=max(3, round(self.rng.gauss(gap, 2.5))))

    # ------------------------------------------------------------ assembly

    def build(self) -> dict:
        rng = self.rng
        for _ in range(rng.choice([1, 1, 2, 2, 3])):
            self.monthly("video", KINDS["video"][1], fixed=True)
        for _ in range(rng.choice([0, 1, 1, 2])):
            self.monthly("music", KINDS["music"][1], fixed=True)
        for _ in range(rng.choice([0, 0, 1, 2])):
            self.monthly("software", KINDS["software"][1], fixed=True)
        if rng.random() < 0.4:
            self.monthly("gym", KINDS["gym"][1], fixed=True)
        for _ in range(rng.choice([1, 1, 2])):
            self.monthly("telecom", KINDS["telecom"][1], fixed=True, jitter=2)
        if rng.random() < 0.6:
            self.monthly("internet", KINDS["internet"][1], fixed=True, jitter=2)
        self.monthly("electricity", KINDS["electricity"][1], fixed=False, jitter=3)
        if rng.random() < 0.4:
            self.monthly("gas", KINDS["gas"][1], fixed=False, jitter=3)
        if rng.random() < 0.7:
            self.monthly("rent", KINDS["rent"][1] * rng.uniform(0.7, 1.6), fixed=True, dom=rng.randint(1, 5),
                         jitter=1, allow_creep=False)
        if rng.random() < 0.4:
            self.monthly("emi", KINDS["emi"][1] * rng.uniform(0.6, 2.0), fixed=True, dom=rng.randint(2, 10),
                         jitter=0, allow_creep=False)
        if rng.random() < 0.5:
            self.annual("insurance_annual", KINDS["insurance_annual"][1] * rng.uniform(0.6, 1.6))
        if rng.random() < 0.3:
            self.quarterly("school_quarterly", KINDS["school_quarterly"][1] * rng.uniform(0.6, 1.6))
        if rng.random() < 0.25:
            self.every("tuition_weekly", KINDS["tuition_weekly"][1], 7, skip=0.1)
        if rng.random() < (0.3 if self.region == "US" else 0.12):
            self.every("cleaning_biweekly", KINDS["cleaning_biweekly"][1], 14)
        if rng.random() < 0.12:
            self.parallel(rng.choice(["video", "music"]))

        weekend = lambda d: 1.8 if d.weekday() >= 4 else 0.7  # noqa: E731
        weekday = lambda d: 0.0 if d.weekday() >= 5 else 1.4  # noqa: E731
        for _ in range(rng.choice([1, 1, 2])):
            self.poisson("delivery", rng.uniform(0.4, 3.0), weekend)
        if rng.random() < 0.6:
            self.poisson("coffee", rng.uniform(1.5, 5.0), weekday)
        self.weekly_shop("grocery")
        if rng.random() < 0.6:
            self.poisson("grocery", rng.uniform(0.3, 1.2))
        if rng.random() < 0.5:
            self.refuel()
        if rng.random() < 0.6:
            self.poisson("rides", rng.uniform(0.5, 3.0))
        for _ in range(rng.choice([1, 2])):
            self.poisson("shopping", rng.uniform(0.07, 0.35))
        if rng.random() < 0.6:
            self.poisson("pharmacy", rng.uniform(0.07, 0.25))
        for _ in range(rng.choice([1, 2])):
            self.poisson("dining", rng.uniform(0.1, 0.7))
        if rng.random() < 0.4:
            self.poisson("cinema", rng.uniform(0.07, 0.35))
        return self.finish()

    def finish(self) -> dict:
        history = sorted((c for c in self.charges if c[0] <= self.as_of), key=lambda c: (c[0], c[1], c[2]))
        window_start = add_months(self.as_of, -3)
        for g, group in enumerate(self.groups):
            dates = [d for d, gi, _ in history if gi == g]
            group["count"] = len(dates)
            # a habit is a frequent small purchase; visits counted like the leak window
            recent = sum(1 for d in dates if d >= window_start)
            group["drain"] = group["kind"] == "coffee" and recent >= 8
            if group["kind"] in FREQUENT_IRREGULAR:
                group["frequent_irregular"] = len(dates) >= 4
        fmt = "{:.0f}" if self.cfg["decimals"] == 0 else "{:.2f}"
        return {
            "user": self.uid, "region": self.region, "currency": self.cfg["currency"],
            "as_of": self.as_of.isoformat(), "months": self.months,
            "groups": [{"id": g, **group} for g, group in enumerate(self.groups)],
            "tx": [[d.isoformat(), gi, fmt.format(a), self.groups[gi]["category"]] for d, gi, a in history],
        }


def build(seed: int = SEED) -> list[dict]:
    rng = random.Random(seed)
    users = []
    for region in REGIONS:
        for i in range(USERS_PER_REGION):
            users.append(User(random.Random(rng.getrandbits(64)), f"{region.lower()}-{i:03d}", region).build())
    return users


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--seed", type=int, default=SEED)
    args = ap.parse_args(argv)
    users = build(args.seed)
    args.out.mkdir(parents=True, exist_ok=True)
    with open(args.out / "cases.jsonl", "w", encoding="utf-8") as f:
        for u in users:
            f.write(json.dumps(u, separators=(",", ":"), sort_keys=True) + "\n")
    print(f"{len(users)} users, {sum(len(u['tx']) for u in users)} debits, "
          f"{sum(len(u['groups']) for u in users)} merchant groups -> {args.out / 'cases.jsonl'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
