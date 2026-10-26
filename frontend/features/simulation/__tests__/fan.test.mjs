import { test } from "node:test";
import assert from "node:assert/strict";
import { clampReturn, fanRows, fanSummary, goalOddsText, monteCarloEvidence } from "../fan.ts";

const fan = [{ month: 2, p10: 100, p50: 200, p90: 400 }, { month: 1, p10: 50, p50: 90, p90: 150 }];

test("fanRows sorts, stacks the band and joins the baseline median", () => {
  const rows = fanRows(fan, [{ month: 1, p10: 0, p50: 70, p90: 0 }]);
  assert.deepEqual(rows.map((r) => r.month), [1, 2]);
  assert.equal(rows[0].floor, 50);
  assert.equal(rows[0].band, 100);
  assert.equal(rows[0].baseP50, 70);
  assert.equal(rows[1].baseP50, null);
});

test("fanSummary describes the last month", () => {
  assert.match(fanSummary(fan), /After 2 months.*₹100 and ₹400.*₹200/);
  assert.equal(fanSummary([]), "No simulated paths.");
});

test("clampReturn bounds to 0-30 and rejects blanks", () => {
  assert.equal(clampReturn("12"), 12);
  assert.equal(clampReturn("45"), 30);
  assert.equal(clampReturn("-2"), 0);
  assert.equal(clampReturn(""), null);
  assert.equal(clampReturn("abc"), null);
});

test("goalOddsText reports unreachable months plainly", () => {
  const t = goalOddsText({ goalName: "Trip", targetDate: "2027-04-30", probabilityByTargetDate: 0.64, baselineProbabilityByTargetDate: 0.5, reachMonth: { p10: 3, p50: 6, p90: null }, baselineReachMonth: { p10: null, p50: null, p90: null } });
  assert.match(t.headline, /64%/);
  assert.match(t.reach, /not within the horizon/);
});

test("monteCarloEvidence reports volatility source and assumption", () => {
  const mc = { fan: [], baselineFan: [], goal: null, flexibility: {}, baselineFlexibility: {}, evidence: { method: "block-bootstrap", paths: 2000, seed: 1, horizonMonths: 12, blockLength: 3, residualMonths: 2, window: ["2026-08-01", "2026-09-30"], baseMonthlyNet: 5000, ownMonthlySd: null, priorMonthlySd: 9000, monthlySd: 9000, ownWeight: 0.25, volatilitySource: "PRIOR", note: "n." } };
  const e = monteCarloEvidence(mc, { annualReturnPercent: 8, annualReturnSource: "DEFAULT", label: "" });
  assert.ok(e.inputs.some((i) => i.label === "Return assumption" && /default/.test(i.value)));
  assert.ok(e.inputs.some((i) => /typical spread/.test(i.value)));
  assert.equal(e.inputs.some((i) => i.label === "Your own monthly spread"), false);
});
