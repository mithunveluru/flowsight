import json
from collections import Counter
from decimal import Decimal

from receipts import generate


def test_format_amount_variants():
    v = Decimal("123456.5")
    assert generate.format_amount(v, "us") == "123,456.50"
    assert generate.format_amount(v, "eu") == "123.456,50"
    assert generate.format_amount(v, "indian") == "1,23,456.50"
    assert generate.format_amount(v, "arabic_indic") == "١٢٣٬٤٥٦٫٥٠"
    assert generate.format_amount(Decimal("-0.4"), "indian") == "-0.40"
    assert generate.format_amount(Decimal("12345678"), "indian") == "1,23,45,678.00"


def test_plan_size_and_balance():
    plan = generate.build_plan(generate.SEED)
    assert len(plan) == 200
    assert Counter(c for _, c, _, _ in plan) == {"INR": 50, "USD": 50, "SAR": 50, "AED": 50}
    assert set(Counter(k for *_, k in plan).values()) == {25}


def test_receipt_arithmetic_is_consistent():
    for idx, currency, number_format, kind in generate.build_plan(generate.SEED):
        r = generate.build_receipt(idx, currency, number_format, kind, generate.SEED)
        items = sum(Decimal(i["amount"]) for i in r.items)
        taxes = sum(Decimal(t["amount"]) for t in r.taxes)
        discounts = sum(Decimal(d["amount"]) for d in r.discounts)
        tip = Decimal(r.tip or 0)
        round_off = Decimal(r.round_off or 0)
        total = Decimal(r.total)
        vat_inclusive = currency in ("SAR", "AED") and Decimal(r.subtotal) + taxes == items
        if vat_inclusive:
            assert total == items, r.id
        else:
            assert Decimal(r.subtotal) == items, r.id
            assert total == items - discounts + taxes + tip + round_off, r.id
        assert r.date < "2026-01-01"


def test_printed_text_carries_the_truth():
    r = generate.build_receipt(1, "INR", "indian", "clean", generate.SEED)
    text = "\n".join(t for t, _ in r.lines)
    assert r.merchant_raw in text
    assert generate.format_amount(Decimal(r.total), "indian") in text
    assert r.date_text in text


def test_generation_is_deterministic(tmp_path):
    a = generate.generate(tmp_path / "a", limit=3)
    b = generate.generate(tmp_path / "b", limit=3)
    assert a == b
    assert (tmp_path / "a" / "truth.jsonl").read_bytes() == (tmp_path / "b" / "truth.jsonl").read_bytes()
    first = json.loads((tmp_path / "a" / "truth.jsonl").read_text().splitlines()[0])
    assert first["image_sha256"] and first["source"] == "synthetic"
