import { test } from "node:test";
import assert from "node:assert/strict";
import { nextSort, selectionState, sortRows, toggleId } from "../table-core.ts";

test("nextSort cycles asc, desc, unsorted and resets on a new key", () => {
  let s = nextSort(null, "amount");
  assert.deepEqual(s, { key: "amount", direction: "asc" });
  s = nextSort(s, "amount");
  assert.deepEqual(s, { key: "amount", direction: "desc" });
  assert.equal(nextSort(s, "amount"), null);
  assert.deepEqual(nextSort(s, "date"), { key: "date", direction: "asc" });
});

test("sortRows is stable, numeric-aware, and keeps missing values last", () => {
  const rows = [
    { id: 1, v: 10 },
    { id: 2, v: null },
    { id: 3, v: 2 },
    { id: 4, v: 10 },
    { id: 5, v: Number.NaN },
  ];
  assert.deepEqual(sortRows(rows, (r) => r.v, "asc").map((r) => r.id), [3, 1, 4, 2, 5]);
  assert.deepEqual(sortRows(rows, (r) => r.v, "desc").map((r) => r.id), [1, 4, 3, 2, 5]);
  const names = [{ n: "item 10" }, { n: "Item 2" }, { n: "apple" }];
  assert.deepEqual(sortRows(names, (r) => r.n, "asc").map((r) => r.n), ["apple", "Item 2", "item 10"]);
  const dates = [{ d: new Date("2026-03-01") }, { d: new Date("2026-01-01") }];
  assert.deepEqual(sortRows(dates, (r) => r.d, "asc").map((r) => r.d.getMonth()), [0, 2]);
});

test("selection helpers report none, some, all", () => {
  const ids = ["a", "b", "c"];
  let sel = new Set();
  assert.equal(selectionState(sel, ids), "none");
  sel = toggleId(sel, "a");
  assert.equal(selectionState(sel, ids), "some");
  sel = toggleId(toggleId(sel, "b"), "c");
  assert.equal(selectionState(sel, ids), "all");
  assert.equal(selectionState(toggleId(sel, "b"), ids), "some");
  assert.equal(selectionState(new Set(["zzz"]), ids), "none");
  assert.equal(selectionState(new Set(), []), "none");
});
