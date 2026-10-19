import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.capabilities import load_installed
from app.config import Settings
from app.main import create_app
from app.merchant_embeddings import ARTIFACT, CAPABILITY
from app.merchant_embeddings.build import build, main as build_main
from app.merchant_embeddings.vectors import NgramIndex, normalize
from app.registry import ModelRegistry
from tests.conftest import AUTH, TOKEN

URL = "/v1/capabilities/merchant-embeddings/predict"
ALIASES = [
    ("kibsons", "Kibsons"), ("starbucks", "Starbucks"), ("lulu hypermarket", "Lulu Hypermarket"),
    ("lulu", "Lulu Hypermarket"), ("carrefour", "Carrefour"), ("dominos", "Domino's"),
    ("apollo pharmacy", "Apollo Pharmacy"), ("tamimi markets", "Tamimi Markets"), ("netflix", "Netflix"),
]


@pytest.fixture
def client(tmp_path: Path) -> tuple[TestClient, list[dict]]:
    model_dir = tmp_path / "models"
    artifact = model_dir / "merchant-embeddings" / "v1"
    artifact.mkdir(parents=True)
    (artifact / ARTIFACT).write_text(json.dumps([{"alias": a, "canonical": c} for a, c in ALIASES]))
    ModelRegistry(model_dir).register("merchant-embeddings", "v1", status="active")
    records: list[dict] = []
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_MERCHANT_EMBEDDINGS_ENABLED": "true"})
    app = create_app(settings, capabilities=[CAPABILITY], prediction_sink=records.append)
    return TestClient(app), records


def test_is_installed():
    assert [c.name for c in load_installed()] == ["merchant-embeddings"]


@pytest.mark.parametrize("text,canonical", [
    ("Kbsons Fresh", "Kibsons"),
    ("STARBUKS COFFEE", "Starbucks"),
    ("Lulu Hyper Market Al Barsha", "Lulu Hypermarket"),
    ("Carefour City", "Carrefour"),
    ("Tamimi Mkts", "Tamimi Markets"),
])
def test_finds_the_nearest_alias_for_misspellings(text, canonical):
    index = NgramIndex(ALIASES)
    alias, found, score = index.nearest(text)
    assert found == canonical
    assert 0.5 < score <= 1.0


def test_exact_alias_scores_one_and_unrelated_text_scores_low():
    index = NgramIndex(ALIASES)
    assert index.nearest("NETFLIX")[2] == pytest.approx(1.0)
    hit = index.nearest("Qwzx Vplk")
    assert hit is None or hit[2] < 0.3


def test_normalize_folds_case_and_punctuation():
    assert normalize("DOMINO'S  Pizza-Hut") == "dominos pizza hut"


def test_predict_endpoint_serves_the_neighbour_without_logging_text(client):
    http, records = client
    response = http.post(URL, json={"text": "Kbsons Fresh"}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["result"]["canonical"] == "Kibsons"
    assert body["confidence"] == body["result"]["similarity"]
    assert body["summary"]["matched"] is True
    assert "Kbsons" not in json.dumps(records)


def test_input_is_validated(client):
    http, _ = client
    assert http.post(URL, json={"text": ""}, headers=AUTH).status_code == 422
    assert http.post(URL, json={"text": "x" * 201}, headers=AUTH).status_code == 422


def test_capability_is_off_without_its_flag(tmp_path):
    settings = Settings.from_env({"ML_MODEL_DIR": str(tmp_path), "ML_SERVICE_TOKEN": TOKEN})
    http = TestClient(create_app(settings, capabilities=[CAPABILITY], prediction_sink=lambda r: None))
    assert http.post(URL, json={"text": "Kbsons"}, headers=AUTH).status_code == 404


def test_build_writes_the_artifact_from_a_csv_export(tmp_path):
    export = tmp_path / "aliases.csv"
    export.write_text("alias,canonical_name\nkibsons,Kibsons\n,Skipped\nstarbucks,Starbucks\n", encoding="utf-8")
    out = tmp_path / "v2"
    assert build(export, out) == 2
    assert json.loads((out / ARTIFACT).read_text()) == [
        {"alias": "kibsons", "canonical": "Kibsons"}, {"alias": "starbucks", "canonical": "Starbucks"}]
    assert build_main(["--aliases", str(export), "--out", str(tmp_path / "v3")]) == 0


def test_build_refuses_an_empty_export(tmp_path):
    export = tmp_path / "aliases.csv"
    export.write_text("alias,canonical_name\n", encoding="utf-8")
    with pytest.raises(ValueError):
        build(export, tmp_path / "v1")
