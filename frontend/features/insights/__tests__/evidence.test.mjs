import { test } from "node:test";
import assert from "node:assert/strict";
import { forecastEvidence, leakEvidence, recommendationEvidence, recurringEvidence } from "../evidence.ts";

const leak = (evidence) => ({ type: "SUBSCRIPTION_CREEP", severity: "HIGH", title: "t", description: "desc", recommendation: "rec", monthlyImpact: 100, annualImpact: 1200, affectedItemsCount: 1, items: [{ merchant: "Netflix", amount: 150, detail: "d", category: "x", categoryLabel: "X" }], evidence });

test("leakEvidence uses the API evidence", () => {
  const e = leakEvidence(leak({ source: "MODEL", method: "change-point:median-mad", windowStart: "2026-01-01", windowEnd: "2026-09-30", inputs: { priceBefore: 499, paymentsAfter: 3 }, confidence: 0.97 }));
  assert.equal(e.source, "model");
  assert.equal(e.confidence, 0.97);
  assert.deepEqual(e.window, { from: "2026-01-01", to: "2026-09-30" });
  assert.equal(e.inputs[0].label, "Price before");
  assert.equal(e.inputs.at(-1).label, "Netflix");
});

test("leakEvidence invents no source or window without evidence", () => {
  const e = leakEvidence(leak(undefined));
  assert.equal(e.source, undefined);
  assert.equal(e.window, undefined);
  assert.equal(e.method, "desc");
});

test("recurringEvidence distinguishes model and rules", () => {
  const base = { merchant: "N", period: "MONTHLY", periodLabel: "Monthly", estimatedAmount: 649, lastSeenDate: "2026-09-01", nextExpectedDate: "2026-10-01", occurrenceCount: 4, confidence: 0.9, confidenceTier: "HIGH" };
  const m = recurringEvidence({ ...base, scoredBy: "MODEL", probability: 0.98, nextExpectedLow: "2026-09-30", nextExpectedHigh: "2026-10-02", modelVersion: "logreg-1" });
  assert.equal(m.source, "model");
  assert.equal(m.confidence, 0.98);
  assert.ok(m.inputs.some((i) => i.label === "Likely window"));
  const r = recurringEvidence({ ...base, scoredBy: "RULES" });
  assert.equal(r.source, "rules");
  assert.equal(r.confidence, undefined);
  assert.equal(r.confidenceLevel, "high");
});

test("recommendationEvidence maps severity words to levels", () => {
  const e = recommendationEvidence({ title: "", description: "why", suggestedAction: "", potentialMonthlySaving: 1, potentialAnnualSaving: 12, confidence: "MEDIUM", evidence: ["a"], source: "LEAK", rankingReason: "r" });
  assert.equal(e.confidenceLevel, "medium");
  assert.equal(e.inputs[0].value, "Money drains detector");
  assert.deepEqual(e.caveats, ["r"]);
});

test("forecastEvidence converts month window and flags low history", () => {
  const e = forecastEvidence({ method: "m", window: ["2026-08", "2026-09"], dataMonths: 2, lowHistory: true, intervalLevels: [80, 95], intervalSource: "GLOBAL_PRIOR", backtestPoints: 0, backtestMae: null, backtestWape: null, ownWeight: 0.25, priorsVersion: "p", source: "ml-service", modelVersion: "v", note: "n" }, "2026-10-20");
  assert.equal(e.source, "model");
  assert.equal(e.window.from.getDate(), 1);
  assert.equal(e.window.to.getDate(), 30);
  assert.equal(e.caveats.length, 1);
  assert.equal(e.inputs.some((i) => i.label === "Backtest mean error"), false);
});
