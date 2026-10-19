import cv2
import numpy as np

from app.ocr import images


def paper(w=400, h=700):
    img = np.full((h, w), 245, np.uint8)
    for y in range(40, h - 40, 30):
        for x in range(40, w - 40, 27):
            cv2.rectangle(img, (x, y), (x + 18, y + 11), 30, -1)
    return img


def test_finds_and_flattens_receipt_on_a_table():
    table = np.full((1000, 800), 100, np.uint8)
    table[150:850, 200:600] = paper()
    corners = images.find_paper(table)
    assert corners is not None
    flat = images.warp(table, corners)
    assert abs(flat.shape[1] - 400) < 15 and abs(flat.shape[0] - 700) < 15


def test_scan_without_background_is_left_alone():
    assert images.find_paper(paper()) is None


def test_detects_and_removes_skew():
    skewed = images.rotate(paper(), 8)
    assert abs(abs(images.skew_angle(skewed)) - 8) < 0.7
    assert abs(images.skew_angle(images.rotate(skewed, images.skew_angle(skewed)))) < 0.7


def test_prepare_normalizes_width_and_renderings():
    prepared = images.prepare(paper(w=600))
    assert prepared.shape[1] == images.TARGET_PAPER_WIDTH
    adaptive, gray, otsu = images.renderings(prepared)
    assert images.prepare(paper()).shape[1] == 1200  # 3x scale cap
    assert set(np.unique(adaptive)) <= {0, 255} and set(np.unique(otsu)) <= {0, 255}
    assert gray is prepared


def test_pii_boxes_cover_phone_tax_id_and_card():
    line_texts = [
        ("Ph: +91 6820447270", [("Ph:", 10, 10, 30, 20), ("+91", 50, 10, 30, 20), ("6820447270", 90, 10, 100, 20)]),
        ("GSTIN: 29AAACV4807F1Z6", [("GSTIN:", 10, 40, 60, 20), ("29AAACV4807F1Z6", 80, 40, 150, 20)]),
        ("VISA ****8953 21.19", [("VISA", 10, 70, 40, 20), ("****8953", 60, 70, 80, 20), ("21.19", 150, 70, 50, 20)]),
        ("Cappuccino 180.00", [("Cappuccino", 10, 100, 100, 20), ("180.00", 120, 100, 60, 20)]),
    ]
    boxes = images.pii_boxes([], line_texts)
    assert (90, 10, 100, 20) in boxes and (80, 40, 150, 20) in boxes and (60, 70, 80, 20) in boxes
    assert (120, 100, 60, 20) not in boxes and (150, 70, 50, 20) not in boxes
    masked = images.mask(np.full((200, 300), 255, np.uint8), [(90, 10, 100, 20)])
    assert masked[20, 140] == 0 and masked[150, 150] == 255


def test_decode_rejects_garbage():
    assert images.decode(b"not an image") is None
