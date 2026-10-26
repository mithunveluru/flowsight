import { test } from "node:test";
import assert from "node:assert/strict";
import { assessReach } from "../reach.ts";

const pt = (point, lo, hi, partial = false) => ({ month: "2026-11", partial, point, interval: { lower80: lo, upper80: hi, lower95: lo, upper95: hi } });
const net = (...pts) => ({ series: "net", label: "Net", points: pts, evidence: {} });
const goal = (remaining, daysRemaining, status = "ACTIVE") => ({ remaining, daysRemaining, status });

test("reachable when even the low end covers the need", () => {
  const a = assessReach(goal(30000, 61), net(pt(0, 0, 0, true), pt(20000, 16000, 24000), pt(20000, 16000, 24000)));
  assert.equal(a.reach, "reachable");
  assert.equal(a.months, 3);
  assert.equal(a.forecastMonths, 2);
  assert.equal(a.requiredPerMonth, 10000);
});

test("unreachable when even the high end falls short", () => {
  const a = assessReach(goal(90000, 30), net(pt(5000, -2000, 9000)));
  assert.equal(a.reach, "unreachable");
});

test("likely and unlikely split on the expected value", () => {
  assert.equal(assessReach(goal(10000, 30), net(pt(12000, 2000, 20000))).reach, "likely");
  assert.equal(assessReach(goal(10000, 30), net(pt(8000, 2000, 20000))).reach, "unlikely");
});

test("nothing to assess for finished goals or missing forecasts", () => {
  assert.equal(assessReach(goal(0, 30), net(pt(1, 1, 1))), null);
  assert.equal(assessReach(goal(100, 30, "COMPLETED"), net(pt(1, 1, 1))), null);
  assert.equal(assessReach(goal(100, 30), undefined), null);
  assert.equal(assessReach(goal(100, 30), net(pt(1, 1, 1, true))), null);
});
