import { test } from "node:test";
import assert from "node:assert/strict";
import { announcement, askErrorMessage, citationEvidence, fallbackNote, formatFact, splitAnswer } from "../logic.ts";

const cites = [{ id: "t1", tool: "spending_summary", title: "Spending summary", facts: { total_spend: 45230, transaction_count: 61, period: "LAST_MONTH" } }];

test("splitAnswer turns known markers into citations and leaves unknown ones", () => {
  const s = splitAnswer("You spent 45,230 [t1] and [t9] more.", cites);
  assert.deepEqual(s.map((x) => x.kind), ["text", "cite", "text"]);
  assert.equal(s[1].id, "t1");
  assert.match(s[2].text, /\[t9\]/);
});

test("splitAnswer returns the whole text without citations", () => {
  assert.deepEqual(splitAnswer("Hello", []), [{ kind: "text", text: "Hello" }]);
});

test("formatFact marks money keys only", () => {
  assert.equal(formatFact("total_spend", 45230), "₹45,230");
  assert.equal(formatFact("transaction_count", 61), "61");
  assert.equal(formatFact("period", "LAST_MONTH"), "LAST_MONTH");
});

test("citationEvidence lists every fact once", () => {
  const e = citationEvidence(cites[0]);
  assert.equal(e.inputs.length, 3);
  assert.equal(e.inputs[0].label, "Total spend");
});

test("fallbackNote and announcement", () => {
  assert.match(fallbackNote({ fallbackReason: "llm_timeout" }), /too long/);
  assert.equal(fallbackNote({ fallbackReason: null }), null);
  assert.equal(announcement({ answered: true, answer: "Spent 5 [t1]." }), "Answer: Spent 5.");
  assert.match(announcement({ answered: false, answer: "No." }), /^Could not answer/);
});

test("askErrorMessage maps 429", () => {
  assert.match(askErrorMessage({ status: 429 }), /this hour/);
  assert.match(askErrorMessage(new Error("x")), /could not be reached/);
});
