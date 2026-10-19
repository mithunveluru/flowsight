from recurring import generate

import score


def group(gid, merchant, recurring, **extra):
    base = {"id": gid, "merchant": merchant, "kind": "video" if recurring else "delivery", "recurring": recurring,
            "period": "MONTHLY" if recurring else None, "series": 1, "next_date": None, "creep": None,
            "drain": False, "count": 3, "category": "ENTERTAINMENT" if recurring else "FOOD_DINING"}
    return {**base, **extra}


CASE = {
    "user": "u1", "region": "IN", "months": 6, "as_of": "2026-03-31",
    "groups": [
        group(0, "Netflix", True, next_date="2026-04-05", creep={"date": "2026-02-05", "before": 499, "after": 649}),
        group(1, "Zomato", False, frequent_irregular=True),
        group(2, "Spotify", True),
    ],
    "tx": [["2026-01-05", 0, "499", "ENTERTAINMENT"], ["2026-02-05", 0, "649", "ENTERTAINMENT"],
           ["2026-03-05", 0, "649", "ENTERTAINMENT"], ["2026-01-09", 1, "400", "FOOD_DINING"],
           ["2026-02-01", 1, "700", "FOOD_DINING"], ["2026-03-20", 1, "350", "FOOD_DINING"],
           ["2026-01-12", 2, "119", "ENTERTAINMENT"], ["2026-02-12", 2, "119", "ENTERTAINMENT"],
           ["2026-03-12", 2, "119", "ENTERTAINMENT"]],
}


def test_generator_is_seeded():
    a, b = generate.build(5), generate.build(5)
    assert a == b
    assert a != generate.build(6)
    user = a[0]
    assert all(row[0] <= user["as_of"] for row in user["tx"])
    assert all(g["next_date"] is None or g["next_date"] > user["as_of"] for g in user["groups"])


def test_overlap_truth_needs_two_subscriptions_in_a_category():
    assert score.overlap_truth(CASE["groups"]) == {0, 2}
    assert score.overlap_truth(CASE["groups"][:2]) == set()


def test_score_user_counts_hits_false_patterns_and_leaks():
    pred = {"user": "u1", "keys": {"netflix": [0, 1, 2], "zomato": [3, 4, 5], "spotify": [6, 7, 8]},
            "patterns": [{"key": "netflix", "period": "MONTHLY", "next": "2026-04-06", "confidence": 0.9},
                         {"key": "zomato", "period": "MONTHLY", "next": "2026-04-20", "confidence": 0.4}],
            "leaks": {"creep": ["netflix", "zomato"], "overlap": ["netflix"]}}
    counts, _ = score.score_recurring_user(CASE, pred)
    m = score.recurring_metrics(counts)
    assert (m["group_precision"], m["group_recall"]) == (0.5, 0.5)
    assert m["irregular_fp_rate"] == 1.0 and m["next_within_3d"] == 1.0
    assert (m["creep_precision"], m["creep_recall"]) == (0.5, 1.0)
    assert (m["overlap_precision"], m["overlap_recall"]) == (1.0, 0.5)
    assert counts["shown_tp"] == 1
