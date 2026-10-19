"""Run the ml-service categorizer artifact over the items the backend would send it.

  predict_model.py --artifact DIR --input model-input.jsonl --out pred.jsonl

Loads ml-service/app/categorizer/model.py by path (numpy only), so the eval image needs
neither the service nor scikit-learn.
"""
import argparse
import importlib.util
import json
from pathlib import Path

MODEL_PY = Path(__file__).resolve().parents[2] / "ml-service" / "app" / "categorizer" / "model.py"


def load_model_module(path: Path = MODEL_PY):
    spec = importlib.util.spec_from_file_location("categorizer_model", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--artifact", required=True, type=Path)
    ap.add_argument("--input", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args(argv)

    model = load_model_module().CategoryModel.load(args.artifact)
    rows = [json.loads(line) for line in args.input.read_text(encoding="utf-8").splitlines() if line.strip()]
    predictions = model.predict([r["item"] for r in rows])
    with open(args.out, "w", encoding="utf-8") as out:
        for row, p in zip(rows, predictions):
            out.write(json.dumps({"id": row["id"], "category": p["category"],
                                  "confidence": round(p["probability"], 6),
                                  "alternatives": p["alternatives"],
                                  "model_version": model.meta.get("version")}) + "\n")
    print(f"categorizer {model.meta.get('version')}: {len(rows)} rows -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
