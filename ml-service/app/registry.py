"""Versioned model registry backed by a JSON manifest under the model directory.

Artifacts live at <model_dir>/<capability>/<version>/. Per capability there is at
most one active and at most one shadow version; promoting a version demotes the
previous holder of that status to retired.
"""

import json
import os
import tempfile
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, Field

Status = Literal["active", "shadow", "retired"]
NAME_PATTERN = r"^[a-z0-9][a-z0-9-]{0,49}$"
VERSION_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"
MANIFEST = "manifest.json"


class ModelVersion(BaseModel):
    capability: str = Field(pattern=NAME_PATTERN)
    version: str = Field(pattern=VERSION_PATTERN)
    status: Status
    created_at: datetime
    training_data: dict[str, Any] = Field(default_factory=dict)
    metrics: dict[str, float] = Field(default_factory=dict)


class Manifest(BaseModel):
    models: list[ModelVersion] = Field(default_factory=list)


class RegistryError(ValueError):
    pass


class ModelRegistry:
    def __init__(self, model_dir: Path):
        self.model_dir = Path(model_dir)
        self._lock = threading.Lock()
        self._manifest = Manifest()
        self._stamp: tuple[int, int] | None = None
        self.refresh()

    @property
    def manifest_path(self) -> Path:
        return self.model_dir / MANIFEST

    def artifact_dir(self, model: ModelVersion) -> Path:
        return self.model_dir / model.capability / model.version

    def refresh(self) -> bool:
        """Reload the manifest if it changed on disk (e.g. via the CLI). Returns True on reload."""
        stamp = self._read_stamp()
        with self._lock:
            if stamp == self._stamp:
                return False
            self._manifest = (
                Manifest.model_validate_json(self.manifest_path.read_text())
                if stamp is not None
                else Manifest()
            )
            self._stamp = stamp
            return True

    def _read_stamp(self) -> tuple[int, int] | None:
        # Writes go through os.replace, so a new inode marks a change even when
        # two writes land within the filesystem's timestamp resolution.
        try:
            st = self.manifest_path.stat()
        except FileNotFoundError:
            return None
        return st.st_ino, st.st_mtime_ns

    def list(self, capability: str | None = None) -> list[ModelVersion]:
        with self._lock:
            return [m for m in self._manifest.models if capability in (None, m.capability)]

    def find(self, capability: str, status: Status) -> ModelVersion | None:
        return next((m for m in self.list(capability) if m.status == status), None)

    def register(
        self,
        capability: str,
        version: str,
        training_data: dict[str, Any] | None = None,
        metrics: dict[str, float] | None = None,
        status: Status = "retired",
    ) -> ModelVersion:
        model = ModelVersion(
            capability=capability,
            version=version,
            status="retired",
            created_at=datetime.now(timezone.utc),
            training_data=training_data or {},
            metrics=metrics or {},
        )
        if not self.artifact_dir(model).is_dir():
            raise RegistryError(f"artifact directory missing: {self.artifact_dir(model)}")
        with self._lock:
            if any(m.capability == capability and m.version == version for m in self._manifest.models):
                raise RegistryError(f"{capability} {version} is already registered")
            self._manifest.models.append(model)
            self._save()
        return self.set_status(capability, version, status) if status != "retired" else model

    def set_status(self, capability: str, version: str, status: Status) -> ModelVersion:
        with self._lock:
            target = next(
                (m for m in self._manifest.models if m.capability == capability and m.version == version),
                None,
            )
            if target is None:
                raise RegistryError(f"{capability} {version} is not registered")
            if status != "retired":
                for m in self._manifest.models:
                    if m.capability == capability and m.status == status and m is not target:
                        m.status = "retired"
            target.status = status
            self._save()
            return target.model_copy()

    def _save(self) -> None:
        self.model_dir.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.model_dir, prefix=".manifest-", suffix=".json")
        with os.fdopen(fd, "w") as fh:
            fh.write(self._manifest.model_dump_json(indent=2))
        os.replace(tmp, self.manifest_path)
        self._stamp = self._read_stamp()


def load_json_arg(raw: str | None) -> dict[str, Any]:
    if not raw:
        return {}
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise RegistryError("expected a JSON object")
    return value
