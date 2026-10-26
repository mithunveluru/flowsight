import { test } from "node:test";
import assert from "node:assert/strict";
import { currencyOptions, currencySymbol, formatMoney, normalizeCurrency, parseAmount } from "../currency.ts";

test("currency codes are normalized and junk is rejected", () => {
  assert.equal(normalizeCurrency(" usd "), "USD");
  assert.equal(normalizeCurrency("₹"), null);
  assert.equal(normalizeCurrency("RUPEES"), null);
  assert.equal(normalizeCurrency(null), null);
});

test("picker options lead with an uncommon detected code and never repeat one", () => {
  const opts = currencyOptions("qar", "INR", null);
  assert.equal(opts[0], "QAR");
  assert.equal(opts.filter((c) => c === "INR").length, 1);
  assert.equal(currencyOptions()[0], "INR");
});

test("money is formatted in the transaction's own currency", () => {
  assert.equal(formatMoney(1234.5, "INR"), "₹1,234.50");
  assert.equal(formatMoney(12, "USD"), "$12.00");
  assert.match(formatMoney(40, "AED"), /40\.00/);
  assert.equal(formatMoney(5, null), "5.00");
  assert.equal(formatMoney(null, "INR"), "");
  assert.equal(currencySymbol("INR"), "₹");
  assert.equal(currencySymbol("₹"), "");
});

test("typed amounts accept grouping and reject anything else", () => {
  assert.equal(parseAmount("1,234.50"), 1234.5);
  assert.equal(parseAmount(" 99 "), 99);
  assert.equal(parseAmount(".5"), 0.5);
  assert.equal(parseAmount("12.34567"), null);
  assert.equal(parseAmount("0"), null);
  assert.equal(parseAmount("-4"), null);
  assert.equal(parseAmount("4e2"), null);
  assert.equal(parseAmount(""), null);
  assert.equal(parseAmount(315), 315);
});
