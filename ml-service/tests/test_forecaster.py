from datetime import date
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.forecaster import CAPABILITY, calibrate, model
from app.forecaster.install import BUNDLED, install
from app.main import create_app
from tests.conftest import AUTH, TOKEN

URL = "/v1/capabilities/forecaster/predict"
PRIORS = model.Priors.load(BUNDLED / model.PRIORS)


@pytest.fixture
def client(tmp_path: Path) -> tuple[TestClient, list[dict]]:
    model_dir = tmp_path / "models"
    install(model_dir, "active")
    records: list[dict] = []
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_FORECASTER_ENABLED": "true"})
    return TestClient(create_app(settings, capabilities=[CAPABILITY], prediction_sink=records.append)), records


def parity_history() -> model.History:
    txns = []
    for i in range(14):
        y, m = divmod(2025 * 12 + 7 + i, 12)
        d = date(y, m + 1, 1)
        txns.append((d, "CREDIT", "INCOME", 60000.0 + (2000.0 if i % 5 == 0 else 0.0)))
        txns.append((d.replace(day=3), "DEBIT", "GROCERIES", 8000.0 + 150.0 * i))
        txns.append((d.replace(day=20), "DEBIT", "SHOPPING", 30000.0 if i % 12 == 3 else 5000.0 + 37.0 * i))
    txns.append((date(2026, 10, 2), "CREDIT", "INCOME", 61000.0))
    txns.append((date(2026, 10, 4), "DEBIT", "GROCERIES", 3000.0))
    return model.history_from_transactions(txns, date(2026, 10, 9))


def test_matches_the_backend_forecaster_on_the_shared_parity_case():
    # the same numbers are asserted in the backend's ForecastParityTest
    rows = {r["name"]: r for r in model.forecast_history(parity_history(), 4, PRIORS)}
    assert [rows[n]["method"] for n in ("income", "spend", "net", "GROCERIES", "SHOPPING")] == [
        "recent-median", "damped-trend", "income-minus-spend", "damped-trend", "damped-trend"]
    spend = rows["spend"]["steps"][0]
    assert spend["point"] == pytest.approx(15580.6, abs=1e-6)
    assert spend["lower80"] == pytest.approx(14474.463040607683, abs=1e-6)
    assert spend["upper95"] == pytest.approx(26086.987985319407, abs=1e-6)
    assert rows["net"]["steps"][1]["point"] == pytest.approx(44299.72, abs=1e-6)
    assert rows["GROCERIES"]["interval_ratio"] == pytest.approx(0.723849099681766, abs=1e-9)


def test_history_counts_a_first_month_only_when_it_starts_in_the_opening_week():
    txns = [(date(2026, 8, 12), "DEBIT", "SHOPPING", 700.0), (date(2026, 9, 2), "DEBIT", "SHOPPING", 50.0)]
    h = model.history_from_transactions(txns, date(2026, 9, 3))
    assert h.months == [] and h.spend_to_date == 50.0
    h = model.history_from_transactions(txns, date(2026, 9, 3), earliest=date(2026, 8, 5))
    assert len(h.months) == 1 and h.spend == [700.0] and h.categories["SHOPPING"] == [700.0]


def test_short_and_empty_histories_still_get_a_point_and_a_range():
    none = model.forecast_series("spend", "spend", [], 12000.0, 0.0, 4, 900.0, PRIORS)
    assert none["method"] == "month-to-date" and none["steps"][1]["point"] == 12000.0
    assert none["backtest_mae"] is None and none["steps"][1]["upper95"] > 12000.0
    short = model.forecast_series("spend", "spend", [10000.0, 10000.0], 6000.0, 10000.0, 4, 900.0, PRIORS)
    assert short["method"] == "recent-mean+month-to-date"
    assert short["steps"][0]["point"] == pytest.approx((20000 + 0.5 * 12000) / 2.5)


def test_backtest_selects_the_seasonal_method_for_a_yearly_spike():
    y = [30000.0 if i % 12 == 10 else 10000.0 for i in range(24)]
    assert model.select(y, 4) == "seasonal-deviation"
    assert model.predict("seasonal-deviation", y, 24, 11) > 20000


def test_serves_forecasts_for_every_series(client):
    http, records = client
    body = {"steps": 4, "day_fraction": 0.3, "series": [
        {"name": "income", "kind": "income", "values": [60000.0] * 8, "month_to_date": 60000.0, "same_days": 480000.0},
        {"name": "spend", "kind": "spend", "values": [40000.0 + 500 * i for i in range(8)], "month_to_date": 9000.0,
         "same_days": 90000.0},
        {"name": "GROCERIES", "kind": "category", "values": [9000.0] * 8, "month_to_date": 2500.0, "same_days": 20000.0},
    ]}
    response = http.post(URL, json=body, headers=AUTH)
    assert response.status_code == 200
    result = response.json()["result"]
    assert result["priors_version"] == PRIORS.version
    assert [s["name"] for s in result["series"]] == ["income", "spend", "net", "GROCERIES"]
    for s in result["series"]:
        assert [st["k"] for st in s["steps"]] == [1, 2, 3, 4]
        for st in s["steps"]:
            assert st["lower95"] <= st["lower80"] <= st["point"] <= st["upper80"] <= st["upper95"]
    assert records[0]["input_summary"] == {"series": 3, "months": 8, "steps": 4}


@pytest.mark.parametrize("series", [
    [{"name": "spend", "kind": "spend", "values": [1.0], "month_to_date": 0, "same_days": 0},
     {"name": "income", "kind": "income", "values": [1.0], "month_to_date": 0, "same_days": 0}],
    [{"name": "income", "kind": "income", "values": [1.0, 2.0], "month_to_date": 0, "same_days": 0},
     {"name": "spend", "kind": "spend", "values": [1.0], "month_to_date": 0, "same_days": 0}],
    [{"name": "income", "kind": "income", "values": [], "month_to_date": -5, "same_days": 0},
     {"name": "spend", "kind": "spend", "values": [], "month_to_date": 0, "same_days": 0}],
])
def test_rejects_requests_out_of_shape(client, series):
    http, _ = client
    assert http.post(URL, json={"steps": 2, "day_fraction": 0.5, "series": series}, headers=AUTH).status_code == 422


def test_calibration_errors_are_keyed_by_kind_history_and_step():
    history = {"user": "u", "origins": ["2026-03-10"], "tx": [
        ["2026-01-02", "CREDIT", "INCOME", 1000], ["2026-01-05", "DEBIT", "GROCERIES", 400],
        ["2026-02-02", "CREDIT", "INCOME", 1000], ["2026-02-05", "DEBIT", "GROCERIES", 500],
        ["2026-03-02", "CREDIT", "INCOME", 1000], ["2026-04-02", "CREDIT", "INCOME", 1000]]}
    errs = calibrate.errors([history])
    keys = {k for k, _ in errs}
    assert {"income|2|1", "spend|2|4", "net|2|2", "category|2|3"} <= keys
    assert len(errs) == 4 * 4
    with pytest.raises(SystemExit):
        calibrate.grouped(errs)
    assert calibrate.quantile([0.0, 1.0, 2.0, 3.0], 0.5) == 1.5


def test_fresh_volume_serves_the_bundled_priors(tmp_path: Path):
    settings = Settings.from_env({"ML_MODEL_DIR": str(tmp_path), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_FORECASTER_ENABLED": "true"})
    http = TestClient(create_app(settings, capabilities=[CAPABILITY]))
    caps = {c["name"]: c for c in http.get("/health").json()["capabilities"]}
    assert caps["forecaster"]["active"] and caps["forecaster"]["bootstrap"].startswith("installed")
