from datetime import date
from decimal import Decimal

import pytest

from app.ocr.text import (classify, clean_merchant, detect_currency, excluded, find_amounts, find_date, is_heading,
                          parse_money, read_pass)
from tests.ocr_fakes import lines


@pytest.mark.parametrize("raw,expected", [
    ("1,234.50", "1234.50"), ("1.234,50", "1234.50"), ("1,23,456.00", "123456.00"), ("Rs.254.00", "254.00"),
    ("SAR 45.00", "45.00"), ("١٢٣٫٥٠", "123.50"), ("١٬٢٣٤٫٥٠", "1234.50"), ("1, 335.56", "1335.56"),
    ("(12.75)", "-12.75"), ("-0.37", "-0.37"), (12.5, "12.50"),
])
def test_parse_money_formats(raw, expected):
    assert parse_money(raw) == Decimal(expected)


def test_percentages_and_glued_numbers_are_not_amounts():
    assert [a.value for a in find_amounts("CGST @2.5% 6.06")] == [Decimal("6.06")]
    assert find_amounts("Total Incl. VAT AED 4,VO") == []
    assert parse_money("twelve") is None


@pytest.mark.parametrize("line,kind", [
    ("Total Items: 4", "ITEM_COUNT"), ("Total Tendered 40.00", "TENDERED"), ("CASH 40.00", "TENDERED"),
    ("CHANGE 6.25", "CHANGE"), ("GRAND TOTAL $33.75", "GRAND_TOTAL"), ("TOTAL $29.65", "TOTAL"),
    ("Total Excl. VAT 39.13", "SUBTOTAL"), ("Total Incl. VAT SAR 45.00", "GRAND_TOTAL"), ("VISA ****1234 21.19", "PAYMENT"),
    ("CGST @2.5% 6.06", "TAX"), ("Round Off -0.37", "ROUND_OFF"), ("TIP 4.10", "TIP"), ("Seedless Grapes 4.50", "OTHER"),
])
def test_labels_by_whole_tokens(line, kind):
    assert classify(line) == kind


def test_currency_cues_and_unknown():
    assert detect_currency("GSTIN: 29AAACV4807F1Z6\nNet Payable Rs.254.00")[0] == "INR"
    assert detect_currency("Riyadh\nVAT No: 312345678901233\nTotal SAR 45.00")[0] == "SAR"
    assert detect_currency("Dubai\nTRN: 100123456789012")[0] == "AED"
    assert detect_currency("Chicago IL 60606\nSALES TAX 1.02\nTOTAL $10.97")[0] == "USD"
    assert detect_currency("Total 2.00") == (None, 0.0)


def test_dates_follow_currency_and_never_default():
    today = date(2026, 1, 15)
    assert find_date(["03/04/2025"], "USD", today).value == date(2025, 3, 4)
    assert find_date(["03/04/2025"], "INR", today).value == date(2025, 4, 3)
    assert find_date(["03/04/2025"], "USD", today).ambiguous
    assert find_date(["Date: 18 Nov 2024"], None, today).value == date(2024, 11, 18)
    assert find_date(["1 Market St, San Francisco"], "USD", today).value is None
    assert find_date(["TOTAL 45.00"], "INR", today).value is None


def test_merchant_rules():
    assert is_heading("ORIGINAL FOR RECIPIENT") and is_heading("SIMPLIFIED TAX INVOICE")
    assert not excluded("TOTAL WINE & MORE") and not excluded("BILL'S DINER")
    assert excluded("Server: Mike") and excluded("Store #0042") and excluded("Al Qasba, Sharjah")
    assert clean_merchant("STARBUCKS COFFEE #12345") == "STARBUCKS COFFEE"
    assert clean_merchant("HALDIRAM'S SNACKS PVT LTD") == "HALDIRAM'S SNACKS"


def test_read_pass_items_and_merchant():
    p = read_pass(lines("TAX INVOICE", "CROMA", "MG Road, Bengaluru 560001", "Samsung 55in TV 1 54,990.00 54,990.00",
                        "Maggi 12 Pack 2 168.00 336.00", "Sub Total 55,326.00", "Total 55,326.00"))
    assert p.merchant[0] == "CROMA"
    assert [(i.name, i.quantity, i.amount) for i in p.items] == [
        ("Samsung 55in TV", 1, Decimal("54990.00")), ("Maggi 12 Pack", 2, Decimal("336.00"))]
