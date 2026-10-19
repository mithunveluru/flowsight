"""Pass A: a vision model on an OpenAI-compatible chat-completions endpoint (e.g. Groq), asked for
a fixed JSON schema. Only ever sees the masked image. Off unless ML_OCR_LLM_ENABLED is true and
ML_OCR_LLM_MODEL names a model; no model name is assumed."""

import base64
import json
import logging
import urllib.error
import urllib.request
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Callable, Mapping

from app.ocr.text import parse_money

log = logging.getLogger(__name__)

SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "merchant": {"type": ["string", "null"]},
        "date": {"type": ["string", "null"], "description": "as printed"},
        "currency": {"type": ["string", "null"], "description": "ISO 4217 code"},
        "subtotal": {"type": ["string", "null"]},
        "taxes": {"type": "array", "items": {"type": "object", "properties": {
            "label": {"type": "string"}, "amount": {"type": "string"}}}},
        "discounts": {"type": "array", "items": {"type": "object", "properties": {
            "label": {"type": "string"}, "amount": {"type": "string"}}}},
        "tip": {"type": ["string", "null"]},
        "round_off": {"type": ["string", "null"]},
        "total": {"type": ["string", "null"], "description": "the amount actually paid"},
        "line_items": {"type": "array", "items": {"type": "object", "properties": {
            "name": {"type": "string"}, "quantity": {"type": ["string", "null"]}, "amount": {"type": "string"}}}},
    },
    "required": ["merchant", "date", "currency", "total", "line_items"],
}

INSTRUCTIONS = (
    "You read shop receipts. Copy values exactly as printed; do not compute or guess. "
    "Amounts are strings in the receipt's own format. Use null for anything not printed. "
    "Black boxes hide private data; ignore them. Answer with JSON matching the schema."
)


@dataclass(frozen=True)
class VisionConfig:
    enabled: bool
    url: str
    model: str
    api_key: str
    timeout: float

    @classmethod
    def from_env(cls, env: Mapping[str, str]) -> "VisionConfig":
        model = env.get("ML_OCR_LLM_MODEL", "").strip()
        key = env.get("ML_OCR_LLM_API_KEY", "").strip() or env.get("GROQ_API_KEY", "").strip()
        enabled = env.get("ML_OCR_LLM_ENABLED", "").strip().lower() in {"1", "true", "yes", "on"}
        return cls(enabled and bool(model) and bool(key),
                   env.get("ML_OCR_LLM_URL", "https://api.groq.com/openai/v1").rstrip("/"),
                   model, key, float(env.get("ML_OCR_LLM_TIMEOUT", "20")))


@dataclass
class VisionReading:
    merchant: str | None
    date_text: str | None
    currency: str | None
    total: Decimal | None
    subtotal: Decimal | None
    numbers: list[Decimal]
    items: list[tuple[str, str | None, Decimal]]


Transport = Callable[[str, bytes, dict, float], bytes]


def _post(url: str, body: bytes, headers: dict, timeout: float) -> bytes:
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 - configured https endpoint
        return resp.read()


def request_body(config: VisionConfig, png: bytes) -> dict:
    data_url = "data:image/png;base64," + base64.b64encode(png).decode()
    return {
        "model": config.model,
        "temperature": 0,
        "messages": [
            {"role": "system", "content": INSTRUCTIONS},
            {"role": "user", "content": [
                {"type": "text", "text": "Read this receipt."},
                {"type": "image_url", "image_url": {"url": data_url}},
            ]},
        ],
        "response_format": {"type": "json_schema", "json_schema": {"name": "receipt", "schema": SCHEMA}},
    }


def parse_reading(content: str) -> VisionReading | None:
    try:
        data = json.loads(content.strip().removeprefix("```json").removesuffix("```"))
    except (json.JSONDecodeError, AttributeError):
        return None
    if not isinstance(data, dict):
        return None
    numbers = []

    def money(v):
        m = parse_money(v)
        if m is not None:
            numbers.append(abs(m))
        return m

    items = []
    for it in data.get("line_items") or []:
        if isinstance(it, dict) and isinstance(it.get("name"), str):
            amount = money(it.get("amount"))
            if amount is not None:
                items.append((it["name"], it.get("quantity") if isinstance(it.get("quantity"), str) else None, amount))
    for group in ("taxes", "discounts"):
        for t in data.get(group) or []:
            if isinstance(t, dict):
                money(t.get("amount"))
    money(data.get("tip"))
    money(data.get("round_off"))
    subtotal = money(data.get("subtotal"))
    total = money(data.get("total"))
    text = lambda k: data.get(k) if isinstance(data.get(k), str) and data.get(k).strip() else None  # noqa: E731
    return VisionReading(text("merchant"), text("date"), text("currency"), total, subtotal, numbers, items)


def read(config: VisionConfig, png: bytes, transport: Transport = _post) -> VisionReading | None:
    if not config.enabled:
        return None
    body = json.dumps(request_body(config, png)).encode()
    headers = {"Authorization": f"Bearer {config.api_key}", "Content-Type": "application/json"}
    try:
        raw = transport(config.url + "/chat/completions", body, headers, config.timeout)
        content = json.loads(raw)["choices"][0]["message"]["content"]
    except (urllib.error.URLError, TimeoutError, OSError, KeyError, IndexError, TypeError, ValueError) as exc:
        log.warning("vision pass unavailable: %s", type(exc).__name__)
        return None
    return parse_reading(content) if isinstance(content, str) else None
