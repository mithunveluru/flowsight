import { test } from "node:test";
import assert from "node:assert/strict";
import {
  advanceVisit,
  attentionItems,
  budgetLevel,
  changesSince,
  checklist,
  countDrafts,
  drainKey,
  historyDays,
  rankAttention,
  shouldShowWelcome,
  takeSnapshot,
  typicalMonthlySpend,
  visibleDrains,
} from "../logic.ts";

const HOUR = 60 * 60 * 1000;

test("first run: welcome only for a known-empty account that has not skipped it", () => {
  assert.equal(shouldShowWelcome(0, false), true);
  assert.equal(shouldShowWelcome(0, true), false);
  assert.equal(shouldShowWelcome(12, false), false);
  assert.equal(shouldShowWelcome(undefined, false), false);
});

test("history length counts both ends and is null without data", () => {
  assert.equal(historyDays("2026-10-08", new Date(2026, 9, 16)), 9);
  assert.equal(historyDays("2026-10-16", new Date(2026, 9, 16)), 1);
  assert.equal(historyDays("2026-07-16", new Date(2026, 9, 16)), 93);
  assert.equal(historyDays(null, new Date()), null);
});

test("drafts are completed scans without a confirmed transaction", () => {
  const receipts = [
    { status: "COMPLETED", transaction: null },
    { status: "COMPLETED", transaction: { id: "t" } },
    { status: "FAILED", transaction: null },
    { status: "PROCESSING", transaction: null },
  ];
  assert.equal(countDrafts(receipts), 1);
});

test("typical spend averages complete months and skips a partial first month", () => {
  const points = [
    { month: "2026-07", label: "Jul 2026", spend: 9000, projected: false },
    { month: "2026-08", label: "Aug 2026", spend: 40000, projected: false },
    { month: "2026-09", label: "Sep 2026", spend: 44000, projected: false },
    { month: "2026-10", label: "Oct 2026", spend: 12000, projected: false },
    { month: "2026-11", label: "Nov 2026", spend: 41000, projected: true },
  ];
  assert.deepEqual(typicalMonthlySpend(points, "2026-10", "2026-07-16"), {
    average: 42000,
    months: [{ label: "Aug 2026", spend: 40000 }, { label: "Sep 2026", spend: 44000 }],
  });
  assert.equal(typicalMonthlySpend(points, "2026-10", "2026-07-01").months.length, 3);
  assert.equal(typicalMonthlySpend(points.slice(3), "2026-10", null), null);
});

test("a new visit starts only after a gap; reloads keep the same baseline", () => {
  const snap = (at) => ({ at, recurringIds: [], creepMerchants: [], budgetLevels: {} });
  const first = advanceVisit(null, snap(0));
  assert.equal(first.baseline, null);

  const reload = advanceVisit(first, snap(10 * 60 * 1000));
  assert.equal(reload.baseline, null);

  const nextDay = advanceVisit(reload, snap(24 * HOUR));
  assert.equal(nextDay.baseline.at, 10 * 60 * 1000);

  const sameVisit = advanceVisit(nextDay, snap(24 * HOUR + 5 * 60 * 1000));
  assert.equal(sameVisit.baseline.at, 10 * 60 * 1000);
});

test("changes since the last visit cover transactions, receipts, subscriptions, prices and budgets", () => {
  const baseline = takeSnapshot({
    now: Date.parse("2026-10-10T00:00:00Z"),
    recurring: [{ id: "r1" }],
    leaks: [],
    budgets: [{ id: "b1", percentUsed: 50 }, { id: "b2", percentUsed: 85 }],
  });
  const changes = changesSince(baseline, {
    transactions: [
      { createdAt: "2026-10-12T09:00:00Z", amount: 250, type: "DEBIT" },
      { createdAt: "2026-10-12T10:00:00Z", amount: 92000, type: "CREDIT" },
      { createdAt: "2026-10-01T10:00:00Z", amount: 99, type: "DEBIT" },
    ],
    receipts: [{ transaction: { createdAt: "2026-10-11T08:00:00Z" } }, { transaction: null }],
    recurring: [{ id: "r1", merchant: "Spotify" }, { id: "r2", merchant: "Netflix" }],
    leaks: [{ type: "SUBSCRIPTION_CREEP", items: [{ merchant: "Netflix", detail: "₹499 → ₹649 (+30%)" }] }],
    budgets: [
      { id: "b1", percentUsed: 82, categoryDisplayName: "Groceries" },
      { id: "b2", percentUsed: 90, categoryDisplayName: "Dining" },
    ],
  });
  assert.deepEqual(changes.map((c) => [c.label, c.detail]), [
    ["2 new transactions", "₹250 spent"],
    ["1 receipt confirmed", undefined],
    ["1 new subscription detected", "Netflix"],
    ["Price increase: Netflix", "₹499 → ₹649 (+30%)"],
    ["Groceries budget passed 80%", "82% used"],
  ]);
});

