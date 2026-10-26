import { test } from "node:test";
import assert from "node:assert/strict";
import { paceView } from "../pace.ts";

const budget = (over) => ({ monthlyLimit: 8000, currentSpend: 2816, projectedTotal: 5111, pace: { method: "forecast-remaining", remainingShare: 0.5, lower80: 3161, upper80: 7250, lower95: 0, upper95: 0, evidence: null }, ...over });

test("inside the limit shows no risk", () => {
  const v = paceView(budget());
  assert.equal(v.risk, "none");
  assert.equal(v.limit, 8000);
  assert.equal(v.scale, 8000);
  assert.equal(Math.round(v.limitAt), 100);
});

test("a band crossing the limit is possible, fully above is likely", () => {
  assert.equal(paceView(budget({ monthlyLimit: 5000 })).risk, "possible");
  assert.equal(paceView(budget({ monthlyLimit: 3000 })).risk, "likely");
});

test("rollover raises the limit", () => {
  assert.equal(paceView(budget({ monthlyLimit: 5000, effectiveLimit: 9000 })).risk, "none");
});

test("no pace, no view", () => {
  assert.equal(paceView(budget({ pace: undefined })), null);
  assert.equal(paceView(budget({ pace: { method: "none" } })), null);
});
