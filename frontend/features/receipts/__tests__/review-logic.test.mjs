import { test } from "node:test";
import assert from "node:assert/strict";
import {
  amountChoices,
  askedSummary,
  describeAmountBasis,
  describeDateBasis,
  fieldsToAsk,
  imageRetention,
  initialValues,
  planReview,
  receiptStage,
  selectedAmount,
  sortInbox,
  swapDayMonth,
  validateReview,
} from "../review-logic.ts";

const field = (decision, confidence = 0.5, basis = null) => ({ decision, confidence, basis });

const extraction = (over = {}) => ({
  amount: 315,
  date: "2026-10-14",
  merchant: "Cafe Aroma",
  merchantRaw: "CAFE AROMA",
  merchantAddress: null,
  currency: "INR",
  successful: true,
  rawText: null,
  lineItems: [],
  confidence: 0.97,
  requiresConfirmation: false,
  fields: {
    merchant: field("ACCEPT", 0.95),
    amount: field("ACCEPT", 0.97, "arithmetic+total-label"),
    date: field("ACCEPT", 0.95, "labelled"),
    currency: field("ACCEPT", 0.99),
  },
  ...over,
});

test("each backend decision maps to one way of showing the field", () => {
  const plan = planReview(
    extraction({
      fields: {
        merchant: field("ACCEPT", 0.95),
        amount: field("ACCEPT_WITH_HIGHLIGHT", 0.7, "total-label"),
        date: field("ASK_USER", 0.45, "day-month-order-unknown"),
        currency: field("ACCEPT", 0.99),
      },
    })
  );
  assert.equal(plan.merchant.mode, "plain");
  assert.equal(plan.amount.mode, "highlight");
  assert.equal(plan.amount.confidence, 0.7);
  assert.equal(plan.date.mode, "ask");
  assert.equal(plan.date.basis, "day-month-order-unknown");
  assert.deepEqual(fieldsToAsk(plan), ["date"]);
});

test("a missing value is always asked for, whatever the decision says", () => {
  const plan = planReview(extraction({ currency: null, date: null }));
  assert.equal(plan.currency.mode, "ask");
  assert.equal(plan.date.mode, "ask");
  assert.deepEqual(fieldsToAsk(plan), ["date", "currency"]);
});

test("receipts read before per-field decisions fall back to the amount flag", () => {
  const legacy = extraction({ fields: undefined, requiresConfirmation: true });
  const plan = planReview(legacy);
  assert.equal(plan.amount.mode, "highlight");
  assert.equal(plan.merchant.mode, "plain");
  assert.equal(planReview(null).amount.mode, "ask");
});

test("asked fields start empty; accepted ones are prefilled", () => {
  const x = extraction({
    fields: { ...extraction().fields, amount: field("ASK_USER", 0.3, "conflict:total-label") },
  });
  const v = initialValues({ extraction: x, currency: null }, planReview(x));
  assert.equal(v.amount, "");
  assert.equal(v.merchant, "Cafe Aroma");
  assert.equal(v.date, "2026-10-14");
  assert.equal(v.currency, "INR");
});

test("amount choices keep the backend order, add its pick, and drop duplicates", () => {
  const x = extraction({
    amount: 300,
    fields: { amount: field("ASK_USER", 0.4, "derived") },
    amountCandidates: [
      { amount: 315, label: "TOTAL", basis: "conflict:total-label" },
      { amount: 315.0, label: "Grand total", basis: "grand-total-label" },
      { amount: 0, label: "Change", basis: null },
    ],
  });
  const choices = amountChoices(x);
  assert.deepEqual(choices.map((c) => c.amount), [300, 315]);
  assert.equal(choices[0].description, "Cash paid minus change");
  assert.equal(choices[1].label, "TOTAL");
});

