import test from "node:test";
import assert from "node:assert/strict";
import { niceTicks, yDomain, linePath, blend, nearestIndex, spreadLabels, donutSlices } from "../chart-core.ts";

test("ticks are round and cover the range", () => {
  const t = niceTicks(0, 87000, 4);
  assert.equal(t[0], 0);
  assert.ok(t.every((v) => v % 10000 === 0 || v % 20000 === 0));
});

test("y domain always contains zero and the data", () => {
  const [lo, hi] = yDomain([120, 340, 95]);
  assert.equal(lo, 0);
  assert.ok(hi >= 340);
  const [nlo] = yDomain([-50, 20]);
  assert.ok(nlo <= -50);
});

test("a NaN point breaks the line", () => {
  assert.equal(linePath([0, 1, 2], [5, NaN, 7]), "M0.00 5.00M2.00 7.00");
});

test("blend moves linearly and snaps NaN to the target", () => {
  assert.deepEqual(blend([0, 10], [10, 20], 0.5), [5, 15]);
  assert.deepEqual(blend([NaN], [4], 0.5), [4]);
});

test("nearest index clamps to the plot", () => {
  assert.equal(nearestIndex(-20, 0, 100, 5), 0);
  assert.equal(nearestIndex(60, 0, 100, 5), 2);
  assert.equal(nearestIndex(500, 0, 100, 5), 4);
});

test("spread keeps labels apart and in order", () => {
  const out = spreadLabels([10, 12, 40], 14, 0, 100);
  assert.ok(out[1] - out[0] >= 14);
  assert.equal(out[2], 40);
});

test("donut slices add up to the circumference minus gaps", () => {
  const s = donutSlices([1, 1, 2], 100, 2);
  assert.equal(s.reduce((a, x) => a + x.share, 0), 1);
  assert.equal(s[2].length, 48);
});
