import { test } from "node:test";
import assert from "node:assert/strict";
import { activeRailKey, moreItems, navSections, railItems } from "../nav.ts";
import { centerScrollLeft } from "../rail-core.ts";

test("the rail shows the ten primary sections in order, labelled for the product", () => {
  assert.deepEqual(
    railItems.map((i) => [i.tab, i.href]),
    [
      ["Overview", "/dashboard"],
      ["Transactions", "/dashboard/transactions"],
      ["Receipts", "/dashboard/receipts"],
      ["Insights", "/dashboard/habits"],
      ["Leaks", "/dashboard/drains"],
      ["Recurring", "/dashboard/subscriptions"],
      ["Simulator", "/dashboard/what-if"],
      ["Budgets", "/dashboard/budgets"],
      ["Goals", "/dashboard/goals"],
      ["Reports", "/dashboard/reports"],
    ]
  );
});

test("every navigation route is on the rail or under More", () => {
  const reachable = new Set([...railItems, ...moreItems].map((i) => i.href));
  for (const item of navSections.flatMap((s) => s.items)) assert.ok(reachable.has(item.href), item.href);
  assert.ok(reachable.has("/dashboard/settings"));
  assert.equal(reachable.size, railItems.length + moreItems.length);
});

test("active tab follows the route, including nested pages", () => {
  assert.equal(activeRailKey("/dashboard"), "/dashboard");
  assert.equal(activeRailKey("/dashboard/receipts/abc/review"), "/dashboard/receipts");
  assert.equal(activeRailKey("/dashboard/reports/monthly"), "/dashboard/reports");
  assert.equal(activeRailKey("/dashboard/what-if"), "/dashboard/what-if");
});

test("pages under More mark More active; unknown pages mark nothing", () => {
  assert.equal(activeRailKey("/dashboard/trends"), "more");
  assert.equal(activeRailKey("/dashboard/capture/import"), "more");
  assert.equal(activeRailKey("/dashboard/settings"), "more");
  assert.equal(activeRailKey("/dashboard/welcome"), null);
  assert.equal(activeRailKey(null), null);
});

test("the active tab is centred in the scroller within its bounds", () => {
  assert.equal(centerScrollLeft(500, 100, 300, 1200), 400);
  assert.equal(centerScrollLeft(10, 80, 300, 1200), 0);
  assert.equal(centerScrollLeft(1150, 80, 300, 1200), 900);
  assert.equal(centerScrollLeft(0, 80, 400, 300), 0);
});
