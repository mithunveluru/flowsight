"""Nearest merchant alias by character n-gram TF-IDF vectors.

Pure Python and small: the artifact is the alias table itself (aliases.json, written by
`python -m app.merchant_embeddings.build`), vectors are built when the version loads, and a
query is compared through an inverted n-gram index. The backend decides whether the
neighbour is close enough to use.
"""

import json
from pathlib import Path

from pydantic import BaseModel, Field

from app.capability import Capability, Predictor
from app.merchant_embeddings.vectors import NgramIndex
from app.registry import ModelVersion

ARTIFACT = "aliases.json"


class MerchantInput(BaseModel):
    text: str = Field(min_length=1, max_length=200)


class MerchantOutput(BaseModel):
    canonical: str | None
    alias: str | None
    similarity: float


class MerchantEmbeddings(Capability):
    name = "merchant-embeddings"
    input_model = MerchantInput
    output_model = MerchantOutput

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        rows = json.loads((artifact_dir / ARTIFACT).read_text(encoding="utf-8"))
        index = NgramIndex([(r["alias"], r["canonical"]) for r in rows])

        def predict(payload: MerchantInput) -> MerchantOutput:
            hit = index.nearest(payload.text)
            if hit is None:
                return MerchantOutput(canonical=None, alias=None, similarity=0.0)
            alias, canonical, score = hit
            return MerchantOutput(canonical=canonical, alias=alias, similarity=round(score, 4))

        return predict

    def input_summary(self, payload: MerchantInput) -> dict:
        return {"length": len(payload.text), "words": len(payload.text.split())}

    def output_summary(self, result: MerchantOutput) -> dict:
        return {"matched": result.canonical is not None, "similarity": result.similarity}

    def confidence(self, result: MerchantOutput) -> float:
        return result.similarity


CAPABILITY = MerchantEmbeddings()
