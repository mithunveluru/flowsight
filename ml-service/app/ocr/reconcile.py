"""Choosing the total by consistency across passes, as the backend's ReceiptReconciler does.

A value read on a total or payment line is checked against subtotal (or the item sum) minus
discounts plus taxes and service (plus tip, plus or minus round-off), the payment line and cash
minus change. Two well-supported totals that no tip or round-off explains are a conflict and go
to the user with both values.
"""

from dataclasses import dataclass, field
from decimal import Decimal

from app.ocr.text import CENT, TOTAL_KINDS, Parsed

ACCEPT_AT = 0.90
HIGHLIGHT_AT = 0.60
UNPRINTED_ROUNDING = Decimal("0.50")


def decision(confidence: float) -> str:
    if confidence >= ACCEPT_AT:
        return "ACCEPT"
    return "ACCEPT_WITH_HIGHLIGHT" if confidence >= HIGHLIGHT_AT else "ASK_USER"


def tolerance(components: int) -> Decimal:
    """Each tax, discount and charge line is rounded on its own: one cent per such line."""
    return CENT * components


def _close(a: Decimal, b: Decimal, components: int) -> bool:
    return abs(a - b) <= tolerance(components)


@dataclass
class Evidence:
    value: Decimal
    grand: bool = False
    labelled: bool = False
    payment: bool = False
    arithmetic: str | None = None
    derived: bool = False
    last_total: bool = False
    label: str | None = None
    passes: set = field(default_factory=set)
    sources: set = field(default_factory=set)

    def strong(self) -> bool:
        return (bool(self.arithmetic) and (self.labelled or self.payment)) or (self.labelled and self.payment)

    def score(self) -> float:
        s = (0.40 if self.arithmetic else 0) + (0.25 if self.grand else 0.18 if self.labelled else 0)
        s += (0.40 if self.payment else 0) + (0.04 if self.last_total else 0)
        return s + 0.03 * min(2, len(self.passes) - 1) + (0.10 if "vision" in self.sources else 0)

    def basis(self) -> str:
        parts = []
        if self.arithmetic:
            parts.append(f"arithmetic({self.arithmetic})")
        if self.labelled:
            parts.append("grand-total-label" if self.grand else "total-label")
        if self.payment:
            parts.append("payment")
        if self.derived:
            parts.append("derived")
        if "vision" in self.sources:
            parts.append("vision")
        if len(self.passes) > 1:
            parts.append(f"{len(self.passes)}-passes")
        return "+".join(parts)


def confidence_of(e: Evidence) -> float:
    if e.derived:
        return 0.80 if e.arithmetic else 0.30
    if e.arithmetic and e.labelled and e.payment:
        c = 0.98
    elif e.arithmetic and (e.labelled or e.payment):
        c = 0.95
    elif e.labelled and e.payment:
        c = 0.90
    elif e.arithmetic:
        c = 0.75
    elif e.grand:
        c = 0.65
    elif e.labelled:
        c = 0.55
    else:
        c = 0.45
    if len(e.passes) > 1 and c < 0.98:
        c = min(0.99, c + 0.02)
    return c


@dataclass
class Components:
    items_sum: Decimal | None
    subtotals: list
    tax: Decimal
    discount: Decimal
    service: Decimal
    tips: list
    round_off: Decimal
    has_round_off: bool
    has_tax: bool
    count: int


def components(p: Parsed) -> Components:
    vals = lambda *k: [a.value for a in p.of(*k)]  # noqa: E731
    taxes = vals("TAX") or vals("TAX_TOTAL")
    disc, svc, ro = vals("DISCOUNT"), vals("SERVICE_CHARGE"), vals("ROUND_OFF")
    return Components(
        sum((i.amount for i in p.items), Decimal(0)) if p.items else None,
        [v for v in vals("SUBTOTAL") if v > 0], sum(map(abs, taxes), Decimal(0)), sum(map(abs, disc), Decimal(0)),
        sum(map(abs, svc), Decimal(0)), [abs(v) for v in vals("TIP")], sum(ro, Decimal(0)), bool(ro), bool(taxes),
        len(taxes) + len(disc) + len(svc) + len(ro))


def arithmetic(v: Decimal, c: Components, totals: list) -> str | None:
    bases = list(c.subtotals) + ([c.items_sum] if c.items_sum is not None else [])
    for i, base in enumerate(bases):
        from_items = i == len(c.subtotals)
        whole = v % 1 == 0
        if not c.has_tax and c.discount == 0 and c.service == 0:
            if _close(base, v, c.count):
                return "items" if from_items else "subtotal"
            if not c.has_round_off and whole and abs(base - v) < UNPRINTED_ROUNDING:
                return "rounded"
            continue
        expected = base - c.discount + c.tax + c.service + c.round_off
        name = "items+charges" if from_items else "subtotal+charges"
        if _close(expected, v, c.count):
            return name
        if any(_close(expected + t, v, c.count) for t in c.tips):
            return name + "+tip"
        if not c.has_round_off and whole and abs(expected - v) < UNPRINTED_ROUNDING:
            return name + "+rounded"
    for t in c.tips:
        if any(not _close(x, v, 0) and _close(x + t, v, 0) for x in totals):
            return "total+tip"
    return None


