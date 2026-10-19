import hashlib
import json
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.categorizer import CAPABILITY, MAX_ITEMS, corpus
from app.categorizer.install import BUNDLED, install
from app.categorizer.model import META, WEIGHTS, CategoryModel, clean, features
from app.categorizer.train import fit, train
from app.config import Settings
from app.main import create_app
from app.registry import ModelRegistry, RegistryError
from tests.conftest import AUTH, TOKEN

URL = "/v1/capabilities/categorizer/predict"


@pytest.fixture(scope="module")
def small_rows() -> list[dict]:
    return corpus.build(7)[::8]


@pytest.fixture
def client(tmp_path: Path) -> tuple[TestClient, list[dict], Path]:
    model_dir = tmp_path / "models"
    install(model_dir, "active")
    records: list[dict] = []
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_CATEGORIZER_ENABLED": "true"})
    app = create_app(settings, capabilities=[CAPABILITY], prediction_sink=records.append)
    return TestClient(app), records, model_dir


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_clean_drops_references_cards_and_punctuation():
    assert clean("UPI/DR/412345678901/SWIGGY/YESB/swiggy@ybl/Pay") == "upi dr swiggy yesb swiggy ybl pay"
    assert clean("POS 532122XXXXXX3307 DOMINOS PIZZA PUNE") == "pos dominos pizza pune"
    assert clean("7-ELEVEN #33421 AUSTIN TX") == "0 eleven austin tx"
    assert clean(None) == ""


def test_features_mark_their_source_and_direction():
    tokens = features({"merchant": "Swiggy", "description": "UPI SWIGGY", "direction": "DEBIT"})
    assert "m|swiggy" in tokens and "w|upi" in tokens and "b|upi swiggy" in tokens
    assert "c| sw" in tokens and "d|debit" in tokens
    assert not any(t.startswith("d|") for t in features({"description": "x", "direction": None}))


def test_corpus_is_seeded():
    assert corpus.build(7)[:50] == corpus.build(7)[:50]
    assert corpus.build(7)[:50] != corpus.build(8)[:50]


def test_training_is_byte_reproducible(tmp_path, small_rows):
    a = train(tmp_path / "a", rows=small_rows, c_grid=(4.0,), folds=3)
    b = train(tmp_path / "b", rows=small_rows, c_grid=(4.0,), folds=3)
    assert a == b
    for name in (WEIGHTS, META):
        assert sha(tmp_path / "a" / name) == sha(tmp_path / "b" / name)


def test_numpy_inference_matches_scikit_learn(tmp_path, small_rows):
    train(tmp_path / "m", rows=small_rows, c_grid=(4.0,), folds=3)
    model = CategoryModel.load(tmp_path / "m")
    vectorizer, clf = fit(small_rows, 4.0)
    sample = small_rows[:40]
    order = [list(clf.classes_).index(c) for c in model.classes]
    expected = clf.decision_function(vectorizer.transform(sample))[:, order]
    np.testing.assert_allclose(model.logits(sample), expected, atol=1e-4)


def test_temperature_does_not_worsen_validation_log_loss(tmp_path, small_rows):
    meta = train(tmp_path / "m", rows=small_rows, c_grid=(4.0,), folds=3)
    assert meta["validation"]["chosen"]["nll"] <= meta["validation"]["before_temperature"]["nll"]


def test_bundled_artifact_comes_from_the_committed_corpus():
    meta = json.loads((BUNDLED / META).read_text())
    rows = corpus.build(meta["training_data"]["seed"])
    assert hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest() == meta["training_data"]["sha256"]


@pytest.mark.parametrize("item,category", [
    ({"description": "UPI/DR/412345678901/SWIGGY/YESB/swiggy@ybl/Pay", "merchant": "Swiggy"}, "FOOD_DINING"),
    ({"description": "NETFLIX.COM CA", "merchant": "Netflix"}, "ENTERTAINMENT"),
    ({"description": "MADA PURCHASE PANDA RIYADH", "merchant": "Panda"}, "GROCERIES"),
    ({"description": "DEWA BILL PAYMENT", "merchant": "DEWA"}, "UTILITIES"),
    ({"description": "ACME CORP PAYROLL PPD ID: 99812", "merchant": "", "direction": "CREDIT"}, "INCOME"),
])
def test_bundled_model_on_obvious_rows(item, category):
    assert CategoryModel.load(BUNDLED).predict([item])[0]["category"] == category


def test_top_three_are_ranked_probabilities():
    model = CategoryModel.load(BUNDLED)
    for p in model.predict([{"description": "AL NOOR TRADING LLC"}, {"description": "", "merchant": None}]):
        probs = [a["probability"] for a in p["alternatives"]]
        assert len(probs) == 3 and probs == sorted(probs, reverse=True)
        assert p["category"] == p["alternatives"][0]["category"] and p["probability"] == probs[0]
        assert 0 < sum(probs) <= 1 + 1e-9
    row = model.predict_proba([{"description": "anything"}])[0]
    assert row.sum() == pytest.approx(1.0)


def test_api_returns_one_prediction_per_item_with_the_model_version(client):
    http, records, _ = client
    body = {"items": [{"description": "POS 4111XXXXXX1111 STARBUCKS DUBAI AE", "merchant": "Starbucks"},
                      {"description": "ZELLE TO JOHN SMITH", "merchant": "John Smith", "direction": "DEBIT"}]}
    r = http.post(URL, json=body, headers=AUTH)
    assert r.status_code == 200
    data = r.json()
    version = json.loads((BUNDLED / META).read_text())["version"]
    assert data["model_version"] == version
    predictions = data["result"]["predictions"]
    assert [p["category"] for p in predictions] == ["FOOD_DINING", "TRANSFER"]
    assert all(len(p["alternatives"]) == 3 for p in predictions)
    assert 0 < data["confidence"] <= 1
    logged = json.dumps(records)
    assert "STARBUCKS" not in logged and "JOHN" not in logged
    assert records[0]["input_summary"] == {"items": 2, "with_merchant": 2, "with_direction": 1}


def test_api_validates_the_batch(client):
    http, _, _ = client
    assert http.post(URL, json={"items": []}, headers=AUTH).status_code == 422
    too_many = {"items": [{"description": "x"}] * (MAX_ITEMS + 1)}
    assert http.post(URL, json=too_many, headers=AUTH).status_code == 422
    assert http.post(URL, json={"items": [{"direction": "SIDEWAYS"}]}, headers=AUTH).status_code == 422


def test_api_is_off_without_the_flag(tmp_path):
    model_dir = tmp_path / "models"
    install(model_dir, "active")
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN})
    http = TestClient(create_app(settings, capabilities=[CAPABILITY], prediction_sink=[].append))
    assert http.post(URL, json={"items": [{"description": "x"}]}, headers=AUTH).status_code == 404


def test_install_registers_once_and_refuses_foreign_files(tmp_path):
    model_dir = tmp_path / "models"
    first = install(model_dir, "shadow")
    assert first.status == "shadow" and first.metrics["cv_accuracy"] > 0
    assert install(model_dir, "active").status == "active"
    assert len(ModelRegistry(model_dir).list("categorizer")) == 1
    (model_dir / "categorizer" / first.version / META).write_text("{}")
    with pytest.raises(RegistryError):
        install(model_dir, "active")
