import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Mapping

_TRUE = {"1", "true", "yes", "on"}


def _flag(env: Mapping[str, str], key: str) -> bool:
    return env.get(key, "").strip().lower() in _TRUE


def env_prefix(capability: str) -> str:
    return "ML_" + capability.upper().replace("-", "_")


@dataclass(frozen=True)
class Settings:
    model_dir: Path
    service_token: str
    env: Mapping[str, str] = field(default_factory=dict)

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> "Settings":
        env = dict(os.environ if env is None else env)
        return cls(
            model_dir=Path(env.get("ML_MODEL_DIR", "./models")),
            service_token=env.get("ML_SERVICE_TOKEN", ""),
            env=env,
        )

    @property
    def bootstrap(self) -> bool:
        return self.env.get("ML_BOOTSTRAP", "true").strip().lower() in _TRUE

    def enabled(self, capability: str) -> bool:
        return _flag(self.env, env_prefix(capability) + "_ENABLED")

    def shadow(self, capability: str) -> bool:
        return _flag(self.env, env_prefix(capability) + "_SHADOW")
