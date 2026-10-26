import { test } from "node:test";
import assert from "node:assert/strict";
import { applyFeedback, canAnswer } from "../feedback.ts";

const data = { recommendations: [{ id: "a", title: "A" }, { id: "b", title: "B" }, { title: "legacy" }] };

test("dismissed recommendations disappear, others are untouched", () => {
  const out = applyFeedback(data, "a", "DISMISSED");
  assert.deepEqual(out.recommendations.map((r) => r.title), ["B", "legacy"]);
  assert.equal(data.recommendations.length, 3);
});

test("accepted recommendations stay and are marked", () => {
  const out = applyFeedback(data, "b", "ACCEPTED");
  assert.equal(out.recommendations[1].feedback, "ACCEPTED");
  assert.equal(out.recommendations[0].feedback, undefined);
});

test("only recommendations with an id can be answered", () => {
  assert.equal(canAnswer({ id: "x" }), true);
  assert.equal(canAnswer({}), false);
});
