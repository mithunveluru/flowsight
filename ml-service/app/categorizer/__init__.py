"""Transaction category from description, merchant and direction.

A linear model over character and word n-grams (see model.py), trained on a seeded
synthetic corpus (corpus.py, train.py). Probabilities are temperature-calibrated; each item
gets its top three categories. The backend blends them with the user's own corrections.
"""

from collections import Counter
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field

from app.capability import Capability, Predictor
from app.categorizer.model import CategoryModel
from app.registry import ModelRegistry, ModelVersion

MAX_ITEMS = 100


class Item(BaseModel):
    description: str | None = Field(default=None, max_length=500)
    merchant: str | None = Field(default=None, max_length=255)
    direction: Literal["DEBIT", "CREDIT"] | None = None


class CategorizeInput(BaseModel):
    items: list[Item] = Field(min_length=1, max_length=MAX_ITEMS)


class Alternative(BaseModel):
    category: str
    probability: float


class Prediction(BaseModel):
    category: str
    probability: float
    alternatives: list[Alternative]


class CategorizeOutput(BaseModel):
    predictions: list[Prediction]


class Categorizer(Capability):
    name = "categorizer"
    input_model = CategorizeInput
    output_model = CategorizeOutput

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        weights = CategoryModel.load(artifact_dir)

        def predict(payload: CategorizeInput) -> CategorizeOutput:
            rows = weights.predict([item.model_dump() for item in payload.items])
            return CategorizeOutput.model_validate({"predictions": rows})

        return predict

    def bootstrap(self, registry: ModelRegistry) -> ModelVersion:
        from app.categorizer.install import install
        return install(registry.model_dir, status="active", registry=registry)

    def input_summary(self, payload: CategorizeInput) -> dict:
        return {"items": len(payload.items),
                "with_merchant": sum(1 for i in payload.items if i.merchant),
                "with_direction": sum(1 for i in payload.items if i.direction)}

    def output_summary(self, result: CategorizeOutput) -> dict:
        return {"items": len(result.predictions),
                "categories": dict(sorted(Counter(p.category for p in result.predictions).items()))}

    def confidence(self, result: CategorizeOutput) -> float | None:
        if not result.predictions:
            return None
        return sum(p.probability for p in result.predictions) / len(result.predictions)


CAPABILITY = Categorizer()
