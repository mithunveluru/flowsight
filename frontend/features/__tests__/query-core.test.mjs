import { test } from "node:test";
import assert from "node:assert/strict";
import { isoDate, monthToDate, queryKeys, shouldRetryQuery } from "../query-core.ts";

test("retries a network or server failure once and never a client error", () => {
  assert.equal(shouldRetryQuery(0, new TypeError("Failed to fetch")), true);
  assert.equal(shouldRetryQuery(0, { status: 503 }), true);
  assert.equal(shouldRetryQuery(1, { status: 503 }), false);
  assert.equal(shouldRetryQuery(0, { status: 404 }), false);
  assert.equal(shouldRetryQuery(0, { status: 429 }), false);
  assert.equal(shouldRetryQuery(0, null), true);
});

test("query keys separate parameters so caches never mix", () => {
  assert.notDeepEqual(queryKeys.analytics.overview("2026-10-01", "2026-10-16"), queryKeys.analytics.overview("2026-09-01", "2026-09-30"));
  assert.deepEqual(queryKeys.receipts.list(0, 50), ["receipts", "list", 0, 50]);
  assert.equal(queryKeys.transactions.list({ size: 1 })[0], queryKeys.transactions.all()[0]);
});

test("dates use the viewer's calendar day, not UTC", () => {
  const lateNight = new Date(2026, 9, 16, 23, 50);
  assert.equal(isoDate(lateNight), "2026-10-16");
  assert.equal(isoDate(new Date(2026, 0, 1, 0, 5)), "2026-01-01");
  assert.deepEqual(monthToDate(lateNight), { from: "2026-10-01", to: "2026-10-16" });
});
