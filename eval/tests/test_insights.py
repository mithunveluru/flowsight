import json
import subprocess
import sys
from pathlib import Path

import score

HERE = Path(__file__).resolve().parent.parent


def test_validator_rates_split_false_accepts_from_false_rejects():
    cases = [{"id": "a", "variant": "g_exact", "expected": "accept"},
             {"id": "b", "variant": "g_exact", "expected": "accept"},
             {"id": "c", "variant": "r_sum", "expected": "reject"},
             {"id": "d", "variant": "r_sum", "expected": "reject"}]
    preds = {"a": {"grounded": True}, "b": {"grounded": False}, "c": {"grounded": True}}
    metrics, slices = score.score_validator(cases, preds)
    assert metrics == {"accuracy": 0.5, "false_accept_rate": 0.5, "false_reject_rate": 0.5}
    assert slices["variant=r_sum"] == {"n": 2, "accuracy": 0.5}


def test_intent_scores_refusals_and_slots():
    cases = [{"id": "1", "intent": "LEAKS", "period": "THIS_MONTH", "category": ""},
             {"id": "2", "intent": "WRITE_REQUEST", "period": "THIS_MONTH", "category": ""},
             {"id": "3", "intent": "SPENDING_BY_CATEGORY", "period": "LAST_MONTH", "category": "GROCERIES"}]
    preds = {"1": {"intent": "LEAKS", "period": "THIS_MONTH", "category": ""},
             "2": {"intent": "UNSUPPORTED", "period": "THIS_MONTH", "category": ""},
             "3": {"intent": "SPENDING_BY_CATEGORY", "period": "THIS_MONTH", "category": "GROCERIES"}}
    metrics, _ = score.score_intents(cases, preds)
    assert metrics["intent_accuracy"] == round(2 / 3, 4)
    assert metrics["period_accuracy"] == round(2 / 3, 4)
    assert metrics["category_accuracy"] == 1.0
    assert metrics["refusal_recall"] == 1.0
    assert metrics["answerable_recall"] == 1.0


def test_generator_is_deterministic_and_balanced(tmp_path):
    out = tmp_path / "cases.jsonl"
    subprocess.run([sys.executable, str(HERE / "insights" / "generate.py"), "--out", str(out)], check=True)
    rows = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines()]
    assert out.read_bytes() == (HERE / "insights" / "validator_cases.jsonl").read_bytes()
    assert {r["expected"] for r in rows} == {"accept", "reject"}
    assert sum(r["expected"] == "reject" for r in rows) >= 60
