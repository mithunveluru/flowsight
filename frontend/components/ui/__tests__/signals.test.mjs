import { test } from "node:test";
import assert from "node:assert/strict";
import { formatPercentDelta, trendTone } from "../signals-core.ts";

test("trendTone judges direction against intent", () => {
  assert.equal(trendTone(12, "higher-is-better"), "good");
  assert.equal(trendTone(12, "lower-is-better"), "bad");
  assert.equal(trendTone(-3, "lower-is-better"), "good");
  assert.equal(trendTone(0, "lower-is-better"), "flat");
  assert.equal(trendTone(0.04, "higher-is-better"), "flat");
  assert.equal(trendTone(5, "neutral"), "neutral");
});

test("formatPercentDelta signs the change and collapses noise to 0%", () => {
  assert.equal(formatPercentDelta(12.34), "+12.3%");
  assert.equal(formatPercentDelta(-4), "−4.0%");
  assert.equal(formatPercentDelta(0.01), "0%");
  assert.equal(formatPercentDelta(7, 0), "+7%");
});
