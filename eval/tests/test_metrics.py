from decimal import Decimal

import pytest

from metrics import (amount_equal, calibration, compare, f1, format_table, fuzzy_key, macro_f1, match_line_items,
                     names_equal, normalize_name, similarity)


def test_normalize_name_folds_case_punctuation_and_ampersand():
    assert normalize_name("TOTAL WINE & MORE") == "total wine and more"
    assert normalize_name("Bill’s  Diner!") == "bills diner"
    assert normalize_name(None) == ""


def test_fuzzy_key_drops_store_numbers_and_legal_words():
    assert fuzzy_key("STARBUCKS COFFEE #12345") == "starbucks coffee"
    assert fuzzy_key("Haldiram's Snacks Pvt Ltd") == "haldirams snacks"


def test_names_equal_requires_a_name():
    assert names_equal("STARBUCKS", "Starbucks")
    assert not names_equal("", "")
    assert not names_equal(None, "Starbucks")


def test_similarity_tolerates_ocr_noise_but_not_other_names():
    assert similarity("STARBUCKS C0FFEE", "Starbucks Coffee") >= 0.85
    assert similarity("TOTAL", "Total Wine & More") < 0.85
    assert similarity("0042 ANNAPOLIS MD", "Total Wine & More") < 0.5
    assert similarity(None, "x") == 0.0


def test_amount_equal_to_the_cent():
    assert amount_equal(Decimal("12.96"), "12.96")
    assert amount_equal("1.8E+2", "180.00")
    assert not amount_equal("12.95", "12.96")
    assert not amount_equal(None, "1")
    assert not amount_equal("abc", "1")


def test_line_items_match_one_to_one_on_name_and_price():
    truth = [{"name": "Burger Deluxe", "unit_price": "12.00", "amount": "24.00"},
             {"name": "Coffee", "unit_price": "2.50", "amount": "2.50"}]
    predicted = [{"name": "BURGER DELUXE", "price": 12.00}, {"name": "Coffee", "price": 2.5}, {"name": "Coffee", "price": 2.5}]
    assert match_line_items(predicted, truth) == 2
    assert match_line_items([{"name": "Milkshake", "price": 2.5}], truth) == 0


def test_f1_handles_empty_predictions():
    assert f1(0, 0, 10) == 0.0
    assert f1(5, 10, 10) == pytest.approx(0.5)


def test_calibration_perfect_and_overconfident():
    ece, rows = calibration([(0.85, True)] * 85 + [(0.85, False)] * 15)
    assert ece == pytest.approx(0.0)
    assert rows == [{"bin": "0.8-0.9", "n": 100, "mean_conf": 0.85, "accuracy": 0.85}]
    ece, _ = calibration([(0.9, False)] * 4)
    assert ece == pytest.approx(0.9)
    ece, rows = calibration([(1.0, True)])
    assert rows[0]["bin"] == "0.9-1.0" and ece == 0.0


def test_calibration_without_confidence_is_none():
    assert calibration([(None, True), (None, False)]) == (None, [])


def test_macro_f1_averages_truth_classes():
    score, per_class = macro_f1(["A", "A", "B", "B"], ["A", "B", "B", "UNCATEGORIZED"])
    assert per_class["A"] == pytest.approx(2 / 3, abs=1e-4)
    assert per_class["B"] == pytest.approx(0.5)
    assert score == pytest.approx((2 / 3 + 0.5) / 2, abs=1e-4)


def baseline(**over):
    b = {"metrics": {"total": 0.50, "ece": 0.10}, "slices": {"currency=INR": {"n": 50, "total": 0.40}},
         "tolerance": {"overall": 0.01, "slice_items": 1}}
    b.update(over)
    return b


def current(total=0.50, ece=0.10, slice_total=0.40):
    return {"metrics": {"total": total, "ece": ece}, "slices": {"currency=INR": {"n": 50, "total": slice_total}},
            "slice_gated": ["total"]}


def test_compare_passes_within_tolerance():
    assert compare(current(total=0.495, ece=0.105, slice_total=0.38), baseline()) == []


def test_compare_flags_overall_drop_and_ece_rise():
    problems = compare(current(total=0.48, ece=0.12), baseline())
    assert any(p.startswith("overall total") for p in problems)
    assert any(p.startswith("overall ece") for p in problems)


def test_compare_allows_one_item_per_slice_only():
    assert compare(current(slice_total=0.38), baseline()) == []
    assert any("slice currency=INR total" in p for p in compare(current(slice_total=0.36), baseline()))


def test_compare_skips_slices_when_not_gated():
    assert compare(current(slice_total=0.0), baseline(tolerance={"overall": 0.05, "slice_items": None})) == []


def test_compare_reports_a_metric_that_disappeared():
    assert compare(current(ece=None), baseline()) == ["overall ece: missing (baseline 0.1)"]


def test_format_table_renders_none_and_floats():
    text = format_table([{"a": None, "b": 0.5}], ["a", "b"])
    assert "n/a" in text and "0.500" in text
