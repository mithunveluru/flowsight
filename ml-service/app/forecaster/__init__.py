"""Monthly income, spend, net and category forecasts with 80% / 95% prediction intervals.

The backend sends a user's complete monthly totals (no transaction text) and the current
month so far; see model.py for the method. The artifact holds the interval priors from
calibrate.py. The backend computes the same forecast in-app whenever this capability is off.
"""

from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field, model_validator

from app.capability import Capability, Predictor
from app.forecaster.model import MAX_MONTHS, PRIORS, Priors, forecast_net, forecast_series, floor_of
from app.registry import ModelRegistry, ModelVersion

MAX_SERIES = 20
MAX_STEPS = 12


class SeriesInput(BaseModel):
    name: str = Field(min_length=1, max_length=40, pattern=r"^[A-Za-z_]+$")
    kind: Literal["income", "spend", "category"]
    values: list[float] = Field(max_length=MAX_MONTHS)
    month_to_date: float = Field(ge=0)
    same_days: float = Field(ge=0)


class ForecastInput(BaseModel):
    steps: int = Field(ge=1, le=MAX_STEPS)
    day_fraction: float = Field(gt=0, le=1)
    series: list[SeriesInput] = Field(min_length=2, max_length=MAX_SERIES)

    @model_validator(mode="after")
    def _shape(self):
        kinds = [s.kind for s in self.series]
        if kinds[:2] != ["income", "spend"] or "income" in kinds[2:] or "spend" in kinds[2:]:
            raise ValueError("series must start with income and spend, then categories")
        if len({len(s.values) for s in self.series}) != 1:
            raise ValueError("all series need the same number of months")
        return self


class Step(BaseModel):
    k: int
    point: float
    lower80: float
    upper80: float
    lower95: float
    upper95: float


class SeriesOutput(BaseModel):
    name: str
    kind: str
    method: str
    data_months: int
    scale: float
    steps: list[Step]
    backtest_points: int
    backtest_mae: float | None
    backtest_wape: float | None
    interval_ratio: float
    own_weight: float


class ForecastOutput(BaseModel):
    priors_version: str
    series: list[SeriesOutput]


class Forecaster(Capability):
    name = "forecaster"
    input_model = ForecastInput
    output_model = ForecastOutput

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        priors = Priors.load(artifact_dir / PRIORS)

        def predict(payload: ForecastInput) -> ForecastOutput:
            income, spend, *cats = payload.series

            def one(s: SeriesInput) -> dict:
                floor = floor_of(income.values, spend.values, income.month_to_date, spend.month_to_date,
                                 payload.day_fraction, s.kind)
                return forecast_series(s.name, s.kind, s.values, s.month_to_date, s.same_days,
                                       payload.steps, floor, priors)

            inc, sp = one(income), one(spend)
            floor = floor_of(income.values, spend.values, income.month_to_date, spend.month_to_date,
                             payload.day_fraction)
            rows = [inc, sp, forecast_net(inc, sp, income.values, spend.values, floor, priors)]
            rows += [one(c) for c in cats]
            return ForecastOutput.model_validate({"priors_version": priors.version, "series": rows})

        return predict

    def bootstrap(self, registry: ModelRegistry) -> ModelVersion:
        from app.forecaster.install import install
        return install(registry.model_dir, status="active", registry=registry)

    def input_summary(self, payload: ForecastInput) -> dict:
        return {"series": len(payload.series), "months": len(payload.series[0].values), "steps": payload.steps}

    def output_summary(self, result: ForecastOutput) -> dict:
        return {"methods": sorted({s.method for s in result.series})}


CAPABILITY = Forecaster()
