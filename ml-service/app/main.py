import logging
from typing import Any

from fastapi import APIRouter, BackgroundTasks, Depends, FastAPI, HTTPException
from pydantic import BaseModel

from app.auth import require_service_token
from app.capabilities import load_installed
from app.capability import Capability
from app.config import Settings
from app.predictions import PredictionLog, Sink, stdout_sink
from app.registry import ModelRegistry, ModelVersion
from app.runtime import CapabilityDisabled, NoActiveModel, PredictionFailed, Runtime

log = logging.getLogger(__name__)


class PredictResponse(BaseModel):
    request_id: str
    capability: str
    model_version: str
    confidence: float | None
    summary: dict[str, Any]
    result: dict[str, Any]


class ModelList(BaseModel):
    models: list[ModelVersion]


def _predict_route(runtime: Runtime, capability: Capability):
    name = capability.name
    InputModel = capability.input_model

    # The annotation must stay a real class (no postponed annotations in this module)
    # so FastAPI validates the body against the capability's own schema.
    def predict(body: InputModel, background: BackgroundTasks) -> PredictResponse:  # type: ignore[valid-type]
        try:
            served, shadow = runtime.predict(name, body)
        except CapabilityDisabled:
            raise HTTPException(status_code=404, detail="capability not enabled")
        except NoActiveModel:
            raise HTTPException(status_code=503, detail="no active model")
        except PredictionFailed:
            raise HTTPException(status_code=500, detail="prediction failed")
        if shadow is not None:
            background.add_task(runtime.run_shadow, name, shadow, body, served.request_id)
        return PredictResponse(
            request_id=served.request_id,
            capability=name,
            model_version=served.model_version,
            confidence=served.confidence,
            summary=served.summary,
            result=served.result.model_dump(mode="json"),
        )

    return predict


def bootstrap(capabilities: list[Capability], registry: ModelRegistry) -> dict[str, str]:
    """Give every capability the registry has never seen its bundled baseline, so a fresh model
    volume serves models immediately. Capabilities an operator already registered, retired or
    shadowed are left alone."""
    outcome: dict[str, str] = {}
    for capability in capabilities:
        if registry.list(capability.name):
            continue
        try:
            installed = capability.bootstrap(registry)
        except Exception as exc:
            log.exception("bootstrap failed for %s", capability.name)
            outcome[capability.name] = f"failed: {type(exc).__name__}"
            continue
        if installed is not None:
            log.info("bootstrapped %s %s as %s", capability.name, installed.version, installed.status)
            outcome[capability.name] = f"installed {installed.version}"
    return outcome


def create_app(
    settings: Settings | None = None,
    capabilities: list[Capability] | None = None,
    prediction_sink: Sink = stdout_sink,
) -> FastAPI:
    settings = settings or Settings.from_env()
    capabilities = load_installed() if capabilities is None else capabilities
    registry = ModelRegistry(settings.model_dir)
    runtime = Runtime(capabilities, registry, settings, PredictionLog(settings.service_token, prediction_sink))

    bootstrapped = bootstrap(capabilities, registry) if settings.bootstrap else {}

    if not settings.service_token:
        log.warning("ML_SERVICE_TOKEN is not set; every endpoint except /health will reject requests")

    app = FastAPI(title="FlowSight ML service", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    app.state.runtime = runtime

    @app.get("/health")
    def health() -> dict[str, Any]:
        try:
            runtime.refresh()
            registry_ok = True
        except Exception:
            log.exception("model manifest could not be read")
            registry_ok = False
        return {
            "status": "ok" if registry_ok else "degraded",
            "capabilities": [
                {
                    "name": name,
                    "enabled": settings.enabled(name),
                    "shadow_mode": settings.shadow(name),
                    "active": getattr(registry.find(name, "active"), "version", None),
                    "shadow": getattr(registry.find(name, "shadow"), "version", None),
                    **({"bootstrap": bootstrapped[name]} if name in bootstrapped else {}),
                }
                for name in runtime.capabilities
            ],
        }

    api = APIRouter(prefix="/v1", dependencies=[Depends(require_service_token)])

    @api.get("/models")
    def list_models(capability: str | None = None) -> ModelList:
        runtime.refresh()
        return ModelList(models=registry.list(capability))

    for capability in capabilities:
        api.add_api_route(
            f"/capabilities/{capability.name}/predict",
            _predict_route(runtime, capability),
            methods=["POST"],
            response_model=PredictResponse,
        )

    app.include_router(api)
    return app
