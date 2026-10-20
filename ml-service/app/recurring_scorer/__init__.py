"""Recurring-payment scorer: is one merchant's charge history a recurring obligation?

For each merchant group the backend sends the charge dates (days relative to as_of) and
amounts, plus the dominant category. The answer is a calibrated probability, the period,
the next expected charge with an interval, the current amount and whether the service is
billed twice per period. A logistic regression over series features (model.py), trained
on a seeded synthetic corpus (corpus.py, train.py).
"""

from datetime import date
from pathlib import Path

from pydantic import BaseModel, Field, model_validator

from app.capability import Capability, Predictor
from app.recurring_scorer.model import RecurringModel
from app.registry import ModelRegistry, ModelVersion

MAX_GROUPS = 300
MAX_CHARGES = 120


class Group(BaseModel):
    days: list[int] = Field(min_length=2, max_length=MAX_CHARGES)
    amounts: list[float] = Field(min_length=2, max_length=MAX_CHARGES)
    category: str | None = Field(default=None, max_length=30)

    @model_validator(mode="after")
    def check(self) -> "Group":
        if len(self.days) != len(self.amounts):
            raise ValueError("days and amounts must have the same length")
        if any(d > 0 or d < -3660 for d in self.days):
            raise ValueError("days must lie within ten years before as_of")
        if any(not a > 0 or a > 1e10 for a in self.amounts):
            raise ValueError("amounts must be positive")
        return self


class ScoreInput(BaseModel):
    as_of: date
    groups: list[Group] = Field(min_length=1, max_length=MAX_GROUPS)


class Score(BaseModel):
    probability: float
    recurring: bool
    period: str | None
    interval_days: float
    next_in_days: int | None
    next_low_in_days: int | None
    next_high_in_days: int | None
    amount: float
    charges_per_period: int


class ScoreOutput(BaseModel):
    scores: list[Score]


class RecurringScorer(Capability):
    name = "recurring-scorer"
    input_model = ScoreInput
    output_model = ScoreOutput

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        weights = RecurringModel.load(artifact_dir)

        def predict(payload: ScoreInput) -> ScoreOutput:
            rows = weights.score(payload.as_of, [g.model_dump() for g in payload.groups])
            return ScoreOutput.model_validate({"scores": rows})

        return predict

    def bootstrap(self, registry: ModelRegistry) -> ModelVersion:
        from app.recurring_scorer.install import install
        return install(registry.model_dir, status="active", registry=registry)

    def input_summary(self, payload: ScoreInput) -> dict:
        return {"groups": len(payload.groups), "charges": sum(len(g.days) for g in payload.groups),
                "with_category": sum(1 for g in payload.groups if g.category)}

    def output_summary(self, result: ScoreOutput) -> dict:
        return {"groups": len(result.scores), "recurring": sum(1 for s in result.scores if s.recurring),
                "parallel": sum(1 for s in result.scores if s.charges_per_period > 1)}

    def confidence(self, result: ScoreOutput) -> float | None:
        flagged = [s.probability for s in result.scores if s.recurring]
        return sum(flagged) / len(flagged) if flagged else None


CAPABILITY = RecurringScorer()
