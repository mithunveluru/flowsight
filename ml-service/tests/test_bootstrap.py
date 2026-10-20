from pathlib import Path

from fastapi.testclient import TestClient

from app.categorizer import CAPABILITY as CATEGORIZER
from app.config import Settings
from app.main import create_app
from app.ocr import OcrCapability
from app.registry import ModelRegistry
from tests.conftest import AUTH, TOKEN
from tests.echo import EchoCapability


class BrokenCapability(EchoCapability):
    name = "broken"

    def bootstrap(self, registry):
        raise OSError("disk full")


def client(model_dir: Path, capabilities, **env) -> TestClient:
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_CATEGORIZER_ENABLED": "true", **env})
    return TestClient(create_app(settings, capabilities=capabilities, prediction_sink=lambda r: None))


def health(c: TestClient) -> dict[str, dict]:
    return {cap["name"]: cap for cap in c.get("/health").json()["capabilities"]}


def test_fresh_volume_serves_the_bundled_models(tmp_path: Path):
    c = client(tmp_path, [CATEGORIZER, OcrCapability()])
    caps = health(c)
    assert caps["categorizer"]["active"] and caps["categorizer"]["bootstrap"].startswith("installed")
    assert caps["ocr"]["active"] == "v1"

    r = c.post("/v1/capabilities/categorizer/predict", headers=AUTH,
               json={"items": [{"description": "NETFLIX.COM subscription", "merchant": "Netflix"}]})
    assert r.status_code == 200
    assert r.json()["model_version"] == caps["categorizer"]["active"]


def test_restart_does_not_reinstall(tmp_path: Path):
    client(tmp_path, [CATEGORIZER])
    caps = health(client(tmp_path, [CATEGORIZER]))
    assert "bootstrap" not in caps["categorizer"]
    assert len(ModelRegistry(tmp_path).list("categorizer")) == 1


def test_operator_choices_are_left_alone(tmp_path: Path):
    client(tmp_path, [CATEGORIZER])
    reg = ModelRegistry(tmp_path)
    version = reg.find("categorizer", "active").version
    reg.set_status("categorizer", version, "retired")

    caps = health(client(tmp_path, [CATEGORIZER]))
    assert caps["categorizer"]["active"] is None
    assert "bootstrap" not in caps["categorizer"]


def test_can_be_switched_off(tmp_path: Path):
    caps = health(client(tmp_path, [CATEGORIZER], ML_BOOTSTRAP="false"))
    assert caps["categorizer"]["active"] is None
    assert not ModelRegistry(tmp_path).list()


def test_a_failing_bootstrap_does_not_stop_the_service(tmp_path: Path):
    caps = health(client(tmp_path, [BrokenCapability(), CATEGORIZER]))
    assert caps["broken"]["bootstrap"] == "failed: OSError"
    assert caps["categorizer"]["active"]
