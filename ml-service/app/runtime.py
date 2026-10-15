import logging
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel

from app.capability import Capability, Predictor
from app.config import Settings
from app.predictions import PredictionLog
from app.registry import ModelRegistry, ModelVersion

log = logging.getLogger(__name__)


class CapabilityDisabled(Exception):
    pass


class NoActiveModel(Exception):
    pass


class PredictionFailed(Exception):
    pass


@dataclass
class Served:
    request_id: str
    model_version: str
    result: BaseModel
    confidence: float | None
    summary: dict[str, Any]


class Runtime:
    """Serves predictions for installed capabilities from the registry's active models."""

    def __init__(
        self,
        capabilities: list[Capability],
        registry: ModelRegistry,
        settings: Settings,
        predictions: PredictionLog,
    ):
        self.capabilities = {c.name: c for c in capabilities}
        self.registry = registry
        self.settings = settings
        self.predictions = predictions
        self._loaded: dict[tuple[str, str], Predictor] = {}
        self._lock = threading.Lock()

    def refresh(self) -> None:
        if self.registry.refresh():
            live = {(m.capability, m.version) for m in self.registry.list() if m.status != "retired"}
            with self._lock:
                for key in [k for k in self._loaded if k not in live]:
                    del self._loaded[key]

    def _predictor(self, capability: Capability, model: ModelVersion) -> Predictor:
        key = (capability.name, model.version)
        with self._lock:
            if key not in self._loaded:
                self._loaded[key] = capability.load(model, self.registry.artifact_dir(model))
            return self._loaded[key]

    def _run(self, capability: Capability, model: ModelVersion, payload: BaseModel) -> tuple[BaseModel, float]:
        start = time.perf_counter()
        result = self._predictor(capability, model)(payload)
        if not isinstance(result, capability.output_model):
            result = capability.output_model.model_validate(result)
        return result, (time.perf_counter() - start) * 1000

    def predict(self, name: str, payload: BaseModel) -> tuple[Served, ModelVersion | None]:
        """Run the active model. Returns the served prediction and the shadow model to run
        afterwards, if shadow mode is on and a shadow version is registered."""
        capability = self.capabilities[name]
        if not self.settings.enabled(name):
            raise CapabilityDisabled(name)
        self.refresh()
        active = self.registry.find(name, "active")
        if active is None:
            raise NoActiveModel(name)

        request_id = uuid.uuid4().hex
        common = dict(
            request_id=request_id,
            capability=name,
            input_hash=self.predictions.input_hash(payload),
            input_summary=capability.input_summary(payload),
        )
        start = time.perf_counter()
        try:
            result, latency = self._run(capability, active, payload)
        except Exception as exc:
            log.exception("%s %s prediction failed", name, active.version)
            self.predictions.record(
                **common,
                model_version=active.version,
                role="active",
                latency_ms=(time.perf_counter() - start) * 1000,
                error=type(exc).__name__,
            )
            raise PredictionFailed(name) from exc

        summary = capability.output_summary(result)
        self.predictions.record(
            **common, model_version=active.version, role="active", latency_ms=latency, output_summary=summary
        )
        shadow = self.registry.find(name, "shadow") if self.settings.shadow(name) else None
        served = Served(request_id, active.version, result, capability.confidence(result), summary)
        return served, shadow

    def run_shadow(self, name: str, model: ModelVersion, payload: BaseModel, request_id: str) -> None:
        capability = self.capabilities[name]
        common = dict(
            request_id=request_id,
            capability=name,
            model_version=model.version,
            role="shadow",
            input_hash=self.predictions.input_hash(payload),
            input_summary=capability.input_summary(payload),
        )
        start = time.perf_counter()
        try:
            result, latency = self._run(capability, model, payload)
        except Exception as exc:
            log.warning("%s shadow %s failed: %s", name, model.version, type(exc).__name__)
            self.predictions.record(
                **common, latency_ms=(time.perf_counter() - start) * 1000, error=type(exc).__name__
            )
            return
        self.predictions.record(**common, latency_ms=latency, output_summary=capability.output_summary(result))
