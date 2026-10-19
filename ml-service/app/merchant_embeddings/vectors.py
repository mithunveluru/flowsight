"""Character n-gram TF-IDF vectors with cosine nearest-neighbour search."""

import math
import re
import unicodedata
from collections import Counter, defaultdict

NGRAM_SIZES = (2, 3, 4)
MAX_WINDOW_WORDS = 3


def normalize(text: str) -> str:
    s = unicodedata.normalize("NFKC", text).casefold()
    s = re.sub(r"['’`]", "", s)
    s = re.sub(r"[^\w]+|_", " ", s)
    return " ".join(s.split())


def ngrams(text: str) -> Counter:
    grams: Counter = Counter()
    for word in text.split():
        padded = f"<{word}>"
        for n in NGRAM_SIZES:
            for i in range(len(padded) - n + 1):
                grams[padded[i:i + n]] += 1
    return grams


def windows(text: str) -> list[str]:
    """The whole text plus every run of up to three words, so a brand inside a longer
    descriptor is compared on its own."""
    words = text.split()
    out = {text}
    for size in range(1, MAX_WINDOW_WORDS + 1):
        for i in range(len(words) - size + 1):
            out.add(" ".join(words[i:i + size]))
    return [w for w in out if w]


class NgramIndex:
    def __init__(self, entries: list[tuple[str, str]]):
        self.entries = [(normalize(a), c) for a, c in entries if normalize(a)]
        df: Counter = Counter()
        grams = [ngrams(a) for a, _ in self.entries]
        for g in grams:
            df.update(g.keys())
        n = len(self.entries)
        self.idf = {g: math.log((1 + n) / (1 + d)) + 1.0 for g, d in df.items()}
        self.unseen_idf = math.log(1 + n) + 1.0
        self.postings: dict[str, list[tuple[int, float]]] = defaultdict(list)
        for i, g in enumerate(grams):
            for gram, weight in self._weigh(g).items():
                self.postings[gram].append((i, weight))

    def _weigh(self, grams: Counter) -> dict[str, float]:
        weights = {g: c * self.idf.get(g, self.unseen_idf) for g, c in grams.items()}
        norm = math.sqrt(sum(w * w for w in weights.values())) or 1.0
        return {g: w / norm for g, w in weights.items()}

    def nearest(self, text: str) -> tuple[str, str, float] | None:
        """(alias, canonical, cosine) of the best alias over all windows of the text."""
        best: tuple[str, str, float] | None = None
        for window in windows(normalize(text)):
            scores: dict[int, float] = defaultdict(float)
            for gram, weight in self._weigh(ngrams(window)).items():
                for i, alias_weight in self.postings.get(gram, ()):
                    scores[i] += weight * alias_weight
            for i, score in scores.items():
                if best is None or score > best[2] + 1e-12:
                    alias, canonical = self.entries[i]
                    best = (alias, canonical, min(score, 1.0))
        return best
