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
        assert headers["User-Agent"] != ""
        return json.dumps({"choices": [{"message": {"content": json.dumps({"merchant": "X", "total": "9.50"})}}]}).encode()

    assert vision.read(CFG, b"png", ok).total == Decimal("9.50")


def _http_error(code: int, body: str):
    import io
    import urllib.error

    def transport(url, body_, headers, timeout):
        raise urllib.error.HTTPError(url, code, "error", {}, io.BytesIO(body.encode()))
    return transport


def test_a_refused_model_is_reported_and_not_retried_on_every_receipt():
    cfg = vision.VisionConfig(True, "https://llm.example/v1", "retired-vision-model", "key", 5)
    vision._refused_until.clear()
    refused = _http_error(404, '{"error": {"code": "model_not_found"}}')

    assert vision.read_with_status(cfg, b"png", refused) == (None, "unavailable: HTTP 404")

    def must_not_be_called(*args):
        raise AssertionError("refused model was called again")
    assert vision.read_with_status(cfg, b"png", must_not_be_called) == (None, "skipped: model refused recently")


def test_a_model_blocked_for_the_project_counts_as_refused():
    cfg = vision.VisionConfig(True, "https://llm.example/v1", "blocked-model", "key", 5)
    vision._refused_until.clear()
    blocked = _http_error(403, '{"error": {"code": "model_permission_blocked_project"}}')
    assert vision.read_with_status(cfg, b"png", blocked) == (None, "unavailable: HTTP 403")


def test_other_errors_are_reported_and_retried():
    cfg = vision.VisionConfig(True, "https://llm.example/v1", "busy-model", "key", 5)
    vision._refused_until.clear()
    assert vision.read_with_status(cfg, b"png", _http_error(503, "overloaded")) == (None, "failed: HTTP 503")
    assert vision.read_with_status(cfg, b"png", _http_error(503, "overloaded"))[1] == "failed: HTTP 503"


def test_status_says_when_the_pass_ran_or_was_off():
    vision._refused_until.clear()
    assert vision.read_with_status(vision.VisionConfig.from_env({}), b"png") == (None, "off")

    def ok(url, body, headers, timeout):
        return json.dumps({"choices": [{"message": {"content": json.dumps({"merchant": "X", "total": "9.50"})}}]}).encode()
    reading, status = vision.read_with_status(CFG, b"png", ok)
    assert status == "used" and reading.total == Decimal("9.50")
