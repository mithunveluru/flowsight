import pytest

from tests.conftest import AUTH, TOKEN


def test_health_is_public_and_summarizes_models(registry, make_client):
    response = make_client({"ML_ECHO_ENABLED": "true"}).get("/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "capabilities": [
            {"name": "echo", "enabled": True, "shadow_mode": False, "active": "v1", "shadow": "v2"}
        ],
    }


@pytest.mark.parametrize(
    "headers",
    [
        {},
        {"Authorization": "Bearer wrong"},
        {"Authorization": f"Basic {TOKEN}"},
        {"Authorization": TOKEN},
        {"Authorization": f"Bearer {TOKEN}x"},
    ],
)
def test_protected_endpoints_reject_bad_credentials(registry, make_client, headers):
    client = make_client({"ML_ECHO_ENABLED": "true"})
    assert client.get("/v1/models", headers=headers).status_code == 401
    assert client.post("/v1/capabilities/echo/predict", json={"text": "a"}, headers=headers).status_code == 401


def test_unset_token_fails_closed(registry, make_client):
    client = make_client({"ML_SERVICE_TOKEN": "", "ML_ECHO_ENABLED": "true"})
    assert client.get("/v1/models", headers={"Authorization": "Bearer "}).status_code == 401
    assert client.get("/health").status_code == 200


def test_models_listing_by_capability(registry, make_client):
    client = make_client()
    body = client.get("/v1/models", params={"capability": "echo"}, headers=AUTH).json()
    assert [(m["version"], m["status"]) for m in body["models"]] == [("v1", "active"), ("v2", "shadow")]
    assert client.get("/v1/models", params={"capability": "none"}, headers=AUTH).json() == {"models": []}


def test_openapi_and_docs_are_not_exposed(make_client):
    client = make_client()
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/docs").status_code == 404
