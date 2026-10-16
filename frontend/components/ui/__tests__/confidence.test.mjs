import { test } from "node:test";
import assert from "node:assert/strict";
import { confidenceLevel, formatProbability } from "../confidence.ts";

test("confidenceLevel buckets a probability with inclusive lower bounds", () => {
  assert.equal(confidenceLevel(0.8), "high");
  assert.equal(confidenceLevel(0.79), "medium");
  assert.equal(confidenceLevel(0.5), "medium");
  assert.equal(confidenceLevel(0.49), "low");
  assert.equal(confidenceLevel(0.65, { high: 0.65, medium: 0.4 }), "high");
  assert.equal(confidenceLevel(0.39, { high: 0.65, medium: 0.4 }), "low");
});

test("confidenceLevel rejects missing values and clamps float noise", () => {
  assert.equal(confidenceLevel(null), null);
  assert.equal(confidenceLevel(undefined), null);
  assert.equal(confidenceLevel(Number.NaN), null);
  assert.equal(confidenceLevel(1.0000001), "high");
  assert.equal(confidenceLevel(-0.01), "low");
  assert.equal(formatProbability(0.926), "93%");
  assert.equal(formatProbability(1.2), "100%");
});
