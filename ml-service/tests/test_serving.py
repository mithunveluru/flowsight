from app.config import Settings
from app.registry import ModelRegistry
from tests.conftest import AUTH, add_artifact
from tests.echo import EchoCapability

URL = "/v1/capabilities/echo/predict"
SECRET_TEXT = "Card 4111 at Joe's Pharmacy, phone 555-0100"


def test_capability_flag_defaults_off(registry, make_client, records):
    response = make_client().post(URL, json={"text": "a"}, headers=AUTH)
    assert response.status_code == 404
    assert records == []


def test_flag_names_follow_capability_name():
    settings = Settings.from_env({"ML_MERCHANT_EMBEDDINGS_ENABLED": "1", "ML_MERCHANT_EMBEDDINGS_SHADOW": "yes"})
    assert settings.enabled("merchant-embeddings") and settings.shadow("merchant-embeddings")
    assert not settings.enabled("echo")


def test_enabled_capability_serves_active_model(registry, make_client, records):
    response = make_client({"ML_ECHO_ENABLED": "true"}).post(URL, json={"text": "abcd"}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["model_version"] == "v1"
    assert body["result"] == {"label": "first", "length": 4, "confidence": 0.75}
    assert body["confidence"] == 0.75
    assert body["summary"] == {"label": "first"}
    assert [r["role"] for r in records] == ["active"]


def test_request_body_is_validated(registry, make_client):
    response = make_client({"ML_ECHO_ENABLED": "true"}).post(URL, json={"wrong": 1}, headers=AUTH)
    assert response.status_code == 422


def test_shadow_mode_returns_active_result_and_records_both(registry, make_client, records):
    client = make_client({"ML_ECHO_ENABLED": "true", "ML_ECHO_SHADOW": "true"})
    body = client.post(URL, json={"text": "abc"}, headers=AUTH).json()
    assert body["result"]["label"] == "first"
    assert [(r["role"], r["model_version"]) for r in records] == [("active", "v1"), ("shadow", "v2")]
    assert records[1]["output_summary"] == {"label": "second"}
    assert records[0]["request_id"] == records[1]["request_id"] == body["request_id"]


def test_shadow_registered_but_mode_off_runs_only_active(registry, make_client, records):
    make_client({"ML_ECHO_ENABLED": "true"}).post(URL, json={"text": "abc"}, headers=AUTH)
    assert [r["role"] for r in records] == ["active"]


def test_shadow_failure_never_affects_the_served_result(model_dir, make_client, records):
    add_artifact(model_dir, "good", "fine")
    add_artifact(model_dir, "bad", "boom")
    reg = ModelRegistry(model_dir)
    reg.register("echo", "good", status="active")
    reg.register("echo", "bad", status="shadow")
    response = make_client({"ML_ECHO_ENABLED": "true", "ML_ECHO_SHADOW": "true"}).post(
        URL, json={"text": "x"}, headers=AUTH
    )
    assert response.status_code == 200
    assert response.json()["result"]["label"] == "fine"
    assert records[1]["role"] == "shadow" and records[1]["error"] == "RuntimeError"


def test_no_active_model_is_503(model_dir, make_client):
    response = make_client({"ML_ECHO_ENABLED": "true"}).post(URL, json={"text": "x"}, headers=AUTH)
    assert response.status_code == 503


def test_model_failure_is_500_and_logged_without_input(model_dir, make_client, records):
    add_artifact(model_dir, "bad", "boom")
    ModelRegistry(model_dir).register("echo", "bad", status="active")
    response = make_client({"ML_ECHO_ENABLED": "true"}).post(URL, json={"text": SECRET_TEXT}, headers=AUTH)
    assert response.status_code == 500
    assert response.json() == {"detail": "prediction failed"}
    assert records[0]["error"] == "RuntimeError"
    assert SECRET_TEXT not in str(records)


def test_prediction_log_holds_hashes_and_summaries_only(registry, make_client, records):
    client = make_client({"ML_ECHO_ENABLED": "true", "ML_ECHO_SHADOW": "true"})
    client.post(URL, json={"text": SECRET_TEXT}, headers=AUTH)
    client.post(URL, json={"text": SECRET_TEXT}, headers=AUTH)
    assert SECRET_TEXT not in str(records)
    assert "4111" not in str(records)
    first = records[0]
    assert set(first) == {
        "ts", "request_id", "capability", "model_version", "role", "latency_ms",
        "input_hash", "input_summary", "output_summary", "error",
    }
    assert first["capability"] == "echo"
    assert first["input_summary"] == {"length": len(SECRET_TEXT)}
    assert first["latency_ms"] >= 0
    assert len(first["input_hash"]) == 64
    assert len({r["input_hash"] for r in records}) == 1


def test_activation_change_is_picked_up_without_restart(registry, model_dir, make_client):
    capability = EchoCapability()
    client = make_client({"ML_ECHO_ENABLED": "true"}, capability)
    assert client.post(URL, json={"text": "x"}, headers=AUTH).json()["model_version"] == "v1"
    assert client.post(URL, json={"text": "x"}, headers=AUTH).json()["model_version"] == "v1"
    ModelRegistry(model_dir).set_status("echo", "v2", "active")
    assert client.post(URL, json={"text": "x"}, headers=AUTH).json()["result"]["label"] == "second"
    assert capability.loads == ["v1", "v2"]
