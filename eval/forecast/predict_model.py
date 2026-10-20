"""Forecasts of the ml-service forecaster model (loaded by path, standard library only) at the
backtest origins, in the same JSONL shape ForecastEvalTest writes.

  python3 forecast/predict_model.py --model ../ml-service/app/forecaster/model.py \\
      --priors ../ml-service/app/forecaster/artifact/priors.json \\
      --histories out/forecast/histories.jsonl --out out/pred-forecast-ml.jsonl
"""
import argparse
import importlib.util
import json
from datetime import date

HORIZON = 3


def load_model(path):
    spec = importlib.util.spec_from_file_location("forecaster_model", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def rows(model, priors, user):
    tx = [(date.fromisoformat(d), typ, cat, float(a)) for d, typ, cat, a in user["tx"]]
    for origin in user["origins"]:
        today = date.fromisoformat(origin)
        h = model.history_from_transactions(tx, today)
        for s in model.forecast_history(h, HORIZON + 1, priors):
            series = "cat:" + s["name"] if s["kind"] == "category" else s["name"]
            for step in s["steps"][1:]:
                y, m = divmod(today.year * 12 + today.month - 1 + step["k"] - 1, 12)
                yield {"user": user["user"], "origin": origin, "series": series, "target": f"{y}-{m + 1:02d}",
                       "h": step["k"] - 1, "n": s["data_months"], "point": round(step["point"], 2),
                       "method": s["method"], "lo80": round(step["lower80"], 2), "hi80": round(step["upper80"], 2),
                       "lo95": round(step["lower95"], 2), "hi95": round(step["upper95"], 2)}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--priors", required=True)
    ap.add_argument("--histories", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    model = load_model(args.model)
    priors = model.Priors.load(args.priors)
    with open(args.histories, encoding="utf-8") as f, open(args.out, "w", encoding="utf-8") as out:
        for line in f:
            if line.strip():
                for r in rows(model, priors, json.loads(line)):
                    out.write(json.dumps(r) + "\n")


if __name__ == "__main__":
    main()
