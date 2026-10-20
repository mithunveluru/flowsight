"""Multi-pass receipt reading.

Pass B: Tesseract over up to three renderings of the preprocessed image, stopping once the total
is settled. Pass A (optional): the vision model on the PII-masked image. When A and B disagree on
the total, the totals region is cropped, enlarged and read again by Tesseract. Every number in the
result must have been read from the receipt by a Tesseract pass; a vision-only value is never
returned as fact.
"""

import re
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

import cv2
import numpy as np

from app.ocr import images, vision
from app.ocr.reconcile import ACCEPT_AT, HIGHLIGHT_AT, TotalResult, decision, reconcile
from app.ocr.tesseract import Runner
from app.ocr.text import TOTAL_KINDS, Parsed, find_amounts, find_date, normalize, read_pass

SINGLE_PASS_ACCEPT = 0.98
REREAD_MARGIN_LINES = 2


def settled(t: TotalResult, passes: int) -> bool:
    """One pass is enough only when label, arithmetic and payment agree; otherwise two must."""
    if t.conflict or t.decision != "ACCEPT":
        return False
    return passes >= 2 or t.confidence >= SINGLE_PASS_ACCEPT


def _squash(s: str) -> str:
    return re.sub(r"[^\w]", "", (s or "").lower())


def _field(confidence: float, basis: str, ask: bool = False) -> dict:
    c = round(max(0.0, min(1.0, confidence)), 3)
    return {"confidence": c, "decision": "ASK_USER" if ask else decision(c), "basis": basis}


MISSING = {"confidence": 0.0, "decision": "ASK_USER", "basis": "not found"}


def _merchant_confidence(score: float, agreeing: int) -> float:
    p = 0.93 if score >= 0.95 else 0.86 if score >= 0.80 else 0.72 if score >= 0.60 else 0.50 if score >= 0.40 else 0.30
    return min(0.97, p + (0.04 if agreeing > 1 else 0))


