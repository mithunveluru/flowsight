import hashlib
import json
import random
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.capabilities import load_installed
from app.config import Settings
from app.main import create_app
from app.recurring_scorer import CAPABILITY, corpus
from app.recurring_scorer.install import BUNDLED, install
from app.recurring_scorer.model import META, WEIGHTS, RecurringModel, Series, analyze, snap, split_parallel
from app.recurring_scorer.train import fit_logreg, train
from app.registry import ModelRegistry, RegistryError
from tests.conftest import AUTH, TOKEN

URL = "/v1/capabilities/recurring-scorer/predict"
AS_OF = date(2026, 6, 30)


def monthly(day: int, months: int, amount: float = 499.0) -> tuple[list[int], list[float]]:
    days = []
    for k in range(months - 1, -1, -1):
        y, m = divmod(AS_OF.month - 1 - k, 12)
        days.append((date(AS_OF.year + y, m + 1, day) - AS_OF).days)
    return days, [amount] * len(days)


def delivery(seed: int, weeks: int = 20) -> tuple[list[int], list[float]]:
    rng = random.Random(seed)
    days = sorted(-rng.randrange(weeks * 7) for _ in range(weeks * 2))
    return days, [round(450 * np.exp(rng.gauss(0, 0.35)), 2) for _ in days]


@pytest.fixture(scope="module")
def model() -> RecurringModel:
    return RecurringModel.load(BUNDLED)


@pytest.fixture
def client(tmp_path: Path) -> TestClient:
    model_dir = tmp_path / "models"
    install(model_dir, "active")
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN,
                                  "ML_RECURRING_SCORER_ENABLED": "true"})
    return TestClient(create_app(settings, capabilities=[CAPABILITY], prediction_sink=[].append))


def test_snap_names_known_periods_and_rejects_others():
    assert snap(30)[0] == "MONTHLY" and snap(7)[0] == "WEEKLY" and snap(364)[0] == "ANNUAL"
    assert snap(55)[0] is None
    assert snap(0)[0] is None


def test_corpus_is_seeded_and_labeled_both_ways():
    a, b = corpus.build(3, rows=200), corpus.build(3, rows=200)
    assert a == b and a != corpus.build(4, rows=200)
    assert {r["label"] for r in a} == {0, 1}
    assert all(len(r["days"]) >= 2 and max(r["days"]) <= 0 for r in a)


def test_logreg_fits_a_separable_rule():
    rng = np.random.default_rng(0)
    x = rng.normal(size=(400, 2))
    y = (x[:, 0] > 0).astype(float)
    coef, _ = fit_logreg(x, y, 1.0)
    assert coef[0] > 2 * abs(coef[1])


def test_training_is_byte_reproducible(tmp_path):
    rows = corpus.build(5, rows=600)
    a = train(tmp_path / "a", rows=rows, l2_grid=(1.0,))
    b = train(tmp_path / "b", rows=rows, l2_grid=(1.0,))
    assert a == b
    for name in (WEIGHTS, META):
        assert hashlib.sha256((tmp_path / "a" / name).read_bytes()).digest() == \
            hashlib.sha256((tmp_path / "b" / name).read_bytes()).digest()


def test_bundled_artifact_comes_from_the_committed_corpus(model):
    meta = json.loads((BUNDLED / META).read_text())
    rows = corpus.build(meta["training_data"]["seed"])
    assert hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest() == meta["training_data"]["sha256"]
    assert model.meta["version"] == meta["version"]


def test_monthly_subscription_is_recurring_with_a_next_date_interval(model):
    days, amounts = monthly(5, 8)
    [s] = model.score(AS_OF, [{"days": days, "amounts": amounts, "category": "ENTERTAINMENT"}])
    assert s["recurring"] and s["period"] == "MONTHLY" and s["probability"] > 0.9
    assert s["next_in_days"] == (date(2026, 7, 5) - AS_OF).days
    assert s["next_low_in_days"] < s["next_in_days"] < s["next_high_in_days"]
    assert s["charges_per_period"] == 1 and s["amount"] == 499.0


def test_metered_bill_is_recurring(model):
    days, _ = monthly(12, 10)
    amounts = [1900 * f for f in (1.1, 0.8, 1.3, 0.9, 1.2, 1.0, 0.7, 1.25, 0.95, 1.15)]
    [s] = model.score(AS_OF, [{"days": days, "amounts": amounts, "category": "UTILITIES"}])
    assert s["recurring"] and s["period"] == "MONTHLY"


@pytest.mark.parametrize("seed", range(5))
def test_food_delivery_is_not_a_subscription(model, seed):
    days, amounts = delivery(seed)
    [s] = model.score(AS_OF, [{"days": days, "amounts": amounts, "category": "FOOD_DINING"}])
    assert not s["recurring"] and s["probability"] < 0.2


