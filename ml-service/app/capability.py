"""Plug-in contract for ML capabilities.

A capability lives in its own package under app/<capability>/, exposes a module-level
CAPABILITY instance, and is enabled by adding its module path to app.capabilities.INSTALLED.
The core turns it into POST /v1/capabilities/<name>/predict with its typed request
and result models, handles auth, flags, shadow runs and the prediction log.
"""

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, Callable, ClassVar

from pydantic import BaseModel

from app.registry import ModelRegistry, ModelVersion

Predictor = Callable[[BaseModel], BaseModel]


class Capability(ABC):
    name: ClassVar[str]
    input_model: ClassVar[type[BaseModel]]
    output_model: ClassVar[type[BaseModel]]

    @abstractmethod
    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        """Load one registered version's artifacts and return a predictor for it."""

    def input_summary(self, payload: BaseModel) -> dict[str, Any]:
        """Non-identifying facts about the input for the prediction log (sizes, counts)."""
        return {}

    def output_summary(self, result: BaseModel) -> dict[str, Any]:
        """Minimal description of the result for the prediction log; never raw user text."""
        return {}

    def confidence(self, result: BaseModel) -> float | None:
        return None

    def bootstrap(self, registry: ModelRegistry) -> ModelVersion | None:
        """Install the baseline that ships in the image, for a registry that has never seen this
        capability. Returns the registered version, or None when there is nothing to install."""
        return None