def reread_totals(prepared: np.ndarray, parsed: Parsed, runner: Runner) -> Parsed | None:
    """Crop the band of lines from the first total-like line to the last payment line, enlarge, read again."""
    idx = [a.index for a in parsed.amounts if a.kind in TOTAL_KINDS | {"PAYMENT", "TENDERED", "CHANGE", "SUBTOTAL", "TAX"}]
    if not idx or not parsed.lines[0].height:
        return None
    lines = parsed.lines
    lo, hi = max(0, min(idx) - REREAD_MARGIN_LINES), min(len(lines) - 1, max(idx) + REREAD_MARGIN_LINES)
    top = max(0, lines[lo].top - lines[lo].height)
    bottom = min(prepared.shape[0], lines[hi].top + 2 * max(1, lines[hi].height))
    if bottom - top < 10:
        return None
    band = cv2.resize(prepared[top:bottom], None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC)
    _, otsu = cv2.threshold(band, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return read_pass(runner(otsu))


@dataclass
class Outcome:
    result: dict
    passes: int
    vision_used: bool
    reread: bool
    vision_status: str = "off"


def run(data: bytes, runner: Runner, vision_config: vision.VisionConfig,
        transport: vision.Transport | None = None, today: date | None = None) -> Outcome:
    gray = images.decode(data)
    if gray is None:
        raise ValueError("unreadable image")
    prepared = images.prepare(gray)

    parsed: list[Parsed] = []
    for rendering in images.renderings(prepared):
        parsed.append(read_pass(runner(rendering)))
        if settled(reconcile(parsed), len(parsed)):
            break

    reading = None
    vision_status = "off"
    if vision_config.enabled:
        first = parsed[0]
        words = [w for line in first.lines for w in line.words]
        boxes = images.pii_boxes(words, [(l.text, l.words) for l in first.lines])
        png = images.encode_png(images.mask(prepared, boxes))
        reading, vision_status = (vision.read_with_status(vision_config, png, transport) if transport
                                  else vision.read_with_status(vision_config, png))

    reread = False
    total = reconcile(parsed, reading.total if reading else None)
    if reading and reading.total is not None and total.value != reading.total:
        extra = reread_totals(prepared, parsed[0], runner)
        if extra is not None:
            parsed.append(extra)
            reread = True
            total = reconcile(parsed, reading.total)

    primary = parsed[total.pass_index if total.pass_index >= 0 else 0]
    return Outcome(assemble(parsed, total, primary, reading, today), len(parsed), reading is not None, reread,
                   vision_status)


def assemble(parsed: list[Parsed], total: TotalResult, primary: Parsed, reading, today: date | None) -> dict:
    all_text = "\n".join(p.text() for p in parsed)

    # currency: Tesseract cues; the vision answer only fills a gap and only for a known code
    cur = max((p.currency for p in parsed), key=lambda c: c[1])
    codes = {p.currency[0] for p in parsed if p.currency[0]}
    if len(codes) > 1:
        cur = (cur[0], min(cur[1], 0.5))
    currency_field = _field(cur[1], "cues") if cur[0] else MISSING
    if not cur[0] and reading and reading.currency and reading.currency.upper() in {"INR", "SAR", "AED", "USD", "EUR", "GBP"}:
        cur = (reading.currency.upper(), 0.6)
        currency_field = _field(0.6, "vision")

    # date: agreement across passes; the vision date counts only if a pass read the same date
    guesses = [find_date([l.text for l in p.lines], cur[0], today) for p in parsed]
    dated = [g for g in guesses if g.value]
    date_value, date_field = None, MISSING
    if dated:
        counts: dict = {}
        for g in dated:
            counts.setdefault(g.value, []).append(g)
        value, group = max(counts.items(), key=lambda kv: (len(kv[1]), any(g.labelled for g in kv[1])))
        ambiguous = all(g.ambiguous for g in group)
        c = (0.70 if cur[0] else 0.45) if ambiguous else (0.95 if any(g.labelled for g in group) else 0.88)
        if len(counts) > 1:
            c = min(c, 0.55)
        if reading and reading.date_text:
            v = find_date([reading.date_text], cur[0], today)
            if v.value == value and c < ACCEPT_AT:
                c = max(c, ACCEPT_AT)
        date_value, date_field = value, _field(c, "ambiguous-order" if ambiguous else "pattern")

    # merchant: best line across passes; the vision name only if it is printed on the receipt
    names: dict = {}
    for p in parsed:
        if p.merchant:
            names.setdefault(p.merchant[0].lower(), []).append(p.merchant)
    merchant, merchant_raw, merchant_field = None, None, MISSING
    if names:
        group = max(names.values(), key=lambda g: max(m[2] for m in g) + 0.05 * (len(g) - 1))
        top = max(group, key=lambda m: m[2])
        merchant, merchant_raw = top[0], top[1]
        merchant_field = _field(_merchant_confidence(top[2], len(group)), "heuristic")
    if reading and reading.merchant and _squash(reading.merchant) and _squash(reading.merchant) in _squash(all_text):
        if merchant is None or _squash(merchant) == _squash(reading.merchant):
            merchant_field = _field(max(merchant_field["confidence"], 0.92), "vision+tesseract")
        elif merchant_field["confidence"] < HIGHLIGHT_AT:
            merchant, merchant_raw = reading.merchant, reading.merchant
            merchant_field = _field(0.70, "vision-verified")

    amount_field = MISSING if total.value is None else _field(total.confidence, total.basis, ask=total.decision == "ASK_USER")
    fields = {"merchant": merchant_field, "amount": amount_field, "date": date_field, "currency": currency_field}
    order = ["ACCEPT", "ACCEPT_WITH_HIGHLIGHT", "ASK_USER"]
    overall = max((f["decision"] for f in fields.values()), key=order.index)

    def first(kind):
        vals = primary.of(kind)
        return str(vals[0].value) if vals else None

    result = {
        "merchant": merchant,
        "merchant_raw": merchant_raw,
        "amount": str(total.value) if total.value is not None else None,
        "date": date_value.isoformat() if date_value else None,
        "currency": cur[0],
        "subtotal": first("SUBTOTAL"),
        "taxes": [{"label": re.split(r"\d", a.text)[0].strip(" :@%")[:40] or None, "amount": str(abs(a.value))}
                  for a in primary.of("TAX", "TAX_TOTAL")],
        "discounts": [{"label": None, "amount": str(abs(a.value))} for a in primary.of("DISCOUNT")],
        "tip": first("TIP"),
        "round_off": first("ROUND_OFF"),
        "line_items": [{"name": i.name, "quantity": str(i.quantity) if i.quantity else None, "price": str(i.amount)}
                       for i in primary.items],
        "amount_candidates": [{"amount": str(v), "label": label, "basis": basis} for v, label, basis in total.options],
        "items_reconciled": total.items_reconciled,
        "fields": fields,
        "decision": overall,
        "text": primary.text(),
    }
    return validate(result, numbers_in(all_text))


def numbers_in(text: str) -> set[Decimal]:
    return {abs(a.value) for line in normalize(text).splitlines() for a in find_amounts(line)}


def validate(result: dict, read: set[Decimal]) -> dict:
    """Drops any amount that no OCR pass read from the receipt."""
    ok = lambda v: v is not None and abs(Decimal(v)) in read  # noqa: E731
    for key in ("amount", "subtotal", "tip", "round_off"):
        if result.get(key) is not None and not ok(result[key]):
            result[key] = None
            if key == "amount":
                result["fields"]["amount"] = dict(MISSING)
                result["decision"] = "ASK_USER"
    result["taxes"] = [t for t in result["taxes"] if ok(t["amount"])]
    result["discounts"] = [d for d in result["discounts"] if ok(d["amount"])]
    result["line_items"] = [i for i in result["line_items"] if ok(i["price"])]
    # cash minus change is computed, not read: it may be offered, never asserted
    result["amount_candidates"] = [c for c in result["amount_candidates"]
                                   if ok(c["amount"]) or "derived" in (c.get("basis") or "")]
    if result["amount"] is None and result["amount_candidates"]:
        result["fields"]["amount"] = dict(MISSING, basis="suggestions only")
    return result
