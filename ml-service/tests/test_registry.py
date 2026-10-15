import json

import pytest

from app import cli
from app.config import Settings
from app.registry import ModelRegistry, RegistryError
from tests.conftest import add_artifact


def statuses(reg: ModelRegistry) -> dict[str, str]:
    return {m.version: m.status for m in reg.list("echo")}


def test_register_records_metadata_and_persists(registry, model_dir):
    reloaded = ModelRegistry(model_dir)
    v1 = reloaded.find("echo", "active")
    assert v1.version == "v1"
    assert v1.metrics == {"accuracy": 0.5}
    assert v1.training_data == {"rows": 10}
    assert v1.created_at.tzinfo is not None
    assert reloaded.find("echo", "shadow").version == "v2"


def test_new_registration_does_not_serve_by_default(registry, model_dir):
    add_artifact(model_dir, "v3", "third")
    assert registry.register("echo", "v3").status == "retired"
    assert registry.find("echo", "active").version == "v1"


def test_activation_retires_previous_active(registry):
    registry.set_status("echo", "v2", "active")
    assert statuses(registry) == {"v1": "retired", "v2": "active"}
    assert registry.find("echo", "shadow") is None


def test_only_one_shadow_per_capability(registry, model_dir):
    add_artifact(model_dir, "v3", "third")
    registry.register("echo", "v3", status="shadow")
    assert statuses(registry) == {"v1": "active", "v2": "retired", "v3": "shadow"}


def test_capabilities_are_independent(registry, model_dir):
    add_artifact(model_dir, "v1", "x", capability="other")
    registry.register("other", "v1", status="active")
    assert registry.find("echo", "active").version == "v1"
    assert [m.capability for m in registry.list("other")] == ["other"]


def test_register_rejects_missing_artifact_duplicate_and_unsafe_names(registry):
    with pytest.raises(RegistryError):
        registry.register("echo", "v9")
    with pytest.raises(RegistryError):
        registry.register("echo", "v1")
    with pytest.raises(ValueError):
        registry.register("echo", "../escape")
    with pytest.raises(RegistryError):
        registry.set_status("echo", "v9", "active")


def test_refresh_picks_up_changes_made_by_another_process(registry, model_dir):
    other = ModelRegistry(model_dir)
    other.set_status("echo", "v2", "active")
    assert registry.refresh() is True
    assert registry.find("echo", "active").version == "v2"
    assert registry.refresh() is False


def test_cli_lifecycle(model_dir, capsys):
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir)})
    add_artifact(model_dir, "v1", "first")
    assert cli.main(["register", "echo", "v1", "--metrics", json.dumps({"f1": 0.8})], settings) == 0
    assert cli.main(["activate", "echo", "v1"], settings) == 0
    assert cli.main(["list", "echo"], settings) == 0
    out = capsys.readouterr().out
    assert "echo\tv1\tactive" in out
    assert cli.main(["retire", "echo", "v1"], settings) == 0
    assert ModelRegistry(model_dir).find("echo", "active") is None
    assert cli.main(["activate", "echo", "missing"], settings) == 1
    assert cli.main(["register", "echo", "v2", "--metrics", "[1]"], settings) == 1
