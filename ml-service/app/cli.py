"""Model registry administration.

    python -m app.cli list [capability]
    python -m app.cli register <capability> <version> [--metrics JSON] [--training-data JSON] [--status S]
    python -m app.cli activate|shadow|retire <capability> <version>

Artifacts must already be in $ML_MODEL_DIR/<capability>/<version>/. A running service
picks up manifest changes on its next request.
"""

import argparse
import sys

from app.config import Settings
from app.registry import ModelRegistry, RegistryError, load_json_arg

STATUS_COMMANDS = {"activate": "active", "shadow": "shadow", "retire": "retired"}


def main(argv: list[str] | None = None, settings: Settings | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.cli")
    sub = parser.add_subparsers(dest="command", required=True)

    listing = sub.add_parser("list")
    listing.add_argument("capability", nargs="?")

    register = sub.add_parser("register")
    register.add_argument("capability")
    register.add_argument("version")
    register.add_argument("--metrics")
    register.add_argument("--training-data")
    register.add_argument("--status", choices=["active", "shadow", "retired"], default="retired")

    for command in STATUS_COMMANDS:
        p = sub.add_parser(command)
        p.add_argument("capability")
        p.add_argument("version")

    args = parser.parse_args(argv)
    registry = ModelRegistry((settings or Settings.from_env()).model_dir)

    try:
        if args.command == "list":
            for m in registry.list(args.capability):
                print(f"{m.capability}\t{m.version}\t{m.status}\t{m.created_at.isoformat()}\t{m.metrics}")
        elif args.command == "register":
            m = registry.register(
                args.capability,
                args.version,
                training_data=load_json_arg(args.training_data),
                metrics=load_json_arg(args.metrics),
                status=args.status,
            )
            print(f"registered {m.capability} {m.version} as {m.status}")
        else:
            m = registry.set_status(args.capability, args.version, STATUS_COMMANDS[args.command])
            print(f"{m.capability} {m.version} is now {m.status}")
    except (RegistryError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