def test_weekly_shop_with_a_variable_basket_is_not_recurring(model):
    rng = random.Random(1)
    days = [-7 * k + rng.choice([0, 0, 1, -1]) for k in range(20, 0, -1)]
    amounts = [round(1800 * np.exp(rng.gauss(0, 0.4)), 2) for _ in days]
    [s] = model.score(AS_OF, [{"days": days, "amounts": amounts, "category": "GROCERIES"}])
    assert not s["recurring"]


def test_one_service_on_two_accounts_is_split_into_two_monthly_series(model):
    a, _ = monthly(7, 6)
    b, _ = monthly(20, 6)
    days = sorted(a + b)
    parts = split_parallel(Series(AS_OF, days, [649.0] * len(days)))
    assert parts is not None and all(p.period == "MONTHLY" for p in parts)
    [s] = model.score(AS_OF, [{"days": days, "amounts": [649.0] * len(days), "category": "ENTERTAINMENT"}])
    assert s["recurring"] and s["period"] == "MONTHLY" and s["charges_per_period"] == 2


def test_a_steady_fourteen_day_cycle_is_not_split():
    days = [-14 * k for k in range(12, 0, -1)]
    assert split_parallel(Series(AS_OF, days, [1500.0] * len(days))) is None


def test_features_do_not_depend_on_input_order():
    days, amounts = monthly(3, 6)
    a = analyze(AS_OF, days, amounts, "SUBSCRIPTIONS")["x"]
    b = analyze(AS_OF, days[::-1], amounts[::-1], "SUBSCRIPTIONS")["x"]
    np.testing.assert_allclose(a, b)


def test_api_scores_each_group_in_order(client):
    sub_days, sub_amounts = monthly(5, 6)
    food_days, food_amounts = delivery(9)
    body = {"as_of": AS_OF.isoformat(), "groups": [
        {"days": sub_days, "amounts": sub_amounts, "category": "SUBSCRIPTIONS"},
        {"days": food_days, "amounts": food_amounts, "category": "FOOD_DINING"}]}
    r = client.post(URL, json=body, headers=AUTH)
    assert r.status_code == 200
    data = r.json()
    assert data["model_version"] == json.loads((BUNDLED / META).read_text())["version"]
    assert [s["recurring"] for s in data["result"]["scores"]] == [True, False]
    assert data["summary"] == {"groups": 2, "recurring": 1, "parallel": 0}


@pytest.mark.parametrize("group", [
    {"days": [-30], "amounts": [10.0]},
    {"days": [-30, -1], "amounts": [10.0]},
    {"days": [-30, 5], "amounts": [10.0, 10.0]},
    {"days": [-30, -1], "amounts": [10.0, -3.0]},
])
def test_api_validates_groups(client, group):
    r = client.post(URL, json={"as_of": AS_OF.isoformat(), "groups": [group]}, headers=AUTH)
    assert r.status_code == 422


def test_api_is_off_without_the_flag(tmp_path):
    model_dir = tmp_path / "models"
    install(model_dir, "active")
    settings = Settings.from_env({"ML_MODEL_DIR": str(model_dir), "ML_SERVICE_TOKEN": TOKEN})
    client = TestClient(create_app(settings, capabilities=[CAPABILITY], prediction_sink=[].append))
    days, amounts = monthly(5, 3)
    r = client.post(URL, json={"as_of": AS_OF.isoformat(), "groups": [{"days": days, "amounts": amounts}]},
                    headers=AUTH)
    assert r.status_code == 404


def test_install_registers_once_and_refuses_foreign_files(tmp_path):
    model_dir = tmp_path / "models"
    first = install(model_dir, "shadow")
    assert install(model_dir, "active").status == "active"
    assert [m.version for m in ModelRegistry(model_dir).list("recurring-scorer")] == [first.version]
    (model_dir / "recurring-scorer" / first.version / WEIGHTS).write_bytes(b"other")
    with pytest.raises(RegistryError):
        install(model_dir, "active")


def test_capability_is_installed():
    assert "recurring-scorer" in [c.name for c in load_installed()]


def test_old_charges_recency_does_not_break_next_date(model):
    days, amounts = monthly(10, 6)
    days = [d - 120 for d in days]
    [s] = model.score(AS_OF, [{"days": days, "amounts": amounts, "category": "SUBSCRIPTIONS"}])
    assert s["next_in_days"] < 0  # the schedule stopped; the backend marks it missed
    assert AS_OF + timedelta(days=s["next_in_days"]) < AS_OF
