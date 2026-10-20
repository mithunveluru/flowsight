"""Run the ml-service recurring-scorer artifact over the requests the backend would send.

  predict_model.py --artifact DIR --input model-input.jsonl --out pred.jsonl

Loads ml-service/app/recurring_scorer/model.py by path (numpy only), so the eval image needs
neither the service nor its dependencies. Each output line holds the capability's result
object exactly as the backend would receive it.
"""
import argparse
import importlib.util
import json
from datetime import date
from pathlib import Path

MODEL_PY = Path(__file__).resolve().parents[2] / "ml-service" / "app" / "recurring_scorer" / "model.py"


def load_model_module(path: Path = MODEL_PY):
    spec = importlib.util.spec_from_file_location("recurring_model", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--artifact", required=True, type=Path)
    ap.add_argument("--input", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args(argv)

    model = load_model_module().RecurringModel.load(args.artifact)
    version = model.meta.get("version")
    rows = [json.loads(line) for line in args.input.read_text(encoding="utf-8").splitlines() if line.strip()]
    groups = 0
    with open(args.out, "w", encoding="utf-8") as out:
        for row in rows:
            request = row["request"]
            scores = model.score(date.fromisoformat(request["as_of"]), request["groups"])
            groups += len(scores)
            out.write(json.dumps({"user": row["user"], "model_version": version, "result": {"scores": scores}}) + "\n")
    print(f"recurring-scorer {version}: {len(rows)} users, {groups} groups -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
