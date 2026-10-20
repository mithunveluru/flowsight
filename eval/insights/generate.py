"""Seeded grounding-validator cases: facts plus one sentence that either restates a fact
(in some formatting variant) or introduces a value the facts do not contain.

  python3 generate.py --out validator_cases.jsonl
"""
import argparse
import json
import random
from datetime import date, timedelta

SEED = 20261019
SETS = 12
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
LONG_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
               "October", "November", "December"]
MERCHANTS = ["7-Eleven 24", "Store 42 Bandra", "Cafe 1947", "Route 66 Diner"]


def indian(n: int) -> str:
    s = str(n)
    if len(s) <= 3:
        return s
    head, tail = s[:-3], s[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    if head:
        groups.insert(0, head)
    return ",".join(groups + [tail])


def near_any(value, facts, tolerance=0.5):
    return any(abs(value - f["value"]) <= tolerance for f in facts if f["kind"] in ("AMOUNT", "COUNT", "PERCENT"))


def cases_for(i, rnd):
    a1 = round(rnd.uniform(1200, 240000), 2)
    a2 = round(rnd.uniform(150, 900), 2)
    pct = round(rnd.uniform(3, 75), 1)
    count = rnd.randint(4, 60)
    start = date(2026, 1, 1) + timedelta(days=rnd.randint(0, 300))
    merchant = MERCHANTS[i % len(MERCHANTS)]
    facts = [
        {"key": "total_spend", "kind": "AMOUNT", "value": a1},
        {"key": "fees", "kind": "AMOUNT", "value": a2},
        {"key": "dining_share", "kind": "PERCENT", "value": pct},
        {"key": "transactions", "kind": "COUNT", "value": count},
        {"key": "from", "kind": "DATE", "value": start.isoformat()},
        {"key": "merchant", "kind": "TEXT", "value": merchant},
    ]
    r1 = round(a1)
    out = [
        ("g_exact", "accept", f"You spent ₹{a1:,.2f} in total."),
        ("g_rounded", "accept", f"You spent ₹{r1:,} in total."),
        ("g_plain", "accept", f"You spent {r1} rupees in total."),
        ("g_indian", "accept", f"You spent ₹{indian(r1)} in total."),
        ("g_thousands", "accept", f"You spent ₹{a1 / 1000:.1f}k in total."),
        ("g_percent", "accept", f"Dining was {round(pct)}% of spending."),
        ("g_percent_exact", "accept", f"Dining was {pct}% of spending."),
        ("g_count", "accept", f"That came from {count} transactions."),
        ("g_date_iso", "accept", f"Tracking started on {start.isoformat()}."),
        ("g_date_long", "accept", f"Tracking started on {start.day} {MONTHS[start.month - 1]} {start.year}."),
        ("g_month_year", "accept", f"Tracking started in {LONG_MONTHS[start.month - 1]} {start.year}."),
        ("g_text_fact", "accept", f"Your top merchant was {merchant}, with fees of ₹{round(a2):,}."),
        ("g_no_numbers", "accept", "Spending looks steady and nothing stands out."),
    ]
    if a1 >= 100000:
        out.append(("g_lakh", "accept", f"You spent ₹{a1 / 100000:.2f} lakh in total."))

    rejects = [
        ("r_perturbed", round(a1 * 1.07), lambda v: f"You spent ₹{v:,} in total."),
        ("r_annualized", round(a1 * 12), lambda v: f"That is ₹{v:,} a year."),
        ("r_sum", round(a1 + a2), lambda v: f"Spend and fees together were ₹{v:,}."),
        ("r_percent", round(pct) + 3, lambda v: f"Dining was {v}% of spending."),
        ("r_count", count + 1, lambda v: f"That came from {v} transactions."),
        ("r_thousands", round(a1 / 1000 + 0.3, 1), lambda v: f"You spent ₹{v:.1f}k in total."),
    ]
    for variant, value, render in rejects:
        if not near_any(value * (1000 if variant == "r_thousands" else 1), facts,
                        50 if variant == "r_thousands" else 0.5):
            out.append((variant, "reject", render(value)))
    shifted = start + timedelta(days=3)
    out.append(("r_date", "reject", f"Tracking started on {shifted.day} {MONTHS[shifted.month - 1]} {shifted.year}."))
    other_month = (start.month % 12) + 1
    out.append(("r_month_year", "reject", f"Tracking started in {LONG_MONTHS[other_month - 1]} {start.year + (start.month == 12)}."))
    if not near_any(5000, facts):
        out.append(("r_words", "reject", "You could save five thousand rupees."))
    return [{"id": f"v{i:02d}-{variant}", "variant": variant, "expected": expected, "text": text, "facts": facts}
            for variant, expected, text in out]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rnd = random.Random(SEED)
    rows = [c for i in range(SETS) for c in cases_for(i, rnd)]
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