test("candidate selection resolves to a number only when valid", () => {
  const choices = [{ amount: 315, label: null, basis: null, description: "" }];
  const parse = (t) => (/^\d+(\.\d+)?$/.test(t) ? Number(t) : null);
  assert.equal(selectedAmount({ kind: "candidate", index: 0 }, choices, parse), 315);
  assert.equal(selectedAmount({ kind: "candidate", index: 3 }, choices, parse), null);
  assert.equal(selectedAmount({ kind: "other", text: "42.5" }, choices, parse), 42.5);
  assert.equal(selectedAmount({ kind: "other", text: "4x" }, choices, parse), null);
  assert.equal(selectedAmount(null, choices, parse), null);
});

test("amount basis reads as plain language", () => {
  assert.equal(describeAmountBasis("conflict:arithmetic(items)+total-label+2-passes"),
    "Two readings of the total disagree · Adds up with the items and tax · Printed next to a total · Same on 2 reads");
  assert.equal(describeAmountBasis("payment"), "Amount paid");
  assert.equal(describeAmountBasis("llm+grand-total-label"), "Printed as the grand total · Read by the vision model");
  assert.equal(describeAmountBasis(null), "Read from the receipt");
});

test("date notes say which order was assumed, and swapping is offered only when it makes a date", () => {
  assert.equal(describeDateBasis("day-month-order-from-USD"), "Read as month/day because the receipt is in USD.");
  assert.equal(describeDateBasis("day-month-order-from-INR"), "Read as day/month because the receipt is in INR.");
  assert.equal(describeDateBasis("labelled"), null);
  assert.equal(swapDayMonth("2026-03-04"), "2026-04-03");
  assert.equal(swapDayMonth("2026-03-03"), null);
  assert.equal(swapDayMonth("2026-03-14"), null);
  assert.equal(swapDayMonth(null), null);
});

test("validation names every missing field and rejects future dates", () => {
  assert.deepEqual(Object.keys(validateReview({ merchant: " ", amount: null, date: "", currency: null }, "2026-10-20")),
    ["merchant", "amount", "date", "currency"]);
  assert.equal(validateReview({ merchant: "A", amount: 1, date: "2026-10-21", currency: "INR" }, "2026-10-20").date,
    "The date cannot be in the future.");
  assert.deepEqual(validateReview({ merchant: "A", amount: 1, date: "2026-10-20", currency: "INR" }, "2026-10-20"), {});
});

test("inbox puts drafts first, then unreadable, then done, newest first within each", () => {
  const r = (id, status, createdAt, transaction = null) => ({ id, status, createdAt, transaction });
  const sorted = sortInbox([
    r("done", "COMPLETED", "2026-10-19T10:00:00Z", { id: "t" }),
    r("old-draft", "COMPLETED", "2026-10-10T10:00:00Z"),
    r("failed", "FAILED", "2026-10-18T10:00:00Z"),
    r("new-draft", "COMPLETED", "2026-10-18T09:00:00Z"),
    r("busy", "PROCESSING", "2026-10-20T09:00:00Z"),
  ]);
  assert.deepEqual(sorted.map((x) => x.id), ["new-draft", "old-draft", "failed", "busy", "done"]);
  assert.equal(receiptStage({ status: "FAILED", transaction: { id: "t" } }), "confirmed");
});

test("image retention follows the availability flag and the stored expiry", () => {
  assert.equal(imageRetention({ imageAvailable: true, imageExpiresAt: "2026-10-27T00:00:00Z" }).state, "kept");
  assert.equal(imageRetention({ imageAvailable: false, imageExpiresAt: "2026-10-01T00:00:00Z" }).state, "expired");
  assert.equal(imageRetention({ imageAvailable: false, imageExpiresAt: null }).state, "none");
  assert.equal(imageRetention({}).state, "none");
});

test("askedSummary joins field names for the note above the form", () => {
  assert.equal(askedSummary([]), "");
  assert.equal(askedSummary(["merchant"]), "the merchant");
  assert.equal(askedSummary(["date", "currency"]), "the date and the currency");
  assert.equal(askedSummary(["merchant", "amount", "date"]), "the merchant, the amount and the date");
});
