import json
from datetime import date

import cv2
import numpy as np

from app.ocr import pipeline, vision
from tests.ocr_fakes import FakeRunner, lines, receipt_png

OFF = vision.VisionConfig(False, "", "", "", 1)
ON = vision.VisionConfig(True, "https://llm.example/v1", "m", "k", 1)
TODAY = date(2026, 1, 15)

GOOD = lines("CHAI POINT", "Ph: +91 6820447270", "Date: 18/11/2024", "Cappuccino 1 180.00 180.00",
             "Veg Puff 1 75.00 75.00", "Sub Total 255.00", "CGST @2.5% 6.38", "SGST @2.5% 6.38", "Round Off +0.24",
             "Net Payable Rs.268.00", "Paid by Card 268.00")


def test_settled_pass_stops_early_and_fields_are_assessed():
    runner = FakeRunner(GOOD)
    out = pipeline.run(receipt_png(), runner, OFF, today=TODAY)
    r = out.result
    assert out.passes == 1 and len(runner.images) == 1
    assert r["amount"] == "268.00" and r["currency"] == "INR" and r["date"] == "2024-11-18"
    assert r["merchant"] == "CHAI POINT" and r["fields"]["amount"]["decision"] == "ACCEPT"
    assert r["items_reconciled"]


def test_weak_first_pass_runs_more_passes():
    runner = FakeRunner(lines("SHOP", "Total 45.00"), lines("SHOP", "Total 45.00"), lines("SHOP", "Total 45.00"))
    out = pipeline.run(receipt_png(), runner, OFF, today=TODAY)
    assert out.passes == 3 and out.result["fields"]["amount"]["decision"] != "ACCEPT"


def test_missing_date_is_asked_not_defaulted():
    out = pipeline.run(receipt_png(), FakeRunner(lines("SHOP", "Total 45.00")), OFF, today=TODAY)
    assert out.result["date"] is None and out.result["fields"]["date"]["decision"] == "ASK_USER"


def test_vision_sees_a_masked_image_and_cannot_introduce_numbers():
    seen = {}

    def transport(url, body, headers, timeout):
        payload = json.loads(body)
        seen["image"] = payload["messages"][1]["content"][1]["image_url"]["url"]
        reading = {"merchant": "Chai Point", "total": "999.00", "currency": "INR", "line_items": []}
        return json.dumps({"choices": [{"message": {"content": json.dumps(reading)}}]}).encode()

    runner = FakeRunner(GOOD)
    out = pipeline.run(receipt_png(), runner, ON, transport, today=TODAY)
    r = out.result
    assert out.vision_used and out.reread  # totals disagreed, so the totals band was read again
    assert r["amount"] == "268.00"
    assert "999.00" not in {c["amount"] for c in r["amount_candidates"]}
    # the phone number's word box was blacked out before the image left the service
    import base64
    png = base64.b64decode(seen["image"].split(",", 1)[1])
    sent = cv2.imdecode(np.frombuffer(png, np.uint8), cv2.IMREAD_GRAYSCALE)
    _, phone_left, phone_top, w, h = GOOD[1].words[2]
    assert sent[phone_top + h // 2, phone_left + w // 2] == 0


def test_validator_drops_unread_numbers():
    result = {"amount": "50.00", "subtotal": "10.00", "tip": None, "round_off": None, "taxes": [{"label": "VAT", "amount": "2.00"}],
              "discounts": [], "line_items": [{"name": "A", "quantity": None, "price": "7.00"}],
              "amount_candidates": [{"amount": "50.00", "basis": "total-label"}, {"amount": "553.00", "basis": "derived"}],
              "fields": {"amount": {"confidence": 0.9, "decision": "ACCEPT"}}, "decision": "ACCEPT"}
    from decimal import Decimal
    out = pipeline.validate(result, {Decimal("10.00"), Decimal("7.00")})
    assert out["amount"] is None and out["decision"] == "ASK_USER" and out["taxes"] == []
    assert out["subtotal"] == "10.00" and out["line_items"][0]["price"] == "7.00"
    assert [c["amount"] for c in out["amount_candidates"]] == ["553.00"]
