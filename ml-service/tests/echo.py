from pathlib import Path

from pydantic import BaseModel

from app.capability import Capability, Predictor
from app.registry import ModelVersion


class EchoInput(BaseModel):
    text: str


class EchoOutput(BaseModel):
    label: str
    length: int
    confidence: float


class EchoCapability(Capability):
    """Labels text with whatever its artifact says; fails when the artifact says 'boom'."""

    name = "echo"
    input_model = EchoInput
    output_model = EchoOutput

    def __init__(self) -> None:
        self.loads: list[str] = []

    def load(self, model: ModelVersion, artifact_dir: Path) -> Predictor:
        self.loads.append(model.version)
        label = (artifact_dir / "label.txt").read_text().strip()

        def predict(payload: EchoInput) -> EchoOutput:
            if label == "boom":
                raise RuntimeError("model failure")
            return EchoOutput(label=label, length=len(payload.text), confidence=0.75)

        return predict

    def input_summary(self, payload: EchoInput) -> dict:
        return {"length": len(payload.text)}

    def output_summary(self, result: EchoOutput) -> dict:
        return {"label": result.label}

    def confidence(self, result: EchoOutput) -> float:
        return result.confidence
