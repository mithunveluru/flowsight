"""Transaction category model: text features and numpy-only inference.

Training (app.categorizer.train) fits the weights with scikit-learn using `features` as the
analyzer; serving and the offline eval only need this module and numpy. Deliberately free
of app.* imports so the eval image can load it by file path.
"""

import io
import json
import re
import unicodedata
import zipfile
from pathlib import Path

import numpy as np

WEIGHTS = "model.npz"
META = "meta.json"

_NON_WORD = re.compile(r"[^\w&]+|_+")
_MASKED = re.compile(r"[x\d]*x{3,}[x\d]*")
_DIGIT = re.compile(r"\d")


def clean(text: str | None) -> str:
    """Lower-case words without reference numbers, masked cards or punctuation."""
    if not text:
        return ""
    words = _NON_WORD.sub(" ", unicodedata.normalize("NFKC", text).lower()).split()
    # three or more digits make a reference, phone, card or store number, not a name
    kept = [w for w in words if sum(c.isdigit() for c in w) < 3 and not _MASKED.fullmatch(w)]
    return " ".join(_DIGIT.sub("0", w) for w in kept)


def features(item: dict) -> list[str]:
    """Feature tokens for one transaction: merchant and description words, their character
    2-5-grams, description word pairs, and the direction when it is known."""
    merchant = clean(item.get("merchant")).split()
    description = clean(item.get("description")).split()
    out = [f"m|{w}" for w in merchant]
    out += [f"w|{w}" for w in description]
    out += [f"b|{a} {b}" for a, b in zip(description, description[1:])]
    for w in dict.fromkeys(merchant + description):
        padded = f" {w} "
        for n in range(2, 6):
            if n > len(padded):
                break
            out += [f"c|{padded[i:i + n]}" for i in range(len(padded) - n + 1)]
    direction = (item.get("direction") or "").lower()
    if direction in ("debit", "credit"):
        out.append(f"d|{direction}")
    return out


class CategoryModel:
    """Sublinear TF-IDF over `features`, a linear layer and a softmax with temperature."""

    def __init__(self, vocabulary, idf, coef, intercept, classes, temperature, meta=None):
        self.index = {term: i for i, term in enumerate(vocabulary)}
        self.idf = np.asarray(idf, dtype=np.float64)
        self.coef = np.asarray(coef, dtype=np.float64)
        self.intercept = np.asarray(intercept, dtype=np.float64)
        self.classes = [str(c) for c in classes]
        self.temperature = float(temperature)
        self.meta = meta or {}

    @classmethod
    def load(cls, directory: Path) -> "CategoryModel":
        directory = Path(directory)
        with np.load(directory / WEIGHTS, allow_pickle=False) as z:
            arrays = {k: z[k] for k in z.files}
        meta = json.loads((directory / META).read_text(encoding="utf-8"))
        return cls(arrays["vocabulary"].tolist(), arrays["idf"], arrays["coef"], arrays["intercept"],
                   arrays["classes"].tolist(), float(arrays["temperature"]), meta)

    def vector(self, item: dict) -> tuple[np.ndarray, np.ndarray]:
        counts: dict[int, int] = {}
        for token in features(item):
            j = self.index.get(token)
            if j is not None:
                counts[j] = counts.get(j, 0) + 1
        if not counts:
            return np.zeros(0, dtype=np.int64), np.zeros(0)
        idx = np.fromiter(counts.keys(), dtype=np.int64, count=len(counts))
        tf = 1.0 + np.log(np.fromiter(counts.values(), dtype=np.float64, count=len(counts)))
        values = tf * self.idf[idx]
        return idx, values / np.linalg.norm(values)

    def logits(self, items: list[dict]) -> np.ndarray:
        out = np.empty((len(items), len(self.classes)))
        for r, item in enumerate(items):
            idx, values = self.vector(item)
            out[r] = self.coef[:, idx] @ values + self.intercept
        return out

    def predict_proba(self, items: list[dict]) -> np.ndarray:
        return softmax(self.logits(items) / self.temperature)

    def predict(self, items: list[dict], top: int = 3) -> list[dict]:
        results = []
        for row in self.predict_proba(items):
            order = sorted(range(len(row)), key=lambda k: (-row[k], self.classes[k]))[:top]
            alternatives = [{"category": self.classes[k], "probability": float(row[k])} for k in order]
            results.append({"category": alternatives[0]["category"],
                            "probability": alternatives[0]["probability"],
                            "alternatives": alternatives})
        return results


def softmax(z: np.ndarray) -> np.ndarray:
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


def save(directory: Path, arrays: dict[str, np.ndarray], meta: dict) -> None:
    """Writes the weights and metadata byte-for-byte reproducibly (fixed zip timestamps)."""
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(directory / WEIGHTS, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name in sorted(arrays):
            buf = io.BytesIO()
            np.lib.format.write_array(buf, np.asarray(arrays[name]), allow_pickle=False)
            info = zipfile.ZipInfo(f"{name}.npy", date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, buf.getvalue())
    (directory / META).write_text(json.dumps(meta, indent=2, sort_keys=True) + "\n", encoding="utf-8")