test("a full capped page of new transactions is reported as a lower bound", () => {
  const baseline = { at: 0, recurringIds: [], creepMerchants: [], budgetLevels: {} };
  const tx = Array.from({ length: 3 }, () => ({ createdAt: "2026-10-12T09:00:00Z", amount: 1, type: "DEBIT" }));
  const [change] = changesSince(baseline, { transactions: tx, transactionsCapped: true, receipts: [], recurring: [], leaks: [], budgets: [] });
  assert.equal(change.label, "3+ new transactions");
  assert.equal(budgetLevel(79.9), 0);
  assert.equal(budgetLevel(100), 100);
});

test("attention ranks by severity, then rupee impact, and keeps five", () => {
  const items = attentionItems({
    receipts: [{ status: "COMPLETED", transaction: null, extraction: { amount: 420 } }],
    uncategorized: 3,
    budgets: [
      { id: "food", percentUsed: 112, categoryDisplayName: "Food", currentSpend: 11200, monthlyLimit: 10000 },
      { id: "fun", percentUsed: 92, categoryDisplayName: "Fun", currentSpend: 4600, monthlyLimit: 5000 },
      { id: "ok", percentUsed: 40, categoryDisplayName: "Travel", currentSpend: 400, monthlyLimit: 1000 },
    ],
    recurring: [
      { id: "r1", merchant: "Gym", status: "OVERDUE", estimatedAmount: 1500 },
      { id: "r2", merchant: "Music", status: "ACTIVE", estimatedAmount: 119 },
    ],
    goals: [
      { id: "g1", name: "Trip", status: "ACTIVE", paceStatus: "BEHIND", remaining: 20000 },
      { id: "g2", name: "Laptop", status: "ACTIVE", paceStatus: "ON_PACE", remaining: 5000 },
    ],
  });
  assert.deepEqual(items.map((i) => i.id), ["budget:food", "recurring:r1", "drafts", "budget:fun", "goal:g1"]);
  assert.equal(items.length, 5);

  const tie = rankAttention([
    { id: "a", severity: "LOW", impact: 1 },
    { id: "b", severity: "LOW", impact: 9 },
    { id: "c", severity: "HIGH", impact: 0 },
  ]);
  assert.deepEqual(tie.map((i) => i.id), ["c", "b", "a"]);
  assert.deepEqual(attentionItems({}), []);
});

test("drains show the three largest that the user has not hidden", () => {
  const leak = (type, merchant, monthlyImpact) => ({ type, items: [{ merchant }], monthlyImpact });
  const leaks = [
    leak("BANK_FEES", "Bank", 50),
    leak("SUBSCRIPTION_CREEP", "Netflix", 150),
    leak("HIGH_FREQUENCY_SMALL_SPEND", "Coffee", 3300),
    leak("DUPLICATE_SUBSCRIPTIONS", "Apple Music", 99),
  ];
  const hidden = [drainKey(leaks[2])];
  assert.deepEqual(visibleDrains(leaks, hidden).map((l) => l.items[0].merchant), ["Netflix", "Apple Music", "Bank"]);
  assert.deepEqual(visibleDrains(leaks, []).map((l) => l.monthlyImpact), [3300, 150, 99]);
});

test("checklist marks each step from real account state", () => {
  const done = (input) => checklist(input).filter((i) => i.done).map((i) => i.id);
  assert.deepEqual(done({ transactionCount: 0, uncategorized: 0, budgetCount: 0, goalCount: 0 }), []);
  assert.deepEqual(done({ transactionCount: 140, uncategorized: 2, budgetCount: 1, goalCount: 0 }), ["data", "budget"]);
  assert.deepEqual(done({ transactionCount: 140, uncategorized: 0, budgetCount: 1, goalCount: 2 }), ["data", "categorize", "budget", "goal"]);
});
