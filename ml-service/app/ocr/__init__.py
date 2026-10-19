"""Receipt OCR capability: OpenCV preprocessing, multi-pass Tesseract, an optional vision-model
pass on a PII-masked image, and reconciliation of the total. Served as POST
/v1/capabilities/ocr/predict behind ML_OCR_ENABLED; the backend falls back to its own Tesseract
pipeline whenever this is off or fails.

The registered version's artifact directory may hold config.json with
{"tesseract_languages": "eng"}; vision settings come from ML_OCR_LLM_* environment variables.
"""

import base64
import binascii
import json
import os
from pathlib import Path
from typing import Any, Mapping

from pydantic import BaseModel, Field

from app.capability import Capability, Predictor
from app.ocr import pipeline, vision
from app.ocr.tesseract import Runner, make_runner
from app.registry import ModelVersion

# 5 MB upload cap in the backend, base64-encoded
MAX_IMAGE_BASE64 = 7_000_000


class OcrInput(BaseModel):
    image_base64: str = Field(max_length=MAX_IMAGE_BASE64)
    mime_type: str | None = None


class FieldResult(BaseModel):
    confidence: float
    decision: str
    basis: str | None = None


class Charge(BaseModel):
    label: str | None = None
    amount: str


class LineItem(BaseModel):
    name: str
    quantity: str | None = None
    price: str


class AmountOption(BaseModel):
    amount: str
    label: str | None = None
    basis: str | None = None


class OcrOutput(BaseModel):
    merchant: str | None = None
    merchant_raw: str | None = None
    amount: str | None = None
    date: str | None = None
    currency: str | None = None
    subtotal: str | None = None
    taxes: list[Charge] = []
    discounts: list[Charge] = []
    tip: str | None = None
    round_off: str | None = None
    line_items: list[LineItem] = []
    amount_candidates: list[AmountOption] = []
    items_reconciled: bool = False
    fields: dict[str, FieldResult]
    decision: str
    text: str | None = None
    passes: int = 0
    vision_used: bool = False
    reread: bool = False


class OcrCapability(Capability):
    name = "ocr"
    input_model = OcrInput
    output_model = OcrOutput

    def __init__(self, env: Mapping[str, str] | None = None, runner: Runner | None = None,
                 transport: vision.Transport | None = None) -> None:
        self._env = env
        self._runner = runner
        self._transport = transport

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        env = dict(os.environ if self._env is None else self._env)
        config_file = artifact_dir / "config.json"
        config: dict[str, Any] = json.loads(config_file.read_text()) if config_file.exists() else {}
        runner = self._runner or make_runner(config.get("tesseract_languages", "eng"))
        vision_config = vision.VisionConfig.from_env(env)
        transport = self._transport

        def predict(payload: OcrInput) -> OcrOutput:
            try:
                data = base64.b64decode(payload.image_base64, validate=True)
            except (binascii.Error, ValueError) as exc:
                raise ValueError("image_base64 is not valid base64") from exc
            out = pipeline.run(data, runner, vision_config, transport)
            return OcrOutput(**out.result, passes=out.passes, vision_used=out.vision_used, reread=out.reread)

        return predict

    def input_summary(self, payload: OcrInput) -> dict:
        return {"bytes_base64": len(payload.image_base64), "mime_type": payload.mime_type}

    def output_summary(self, result: OcrOutput) -> dict:
        # no merchant names or amounts: decisions and process facts only
        return {"decision": result.decision, "passes": result.passes, "vision": result.vision_used,
                "reread": result.reread, "fields": {k: v.decision for k, v in result.fields.items()}}

    def confidence(self, result: OcrOutput) -> float | None:
        amount = result.fields.get("amount")
        return amount.confidence if amount else None


CAPABILITY = OcrCapability()
