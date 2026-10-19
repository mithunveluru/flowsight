"""Tesseract CLI runner: TSV output to lines with word boxes."""

import os
import subprocess
import tempfile
from typing import Callable

import numpy as np

from app.ocr.images import encode_png
from app.ocr.text import Line

TIMEOUT_SECONDS = 30
# one uniform block: keeps right-aligned amounts on the same line as their labels
PAGE_SEGMENTATION = "6"

Runner = Callable[[np.ndarray], list[Line]]


def parse_tsv(tsv: str) -> list[Line]:
    lines: dict[tuple, Line] = {}
    confs: dict[tuple, list[float]] = {}
    for row in tsv.splitlines()[1:]:
        cols = row.split("\t")
        if len(cols) < 12 or cols[0] != "5":
            continue
        try:
            conf = float(cols[10])
            left, top, width, height = (int(c) for c in cols[6:10])
        except ValueError:
            continue
        text = cols[11].strip()
        if conf < 0 or not text:
            continue
        key = (cols[2], cols[3], cols[4])
        line = lines.setdefault(key, Line(text="", top=top))
        line.text = f"{line.text} {text}".strip()
        line.top = min(line.top, top)
        line.height = max(line.height, height)
        line.words.append((text, left, top, width, height))
        confs.setdefault(key, []).append(conf / 100)
    for key, line in lines.items():
        line.confidence = min(1.0, sum(confs[key]) / len(confs[key]))
    return sorted(lines.values(), key=lambda l: l.top)


def make_runner(languages: str = "eng") -> Runner:
    def run(image: np.ndarray) -> list[Line]:
        fd, path = tempfile.mkstemp(suffix=".png")
        try:
            with os.fdopen(fd, "wb") as f:
                f.write(encode_png(image))
            out = subprocess.run(
                ["tesseract", path, "stdout", "-l", languages, "--psm", PAGE_SEGMENTATION, "tsv"],
                capture_output=True, timeout=TIMEOUT_SECONDS, check=True)
            return parse_tsv(out.stdout.decode("utf-8", "replace"))
        finally:
            os.unlink(path)

    return run
