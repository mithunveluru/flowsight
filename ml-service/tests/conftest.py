from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.registry import ModelRegistry
from tests.echo import EchoCapability

TOKEN = "test-service-token"


def add_artifact(model_dir: Path, version: str, label: str, capability: str = "echo") -> None:
    path = model_dir / capability / version
    path.mkdir(parents=True)
    (path / "label.txt").write_text(label)


@pytest.fixture
def model_dir(tmp_path: Path) -> Path:
    return tmp_path / "models"


@pytest.fixture
def registry(model_dir: Path) -> ModelRegistry:
    add_artifact(model_dir, "v1", "first")
    add_artifact(model_dir, "v2", "second")
    reg = ModelRegistry(model_dir)
    reg.register("echo", "v1", metrics={"accuracy": 0.5}, training_data={"rows": 10}, status="active")
    reg.register("echo", "v2", status="shadow")
    return reg


@pytest.fixture
def records() -> list[dict]:
    return []


@pytest.fixture
def make_client(model_dir: Path, records: list[dict]):
    def build(env: dict[str, str] | None = None, capability: EchoCapability | None = None) -> TestClient:
        settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN, **(env or {})})
        app = create_app(settings, capabilities=[capability or EchoCapability()], prediction_sink=records.append)
        return TestClient(app)

    return build


AUTH = {"Authorization": f"Bearer {TOKEN}"}
