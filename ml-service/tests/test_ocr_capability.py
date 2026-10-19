import base64
import shutil

import pytest

from app.ocr import OcrCapability
from app.registry import ModelRegistry
from tests.conftest import AUTH
from tests.ocr_fakes import FakeRunner, lines, receipt_png
from tests.test_ocr_pipeline import GOOD


@pytest.fixture
def ocr_registry(model_dir):
    (model_dir / "ocr" / "v1").mkdir(parents=True)
    (model_dir / "ocr" / "v1" / "config.json").write_text('{"tesseract_languages": "eng"}')
    ModelRegistry(model_dir).register("ocr", "v1", status="active")


def body():
    return {"image_base64": base64.b64encode(receipt_png()).decode(), "mime_type": "image/png"}


def test_predict_returns_fields_and_logs_no_receipt_content(make_client, ocr_registry, records):
    client = make_client({"ML_OCR_ENABLED": "true"}, OcrCapability(env={}, runner=FakeRunner(GOOD)))
    resp = client.post("/v1/capabilities/ocr/predict", json=body(), headers=AUTH)
    assert resp.status_code == 200
    data = resp.json()
    assert data["model_version"] == "v1"
    assert data["result"]["amount"] == "268.00" and data["result"]["fields"]["amount"]["decision"] == "ACCEPT"
    assert data["confidence"] == data["result"]["fields"]["amount"]["confidence"]
    logged = str(records)
    assert "CHAI" not in logged and "268" not in logged


def test_flag_off_is_404_and_bad_input_is_rejected(make_client, ocr_registry):
    client = make_client({}, OcrCapability(env={}, runner=FakeRunner(GOOD)))
    assert client.post("/v1/capabilities/ocr/predict", json=body(), headers=AUTH).status_code == 404
    client = make_client({"ML_OCR_ENABLED": "true"}, OcrCapability(env={}, runner=FakeRunner(GOOD)))
    assert client.post("/v1/capabilities/ocr/predict", json={"image_base64": "%%%"}, headers=AUTH).status_code == 500
    assert client.post("/v1/capabilities/ocr/predict", json={"mime_type": "x"}, headers=AUTH).status_code == 422


@pytest.mark.skipif(shutil.which("tesseract") is None, reason="tesseract not installed")
def test_real_tesseract_reads_a_rendered_receipt(make_client, ocr_registry):
    import cv2
    import numpy as np
    text = ["CHAI POINT", "Date: 18/11/2024", "Cappuccino      180.00", "Veg Puff         75.00", "Sub Total       255.00",
            "CGST 2.5%         6.38", "SGST 2.5%         6.38", "Round Off        +0.24", "Net Payable     268.00",
            "Paid by Card    268.00"]
    img = np.full((60 + 50 * len(text), 760), 245, np.uint8)
    for i, t in enumerate(text):
        cv2.putText(img, t, (30, 60 + 50 * i), cv2.FONT_HERSHEY_SIMPLEX, 1.0, 20, 2, cv2.LINE_AA)
    payload = {"image_base64": base64.b64encode(cv2.imencode(".png", img)[1].tobytes()).decode()}
    client = make_client({"ML_OCR_ENABLED": "true"}, OcrCapability(env={}))
    data = client.post("/v1/capabilities/ocr/predict", json=payload, headers=AUTH).json()["result"]
    assert data["amount"] == "268.00"


def test_is_installed():
    from app.capabilities import load_installed
    assert "ocr" in [c.name for c in load_installed()]
