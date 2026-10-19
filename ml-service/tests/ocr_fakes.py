import cv2
import numpy as np

from app.ocr.text import Line


def lines(*texts: str, height: int = 30) -> list[Line]:
    out = []
    for i, t in enumerate(texts):
        words, x = [], 40
        for w in t.split():
            words.append((w, x, 40 + i * 45, 18 * len(w), height))
            x += 18 * len(w) + 18
        out.append(Line(text=t, confidence=0.9, top=40 + i * 45, height=height, words=words))
    return out


def receipt_png(width: int = 700, height: int = 900, rows: int = 12) -> bytes:
    img = np.full((height, width), 245, np.uint8)
    for r in range(rows):
        y = 60 + r * 60
        for x in range(40, width - 40, 27):
            cv2.rectangle(img, (x, y), (x + 18, y + 14), 30, -1)
    return cv2.imencode(".png", img)[1].tobytes()


class FakeRunner:
    """Returns one scripted pass per call (repeating the last), recording the images it saw."""

    def __init__(self, *passes: list[Line]):
        self.passes = list(passes)
        self.images: list[np.ndarray] = []

    def __call__(self, image: np.ndarray) -> list[Line]:
        self.images.append(image)
        return self.passes[min(len(self.images) - 1, len(self.passes) - 1)]
