import json
from decimal import Decimal

from app.ocr import vision

CFG = vision.VisionConfig(True, "https://llm.example/v1", "some-vision-model", "key", 5)


def test_disabled_without_flag_model_or_key():
    assert not vision.VisionConfig.from_env({}).enabled
    assert not vision.VisionConfig.from_env({"ML_OCR_LLM_ENABLED": "true", "GROQ_API_KEY": "k"}).enabled
    assert vision.VisionConfig.from_env({"ML_OCR_LLM_ENABLED": "true", "ML_OCR_LLM_MODEL": "m", "GROQ_API_KEY": "k"}).enabled
    assert vision.read(vision.VisionConfig.from_env({}), b"png") is None


def test_request_is_schema_constrained_and_carries_the_image():
    body = vision.request_body(CFG, b"\x89PNG")
    assert body["model"] == "some-vision-model"
    assert body["response_format"]["type"] == "json_schema"
    assert body["messages"][1]["content"][1]["image_url"]["url"].startswith("data:image/png;base64,")


def test_string_and_locale_amounts_do_not_void_the_reading():
    r = vision.parse_reading(json.dumps({
        "merchant": "Lulu", "date": "09/03/2024", "currency": "AED", "total": "1,234.50", "subtotal": "abc",
        "line_items": [{"name": "Dates", "amount": "١٢٫٠٠"}, {"name": "Bad", "amount": None}]}))
    assert r.merchant == "Lulu" and r.total == Decimal("1234.50") and r.subtotal is None
    assert r.items == [("Dates", None, Decimal("12.00"))]
    assert vision.parse_reading("not json") is None


def test_transport_failures_fall_back_to_none():
    def broken(url, body, headers, timeout):
        raise TimeoutError()

    assert vision.read(CFG, b"png", broken) is None

    def ok(url, body, headers, timeout):
        assert url == "https://llm.example/v1/chat/completions" and headers["Authorization"] == "Bearer key"
        return json.dumps({"choices": [{"message": {"content": json.dumps({"merchant": "X", "total": "9.50"})}}]}).encode()

    assert vision.read(CFG, b"png", ok).total == Decimal("9.50")
