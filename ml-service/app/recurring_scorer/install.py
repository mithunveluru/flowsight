"""Copy the recurring-scorer artifact that ships in the image into the model registry.

  python -m app.recurring_scorer.install                 # registers as shadow
  python -m app.recurring_scorer.install --status active

Copies app/recurring_scorer/artifact to $ML_MODEL_DIR/recurring-scorer/<version>/ (version
from meta.json) and registers it with its cross-validation metrics. Running it again for the same
version only changes the status. Promote or retire later with `python -m app.cli`.
"""

import argparse
import filecmp
import json
import shutil
import sys
from pathlib import Path

from app.recurring_scorer.model import META, WEIGHTS
from app.config import Settings
from app.registry import ModelRegistry, ModelVersion, RegistryError

BUNDLED = Path(__file__).resolve().parent / "artifact"
CAPABILITY = "recurring-scorer"


def registry_metrics(meta: dict) -> dict[str, float]:
    return {f"cv_{k}": float(v) for k, v in meta["validation"]["chosen"].items()}


def install(model_dir: Path, status: str = "shadow", source: Path = BUNDLED,
            registry: ModelRegistry | None = None) -> ModelVersion:
    meta = json.loads((source / META).read_text(encoding="utf-8"))
    version = meta["version"]
    target = Path(model_dir) / CAPABILITY / version
    if target.exists():
        if not all(filecmp.cmp(source / f, target / f, shallow=False) for f in (WEIGHTS, META)):
            raise RegistryError(f"{target} already holds different files for version {version}")
    else:
        target.mkdir(parents=True)
        for name in (WEIGHTS, META):
            shutil.copyfile(source / name, target / name)

    registry = registry or ModelRegistry(model_dir)
    if any(m.version == version for m in registry.list(CAPABILITY)):
        return registry.set_status(CAPABILITY, version, status)
    return registry.register(CAPABILITY, version, training_data=meta["training_data"],
                             metrics=registry_metrics(meta), status=status)


def main(argv: list[str] | None = None, settings: Settings | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--status", choices=["active", "shadow", "retired"], default="shadow")
    args = ap.parse_args(argv)
    try:
        m = install((settings or Settings.from_env()).model_dir, args.status)
    except (RegistryError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"{m.capability} {m.version} is {m.status}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
