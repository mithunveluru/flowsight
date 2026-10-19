from decimal import Decimal

from app.ocr.reconcile import reconcile, tolerance
from app.ocr.text import read_pass
from tests.ocr_fakes import lines


def p(*t):
    return read_pass(lines(*t))


def test_indian_bill_reconciles_with_discount_round_off_and_payment():
    t = reconcile([p("CHAI POINT", "Cappuccino 1 180.00 180.00", "Veg Puff 1 75.00 75.00", "Sub Total 255.00",
                     "Discount -12.75", "CGST @2.5% 6.06", "SGST @2.5% 6.06", "Round Off -0.37",
                     "Net Payable Rs.254.00", "Paid by Card 254.00")])
    assert t.value == Decimal("254.00") and t.decision == "ACCEPT" and t.items_reconciled


def test_tendered_and_tip_handling():
    t = reconcile([p("WINE", "Cabernet 19.99", "Total Items: 1", "SUBTOTAL 19.99", "SALES TAX 6% 1.20", "TOTAL $21.19",
                     "Total Tendered 40.00", "CASH 40.00", "CHANGE 18.81")])
    assert t.value == Decimal("21.19") and not t.conflict
    t = reconcile([p("DINER", "Burger 12.00", "Coffee 2.50", "SUBTOTAL 14.50", "SALES TAX 8% 1.16", "TOTAL $15.66",
                     "TIP 2.90", "GRAND TOTAL $18.56", "VISA ****1234 18.56")])
    assert t.value == Decimal("18.56") and not t.conflict


def test_conflict_offers_both_totals():
    a = p("SHOP", "Item 1985.60", "Sub Total 1985.60", "CGST 49.63", "SGST 49.63", "Round Off -0.86", "Total 2084.00")
    b = p("SHOP", "Item 1985.00", "Sub Total 1985.00", "CGST 49.63", "SGST 49.63", "Round Off -0.26", "Total 2084.60",
          "Paid by UPI 2084.60")
    t = reconcile([a, b])
    assert t.conflict and t.decision == "ASK_USER"
    assert {str(v) for v, _, _ in t.options} >= {"2084.00", "2084.60"}


def test_vision_total_only_counts_when_tesseract_read_it():
    one = p("SHOP", "Total 45.00")
    assert reconcile([one], Decimal("45.00")).basis.endswith("vision")
    assert reconcile([one], Decimal("54.00")).value == Decimal("45.00")


def test_nothing_and_tolerance():
    assert reconcile([p("SHOP", "Thank you")]).value is None
    assert tolerance(0) == 0 and tolerance(2) == Decimal("0.02")