def _related(a: Decimal, b: Decimal, passes: list[Parsed]) -> bool:
    diff = abs(a - b)
    return any(_close(abs(x.value), diff, 0) for p in passes for x in p.of("TIP", "ROUND_OFF"))


def _label(text: str) -> str | None:
    cut = next((i for i, ch in enumerate(text) if ch.isdigit()), len(text))
    label = text[:cut].strip(" :@*#=(-$₹").removesuffix("Rs.").removesuffix("Rs").strip()
    return label or None


@dataclass
class TotalResult:
    value: Decimal | None
    confidence: float
    decision: str
    basis: str
    conflict: bool
    options: list = field(default_factory=list)  # (amount, label, basis)
    items_reconciled: bool = False
    pass_index: int = -1


def reconcile(passes: list[Parsed], vision_total: Decimal | None = None) -> TotalResult:
    ev: dict[Decimal, Evidence] = {}
    comps, totals_by_pass = [], []
    items_ok = [False] * len(passes)

    def get(v: Decimal) -> Evidence:
        return ev.setdefault(v, Evidence(v))

    for k, p in enumerate(passes):
        c = components(p)
        totals = [a for a in p.amounts if a.kind in TOTAL_KINDS and a.value > 0]
        last = max((a.index for a in totals), default=-1)
        comps.append(c)
        totals_by_pass.append([a.value for a in totals])
        for a in totals:
            e = get(a.value)
            if e.derived:
                e.payment = True
            e.labelled, e.derived = True, False
            e.grand |= a.kind == "GRAND_TOTAL"
            e.last_total |= a.index == last
            e.label = e.label or _label(a.text)
            e.passes.add(k)
        for a in p.of("PAYMENT"):
            if a.value > 0:
                e = get(a.value)
                e.payment, e.derived = True, False
                e.label = e.label or _label(a.text)
                e.passes.add(k)
        tendered, change = [a.value for a in p.of("TENDERED")], [a.value for a in p.of("CHANGE")]
        for t in tendered:
            if not change and t in ev:
                ev[t].payment = True
            for ch in change:
                paid = t - abs(ch)
                if paid <= 0:
                    continue
                if paid not in ev:
                    ev[paid] = Evidence(paid, derived=True, label="Cash minus change")
                elif not ev[paid].derived:
                    ev[paid].payment = True
                ev[paid].passes.add(k)
        items_ok[k] = c.items_sum is not None and (
            any(_close(s, c.items_sum, 0) for s in c.subtotals) or any(_close(t.value, c.items_sum, 0) for t in totals))

    if vision_total is not None and vision_total in ev:
        ev[vision_total].sources.add("vision")

    for e in ev.values():
        for k in range(len(passes)):
            basis = arithmetic(e.value, comps[k], totals_by_pass[k])
            if basis:
                e.arithmetic = basis
                break

    if not ev:
        return TotalResult(None, 0.0, "ASK_USER", "not found", False, [], any(items_ok))

    ranked = sorted(ev.values(), key=lambda e: (e.score(), e.value), reverse=True)
    best = ranked[0]
    for other in ranked:
        tips = [abs(t.value) for p in passes for t in p.of("TIP")]
        if other.value > best.value and (other.labelled or other.payment) and any(_close(t, other.value - best.value, 0) for t in tips):
            best = other

    conflict = False
    for other in ranked:
        if other is best or _related(best.value, other.value, passes):
            continue
        both_weak = not best.strong() and not other.strong() and other.labelled and best.labelled
        if other.strong() or both_weak or (other.grand and not best.grand) or (other.derived and not best.strong()):
            conflict = True
            break

    conf = confidence_of(best)
    disagreeing = sum(1 for t in totals_by_pass if t and not any(_close(x, best.value, 0) or _related(x, best.value, passes) for x in t))
    if disagreeing == 1:
        conf = min(conf, 0.85)
    elif disagreeing > 1:
        conf = min(conf, 0.65)
    if conflict:
        conf, dec, basis = min(conf, 0.45), "ASK_USER", "conflict:" + best.basis()
    else:
        if best.derived:
            conf = min(conf, ACCEPT_AT - 0.01)
        dec, basis = decision(conf), best.basis()
    options = [(best.value, best.label, best.basis())] + [(e.value, e.label, e.basis()) for e in ranked if e is not best]
    first = min(best.passes) if best.passes else -1
    return TotalResult(best.value, round(conf, 3), dec, basis, conflict, options[:4],
                       items_ok[first] if first >= 0 else False, first)
