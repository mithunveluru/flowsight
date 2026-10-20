"""Copy the forecaster priors that ship in the image into the model registry.

  python -m app.forecaster.install                  # registers as shadow
  python -m app.forecaster.install --status active

Copies app/forecaster/artifact to $ML_MODEL_DIR/forecaster/<version>/ (version from meta.json).
Running it again for the same version only changes the status.
"""

import argparse
import filecmp
import json
import shutil
import sys
from pathlib import Path

from app.config import Settings
from app.forecaster.model import PRIORS
from app.registry import ModelRegistry, ModelVersion, RegistryError

BUNDLED = Path(__file__).resolve().parent / "artifact"
CAPABILITY = "forecaster"
META = "meta.json"
FILES = (PRIORS, META)


def install(model_dir: Path, status: str = "shadow", source: Path = BUNDLED,
            registry: ModelRegistry | None = None) -> ModelVersion:
    meta = json.loads((source / META).read_text(encoding="utf-8"))
    version = meta["version"]
    target = Path(model_dir) / CAPABILITY / version
    if target.exists():
        if not all(filecmp.cmp(source / f, target / f, shallow=False) for f in FILES):
            raise RegistryError(f"{target} already holds different files for version {version}")
    else:
        target.mkdir(parents=True)
        for name in FILES:
            shutil.copyfile(source / name, target / name)

    registry = registry or ModelRegistry(model_dir)
    if any(m.version == version for m in registry.list(CAPABILITY)):
        return registry.set_status(CAPABILITY, version, status)
    return registry.register(CAPABILITY, version, training_data=meta["training_data"], status=status)


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
