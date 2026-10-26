import { test } from "node:test";
import assert from "node:assert/strict";
import { markNewTransaction, takeNewTransaction } from "../new-row.ts";

test("a marked transaction is reported once", () => {
  markNewTransaction("a", 1000);
  assert.equal(takeNewTransaction("b", 1000), false);
  assert.equal(takeNewTransaction("a", 2000), true);
  assert.equal(takeNewTransaction("a", 2000), false);
});

test("a stale mark is spent without flashing", () => {
  markNewTransaction("a", 0);
  assert.equal(takeNewTransaction("a", 6 * 60 * 1000), false);
  assert.equal(takeNewTransaction("a", 6 * 60 * 1000), false);
});
