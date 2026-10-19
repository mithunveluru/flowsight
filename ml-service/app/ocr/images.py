"""OpenCV preprocessing for receipt photos: orientation, perspective correction of the paper,
deskew, adaptive contrast, light denoising and resolution normalization."""

import re

import cv2
import numpy as np

TARGET_PAPER_WIDTH = 1500
MAX_SKEW_DEGREES = 15.0
MIN_SKEW_DEGREES = 1.5


def decode(data: bytes) -> np.ndarray | None:
    """Grayscale image; cv2.imdecode applies the EXIF orientation tag."""
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_GRAYSCALE)
    return img if img is not None and img.size else None


def _order(pts: np.ndarray) -> np.ndarray:
    s, d = pts.sum(axis=1), np.diff(pts, axis=1).ravel()
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)], pts[np.argmax(s)], pts[np.argmax(d)]], dtype=np.float32)


def find_paper(gray: np.ndarray) -> np.ndarray | None:
    """Corners of the brightest large quadrilateral (the receipt on a darker table), or None."""
    h, w = gray.shape
    scale = min(1.0, 1000 / max(h, w))
    small = cv2.resize(gray, (int(w * scale), int(h * scale))) if scale < 1 else gray
    _, mask = cv2.threshold(cv2.GaussianBlur(small, (5, 5), 0), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    c = max(contours, key=cv2.contourArea)
    area = cv2.contourArea(c)
    # a scan already filling the frame, or a speck: nothing to correct
    if area < 0.15 * mask.size or area > 0.97 * mask.size:
        return None
    approx = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
    quad = approx.reshape(-1, 2) if len(approx) == 4 else cv2.boxPoints(cv2.minAreaRect(c))
    return _order(np.asarray(quad, dtype=np.float32) / scale)


def warp(gray: np.ndarray, corners: np.ndarray) -> np.ndarray:
    tl, tr, br, bl = corners
    width = int(max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl)))
    height = int(max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr)))
    dst = np.array([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]], dtype=np.float32)
    m = cv2.getPerspectiveTransform(corners, dst)
    return cv2.warpPerspective(gray, m, (width, height), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def skew_angle(gray: np.ndarray) -> float:
    """Projection-profile skew in degrees (rotate by the negative of it to straighten)."""
    h, w = gray.shape
    scale = min(1.0, 600 / max(h, w))
    small = cv2.resize(gray, (max(1, int(w * scale)), max(1, int(h * scale)))) if scale < 1 else gray
    _, ink = cv2.threshold(small, 0, 1, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    ys, xs = np.nonzero(ink)
    if len(xs) < 50:
        return 0.0
    span = small.shape[0] + small.shape[1]

    def sharpness(deg: float) -> float:
        r = np.deg2rad(deg)
        rows = np.round(xs * np.sin(r) + ys * np.cos(r)).astype(int) + span
        prof = np.bincount(rows, minlength=2 * span + 1).astype(float)
        return float(np.sum(np.diff(prof) ** 2))

    coarse = max(np.arange(-MAX_SKEW_DEGREES, MAX_SKEW_DEGREES + 0.01, 1.0), key=sharpness)
    return float(max(np.arange(coarse - 1.0, coarse + 1.01, 0.2), key=sharpness))


def rotate(gray: np.ndarray, degrees: float) -> np.ndarray:
    h, w = gray.shape
    m = cv2.getRotationMatrix2D((w / 2, h / 2), -degrees, 1.0)
    cos, sin = abs(m[0, 0]), abs(m[0, 1])
    nw, nh = int(h * sin + w * cos), int(h * cos + w * sin)
    m[0, 2] += nw / 2 - w / 2
    m[1, 2] += nh / 2 - h / 2
    fill = int(np.percentile(gray, 90))
    return cv2.warpAffine(gray, m, (nw, nh), flags=cv2.INTER_CUBIC, borderValue=fill)


def prepare(gray: np.ndarray) -> np.ndarray:
    corners = find_paper(gray)
    if corners is not None:
        gray = warp(gray, corners)
    angle = skew_angle(gray)
    if abs(angle) >= MIN_SKEW_DEGREES:
        gray = rotate(gray, angle)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    gray = cv2.medianBlur(gray, 3)
    s = max(0.5, min(3.0, TARGET_PAPER_WIDTH / gray.shape[1]))
    if abs(s - 1) >= 0.05:
        gray = cv2.resize(gray, None, fx=s, fy=s, interpolation=cv2.INTER_CUBIC)
    return gray


def renderings(gray: np.ndarray) -> list[np.ndarray]:
    """The pass inputs, in order: adaptive threshold, gray, Otsu threshold at a second scale."""
    block = max(15, (gray.shape[1] // 16) | 1)
    adaptive = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, block, 15)
    small = cv2.resize(gray, None, fx=0.8, fy=0.8, interpolation=cv2.INTER_AREA)
    _, otsu = cv2.threshold(small, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return [adaptive, gray, otsu]


# personal and business identifiers that must not leave the service
PII = [
    re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+"),
    re.compile(r"\b\d{2}[A-Z]{5}\d{4}[A-Z][A-Z\d]Z[A-Z\d]\b"),
    re.compile(r"\b[A-Z]{5}\d{4}[A-Z]\b"),
    re.compile(r"[*xX#]{2,}\s?\d{2,4}\b"),
    re.compile(r"(?<![\d.,])(?:\d[ -]?){9,19}(?![\d.,])"),
    re.compile(r"\+\d{1,3}"),
    re.compile(r"\(\d{3}\)\s?\d{3}[\s-]\d{4}"),
    re.compile(r"\b(?:GSTIN|TRN|VAT\s*No|PAN|Tel|Ph|Phone|Mobile)\b.*", re.I),
]


def pii_boxes(words: list[tuple[str, int, int, int, int]], line_texts: list[tuple[str, list]]) -> list[tuple[int, int, int, int]]:
    """Boxes (left, top, width, height) of the words that form a phone number, card number,
    GSTIN/TRN/VAT ID, PAN or email. Located with Tesseract word boxes, matched per line."""
    boxes = []
    for text, line_words in line_texts:
        spans, pos = [], 0
        for w in line_words:
            start = text.find(w[0], pos)
            if start < 0:
                continue
            spans.append((start, start + len(w[0]), w))
            pos = start + len(w[0])
        for pattern in PII:
            for m in pattern.finditer(text):
                for s, e, w in spans:
                    if s < m.end() and e > m.start():
                        boxes.append(w[1:])
    return boxes


def mask(gray: np.ndarray, boxes: list[tuple[int, int, int, int]], pad: int = 4) -> np.ndarray:
    out = gray.copy()
    for left, top, width, height in boxes:
        cv2.rectangle(out, (left - pad, top - pad), (left + width + pad, top + height + pad), 0, thickness=-1)
    return out


def encode_png(gray: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".png", gray)
    if not ok:
        raise ValueError("could not encode image")
    return buf.tobytes()
