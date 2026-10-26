import { test } from "node:test";
import assert from "node:assert/strict";
import {
  categorySignal,
  collectSuggestions,
  filterRows,
  otherAlternatives,
  planBulk,
  reviewChoices,
  runBulk,
  shouldLoadMore,
  windowRange,
} from "../table-logic.ts";

const tx = (over = {}) => ({
  id: "1",
  category: "FOOD_DINING",
  categorySource: "MODEL",
  confidenceScore: 0.87,
  reviewed: false,
  merchant: "Zomato",
  description: "UPI/ZOMATO/123",
  notes: null,
  source: "CSV",
  categoryAlternatives: [
    { category: "FOOD_DINING", probability: 0.87 },
    { category: "GROCERIES", probability: 0.08 },
    { category: "SHOPPING", probability: 0.03 },
  ],
  ...over,
});

test("category signal follows the source: model and personal show a probability, rules do not, the user's choice shows nothing", () => {
  assert.deepEqual(categorySignal(tx()), { kind: "model", confidence: 0.87 });
  assert.deepEqual(categorySignal(tx({ categorySource: "PERSONAL", confidenceScore: 0.95 })), { kind: "personal", confidence: 0.95 });
  assert.deepEqual(categorySignal(tx({ categorySource: "RULES", confidenceScore: 0.25 })), { kind: "rules" });
  assert.deepEqual(categorySignal(tx({ categorySource: "USER" })), { kind: "user" });
  assert.deepEqual(categorySignal(tx({ category: "UNCATEGORIZED" })), { kind: "none" });
  assert.deepEqual(categorySignal(tx({ categorySource: null, reviewed: true })), { kind: "user" });
  assert.deepEqual(categorySignal(tx({ categorySource: undefined })), { kind: "rules" });
});

test("alternatives leave out the current category; review choices are the top three", () => {
  assert.deepEqual(otherAlternatives(tx()).map((a) => a.category), ["GROCERIES", "SHOPPING"]);
  assert.deepEqual(reviewChoices(tx()).map((c) => c.category), ["FOOD_DINING", "GROCERIES", "SHOPPING"]);
  assert.deepEqual(reviewChoices(tx({ categoryAlternatives: null, category: "TRAVEL" })), [{ category: "TRAVEL", probability: null }]);
  assert.deepEqual(reviewChoices(tx({ categoryAlternatives: null, category: "UNCATEGORIZED" })), []);
});

test("client filters match text, source and review state together", () => {
  const rows = [
    tx({ id: "a" }),
    tx({ id: "b", merchant: "Uber", description: "Ride", source: "MANUAL", reviewed: true }),
    tx({ id: "c", merchant: null, description: "Coffee", notes: "with Zomato coupon", source: "OCR" }),
  ];
  const ids = (f) => filterRows(rows, { query: "", source: null, needsReview: false, ...f }).map((r) => r.id);
  assert.deepEqual(ids({ query: "zomato" }), ["a", "c"]);
  assert.deepEqual(ids({ source: "OCR" }), ["c"]);
  assert.deepEqual(ids({ needsReview: true }), ["a", "c"]);
  assert.deepEqual(ids({}), ["a", "b", "c"]);
});

test("bulk actions only target selected rows they would change", () => {
  const rows = [
    tx({ id: "a", category: "TRAVEL", categorySource: "USER", reviewed: true }),
    tx({ id: "b", category: "TRAVEL", categorySource: "MODEL" }),
    tx({ id: "c" }),
    tx({ id: "d" }),
  ];
  const sel = new Set(["a", "b", "c"]);
  assert.deepEqual(planBulk({ type: "recategorize", category: "TRAVEL" }, rows, sel), { targets: ["b", "c"], unchanged: 1 });
  assert.deepEqual(planBulk({ type: "review" }, rows, sel), { targets: ["b", "c"], unchanged: 1 });
  assert.deepEqual(planBulk({ type: "delete" }, rows, sel), { targets: ["a", "b", "c"], unchanged: 0 });
  assert.deepEqual(planBulk({ type: "delete" }, rows, new Set(["gone"])), { targets: [], unchanged: 0 });
});

test("bulk runs with bounded concurrency and collects failures", async () => {
  let active = 0;
  let peak = 0;
  const out = await runBulk(["1", "2", "3", "4", "5", "6"], async (id) => {
    active++;
    peak = Math.max(peak, active);
    await new Promise((r) => setTimeout(r, 5));
    active--;
    if (id === "4") throw new Error("nope");
    return id + "!";
  }, 2);
  assert.equal(peak, 2);
  assert.deepEqual(out.succeeded.map((s) => s.id).sort(), ["1", "2", "3", "5", "6"]);
  assert.deepEqual(out.failed.map((f) => f.id), ["4"]);
  assert.deepEqual(await runBulk([], async () => 1), { succeeded: [], failed: [] });
});

test("rule suggestions are kept once per rule, latest wins", () => {
  const s = (ruleId, similarCount) => ({ ruleId, canonicalName: "Zomato", category: "FOOD_DINING", similarCount });
  const merged = collectSuggestions([s("r1", 3)], [s("r2", 1), null, s("r1", 5), undefined]);
  assert.deepEqual(merged.map((x) => [x.ruleId, x.similarCount]), [["r1", 5], ["r2", 1]]);
});

test("the window covers the viewport plus overscan and pads the rest", () => {
  assert.deepEqual(windowRange({ scrollTop: 0, viewportHeight: 600, rowHeight: 60, count: 1000, overscan: 5 }),
    { start: 0, end: 16, padTop: 0, padBottom: 984 * 60 });
  const mid = windowRange({ scrollTop: 6000, viewportHeight: 600, rowHeight: 60, count: 1000, overscan: 5 });
  assert.deepEqual([mid.start, mid.end], [95, 116]);
  assert.equal(mid.padTop + (mid.end - mid.start) * 60 + mid.padBottom, 1000 * 60);
  const end = windowRange({ scrollTop: 1e9, viewportHeight: 600, rowHeight: 60, count: 30, overscan: 5 });
  assert.deepEqual([end.start, end.end, end.padBottom], [15, 30, 0]);
  assert.deepEqual(windowRange({ scrollTop: 0, viewportHeight: 600, rowHeight: 60, count: 0 }), { start: 0, end: 0, padTop: 0, padBottom: 0 });
});

test("the next page loads when the window nears the loaded end", () => {
  assert.equal(shouldLoadMore(85, 100, true), true);
  assert.equal(shouldLoadMore(50, 100, true), false);
  assert.equal(shouldLoadMore(100, 100, false), false);
});
