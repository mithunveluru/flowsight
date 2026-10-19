import json
from datetime import date

import pytest

import score
from forecast import generate


def test_histories_are_seeded_and_origins_leave_three_complete_months():
    a, b = generate.build(12, 3), generate.build(12, 3)
    assert a == b
    assert a != generate.build(12, 4)
    for user in a:
        end = date.fromisoformat(user["end"])
        for o in user["origins"]:
            today = date.fromisoformat(o)
            assert date.fromisoformat(user["start"]) <= today <= end
            months_after = (end.year - today.year) * 12 + end.month - today.month
            assert months_after >= 3


HISTORY = {"user": "u1", "tags": ["salaried", "seasonal"], "start": "2024-01-01", "end": "2024-06-30",
           "origins": ["2024-02-10"],
           "tx": [["2024-03-01", "CREDIT", "INCOME", 1000.0], ["2024-03-05", "DEBIT", "GROCERIES", 300.0],
                  ["2024-03-20", "DEBIT", "TRAVEL", 100.0]]}


def pred(series, point, **extra):
    return {"user": "u1", "origin": "2024-02-10", "series": series, "target": "2024-03", "h": 1, "n": 1,
            "point": point, "method": "m", **extra}


def test_forecast_rows_join_monthly_actuals():
    rows = score.forecast_rows([HISTORY], [pred("spend", 500.0), pred("net", 0.0), pred("cat:TRAVEL", 50.0)])
    assert [r["actual"] for r in rows] == [400.0, 600.0, 100.0]
    assert rows[0]["income"] == 1000.0 and rows[0]["tags"] == ["salaried", "seasonal"]


def test_forecast_metrics_and_interval_coverage():
    rows = score.forecast_rows([HISTORY], [
        pred("income", 800.0, lo80=900.0, hi80=1100.0, lo95=700.0, hi95=1300.0),
        pred("spend", 500.0, lo80=420.0, hi80=900.0, lo95=0.0, hi95=900.0),
        pred("net", 300.0),
    ])
    m = score.forecast_metrics(rows)
    assert m["wape"] == pytest.approx((200 + 100) / 1400, abs=1e-4)
    assert m["mape"] == pytest.approx((0.2 + 0.25) / 2, abs=1e-4)
    assert m["net_smae"] == pytest.approx(300 / 1400, abs=1e-4)
    assert (m["cov80"], m["cov95"]) == (0.5, 1.0)
    assert (m["cov80_gap"], m["cov95_gap"]) == (0.3, 0.05)


def test_forecast_metrics_without_intervals_report_no_coverage():
    m = score.forecast_metrics(score.forecast_rows([HISTORY], [pred("spend", 400.0)]))
    assert m["wape"] == 0.0 and m["cov80"] is None and m["cov80_gap"] is None


def test_forecast_suite_writes_and_checks_a_baseline(tmp_path):
    histories = tmp_path / "h.jsonl"
    histories.write_text(json.dumps(HISTORY) + "\n")
    p = tmp_path / "pred.jsonl"
    p.write_text(json.dumps(pred("spend", 500.0)) + "\n")
    args = ["forecast", "--suite", "forecast-current", "--histories", str(histories), "--pred", str(p),
            "--baseline", str(tmp_path / "b.json")]
    assert score.main(args + ["--update-baseline"]) == 0
    assert score.main(args) == 0
    p.write_text(json.dumps(pred("spend", 900.0)) + "\n")
    assert score.main(args) == 1
