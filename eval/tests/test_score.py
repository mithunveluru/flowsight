import json
from decimal import Decimal

import score

TRUTH = {
    "id": "syn-0001", "currency": "USD", "number_format": "us", "perturbation": "clean", "traps": ["tip_after_total"],
    "merchant_raw": "BILL'S DINER", "merchant_canonical": "Bill's Diner", "total": "12.96", "date": "2024-05-01",
    "items": [{"name": "Burger Deluxe", "qty": 1, "unit_price": "12.00", "amount": "12.00"}],
}


def write_jsonl(path, rows):
    path.write_text("".join(json.dumps(r) + "\n" for r in rows))


def test_score_receipt_all_fields():
    pred = {"merchant": "BILL'S DINER", "total": Decimal("12.96"), "date": "2024-05-01", "currency": "USD",
            "line_items": [{"name": "Burger Deluxe", "price": Decimal("12.00")}], "confidence": 0.85,
            "provider": "RECEIPT_OCR_SERVICE"}
    s = score.score_receipt(TRUTH, pred)
    assert s["merchant_exact"] and s["merchant_fuzzy"] and s["total"] and s["date"] and s["currency"]
    assert (s["items_matched"], s["items_pred"], s["items_true"]) == (1, 1, 1)


def test_score_receipt_fuzzy_accepts_printed_name_but_exact_needs_canonical():
    s = score.score_receipt({**TRUTH, "merchant_raw": "STARBUCKS COFFEE #12345", "merchant_canonical": "Starbucks"},
                            {"merchant": "STARBUCKS COFFEE #12345"})
    assert s["merchant_fuzzy"] and not s["merchant_exact"]


def test_score_receipt_missing_prediction_is_wrong_everywhere():
    s = score.score_receipt(TRUTH, None)
    assert not any(s[k] for k in score.RECEIPT_RATES)
    assert s["provider"] == "MISSING"


def test_receipt_slices_include_traps():
    assert score.receipt_slices(TRUTH) == ["currency=USD", "number_format=us", "perturbation=clean",
                                           "trap=tip_after_total"]


def receipts_args(tmp_path, preds, update=False):
    truth = tmp_path / "truth.jsonl"
    write_jsonl(truth, [TRUTH])
    pred = tmp_path / "pred.jsonl"
    write_jsonl(pred, preds)
    return ["receipts", "--suite", "ocr-tesseract", "--truth", str(truth), "--pred", str(pred),
            "--baseline", str(tmp_path / "baseline.json")] + (["--update-baseline"] if update else [])


GOOD = {"id": "syn-0001", "merchant": "Bill's Diner", "total": 12.96, "date": "2024-05-01", "currency": "USD",
        "line_items": [], "confidence": None, "provider": "TESSERACT_FALLBACK"}


def test_receipts_gate_end_to_end(tmp_path, capsys):
    assert score.main(receipts_args(tmp_path, [GOOD])) == 2  # no baseline yet
    assert score.main(receipts_args(tmp_path, [GOOD], update=True)) == 0
    stored = json.loads((tmp_path / "baseline.json").read_text())
    assert stored["metrics"]["total"] == 1.0 and stored["tolerance"] == {"overall": 0.01, "slice_items": 1}
    assert score.main(receipts_args(tmp_path, [GOOD])) == 0
    assert score.main(receipts_args(tmp_path, [{**GOOD, "total": 12.95}])) == 1
    assert "FAIL ocr-tesseract" in capsys.readouterr().out


def test_receipts_gate_rejects_changed_truth(tmp_path):
    score.main(receipts_args(tmp_path, [GOOD], update=True))
    baseline = tmp_path / "baseline.json"
    stored = json.loads(baseline.read_text())
    stored["truth_sha256"] = "0" * 64
    baseline.write_text(json.dumps(stored))
    assert score.main(receipts_args(tmp_path, [GOOD])) == 2


def test_run_without_any_prediction_is_rejected(tmp_path):
    assert score.main(receipts_args(tmp_path, [{**GOOD, "provider": "FAILED"}], update=True)) == 2
    assert not (tmp_path / "baseline.json").exists()


def test_update_keeps_hand_tuned_tolerance(tmp_path):
    (tmp_path / "baseline.json").write_text(json.dumps({"tolerance": {"overall": 0.02, "slice_items": 2}}))
    score.main(receipts_args(tmp_path, [GOOD], update=True))
    assert json.loads((tmp_path / "baseline.json").read_text())["tolerance"] == {"overall": 0.02, "slice_items": 2}


def test_manifest_adds_private_receipts_with_sidecar_truth(tmp_path):
    truth = tmp_path / "truth.jsonl"
    write_jsonl(truth, [{**TRUTH, "image": "images/syn-0001.jpg"}])
    private = tmp_path / "private"
    private.mkdir()
    (private / "lunch.jpg").write_bytes(b"jpg")
    (private / "lunch.json").write_text(json.dumps({"merchant_canonical": "Cafe", "total": "9.50", "currency": "AED"}))
    (private / "orphan.png").write_bytes(b"png")
    out = tmp_path / "out"
    assert score.main(["manifest", "--truth", str(truth), "--private", str(private), "--out", str(out)]) == 0
    rows = [json.loads(line) for line in (out / "manifest.jsonl").read_text().splitlines()]
    assert [r["id"] for r in rows] == ["syn-0001", "real-lunch"]
    assert rows[0]["image"] == str(tmp_path / "images" / "syn-0001.jpg")
    real = json.loads((out / "private-truth.jsonl").read_text())
    assert real["source"] == "real" and real["total"] == "9.50"

    score.main(["manifest", "--truth", str(truth), "--private", str(private), "--out", str(out), "--synthetic-only"])
    assert len((out / "manifest.jsonl").read_text().splitlines()) == 1
    assert (out / "private-truth.jsonl").read_text() == ""


def test_categorization_scoring(tmp_path, capsys):
    cases = tmp_path / "cases.csv"
    cases.write_text("id,region,description,merchant,amount,currency,category\n"
                     "c1,US,SHELL OIL 123,,40,USD,TRANSPORTATION\n"
                     "c2,US,NETFLIX.COM,,15,USD,ENTERTAINMENT\n")
    pred = tmp_path / "pred.jsonl"
    write_jsonl(pred, [{"id": "c1", "category": "UNCATEGORIZED", "confidence": 0.25},
                       {"id": "c2", "category": "ENTERTAINMENT", "confidence": 0.65}])
    args = ["categorization", "--suite", "categorization", "--cases", str(cases), "--pred", str(pred),
            "--baseline", str(tmp_path / "b.json"), "--update-baseline"]
    assert score.main(args) == 0
    stored = json.loads((tmp_path / "b.json").read_text())
    assert stored["metrics"]["accuracy"] == 0.5
    assert stored["metrics"]["macro_f1"] == 0.5
    assert stored["metrics"]["ece"] == 0.3
    assert stored["info"]["uncategorized_rate"] == 0.5


def test_merchant_scoring_counts_alias_collisions():
    collision = score.score_merchant({"expected": "Emirates NBD"},
                                     {"canonical": "EMI", "key": "emi", "expected_key": "emi", "extracted": "Emirates NBD"})
    assert collision["alias_wrong"] and not collision["alias_exact"]
    assert collision["key_match"] and collision["extract_exact"]
    fallback = score.score_merchant({"expected": "Panda"},
                                    {"canonical": None, "key": "panda", "expected_key": "panda", "extracted": None})
    assert fallback["fuzzy"] and not fallback["alias_wrong"] and not fallback["extract_fuzzy"]
