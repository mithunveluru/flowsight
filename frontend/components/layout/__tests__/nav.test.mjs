import { test } from "node:test";
import assert from "node:assert/strict";
import { accentForPath, captureActions, isNavItemActive, locate, navSections } from "../nav.ts";

const shape = navSections.map((s) => [s.section, s.items.map((i) => `${i.label} ${i.href}`)]);

test("navigation groups pages by job, in the approved order", () => {
  assert.deepEqual(shape, [
    [null, ["Today /dashboard"]],
    ["Capture", [
      "Scan receipt /dashboard/capture/receipt",
      "Import statement /dashboard/capture/import",
      "Add manually /dashboard/capture/manual",
      "Receipt inbox /dashboard/receipts",
    ]],
    ["Understand", ["Transactions /dashboard/transactions", "Trends /dashboard/trends", "Habits /dashboard/habits"]],
    ["Act", ["Money drains /dashboard/drains", "Subscriptions /dashboard/subscriptions", "Budgets /dashboard/budgets"]],
    ["Plan", ["Goals /dashboard/goals", "What-if /dashboard/what-if", "Reports /dashboard/reports"]],
  ]);
});

test("labels and destinations are unique", () => {
  const items = navSections.flatMap((s) => s.items);
  assert.equal(new Set(items.map((i) => i.label)).size, items.length);
  assert.equal(new Set(items.map((i) => i.href)).size, items.length);
});

test("capture actions are the three ways in", () => {
  assert.deepEqual(captureActions.map((a) => a.label), ["Scan receipt", "Import statement", "Add manually"]);
});

test("only the matching item is active, including nested pages", () => {
  assert.equal(isNavItemActive("/dashboard", "/dashboard"), true);
  assert.equal(isNavItemActive("/dashboard/trends", "/dashboard"), false);
  assert.equal(isNavItemActive("/dashboard/receipts/abc/review", "/dashboard/receipts"), true);
  assert.equal(isNavItemActive("/dashboard/reports/monthly", "/dashboard/reports"), true);
  assert.equal(isNavItemActive("/dashboard/goalsx", "/dashboard/goals"), false);
  assert.equal(isNavItemActive(null, "/dashboard"), false);

  const activeFor = (path) => navSections.flatMap((s) => s.items).filter((i) => isNavItemActive(path, i.href));
  for (const item of navSections.flatMap((s) => s.items)) {
    assert.deepEqual(activeFor(item.href).map((i) => i.href), [item.href]);
  }
});

test("breadcrumb names the section and page", () => {
  assert.deepEqual(locate("/dashboard/drains"), { section: "Act", label: "Money drains" });
  assert.deepEqual(locate("/dashboard/receipts/1/review"), { section: "Capture", label: "Receipt inbox" });
  assert.deepEqual(locate("/dashboard"), { section: null, label: "Today" });
  assert.deepEqual(locate("/dashboard/settings"), { section: null, label: "Settings" });
  assert.deepEqual(locate("/dashboard/welcome"), { section: null, label: "Welcome" });
  assert.equal(locate("/somewhere"), null);
});

test("section accent follows the route", () => {
  assert.equal(accentForPath("/dashboard/what-if"), "var(--accent-simulate)");
  assert.equal(accentForPath("/dashboard/capture/import"), "var(--accent-receipts)");
  assert.equal(accentForPath("/dashboard"), "var(--accent-overview)");
  assert.equal(accentForPath(null), "var(--brand)");
});
