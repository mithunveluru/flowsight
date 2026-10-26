import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  DURATION_MS,
  EASE_EXIT,
  EASE_STANDARD,
  TAB_SPRING,
  countUpValue,
  drawStart,
  drift,
  enterTransition,
} from "../tokens.ts";

const css = readFileSync(new URL("../../../app/tokens.css", import.meta.url), "utf8");
const cssVar = (name) => css.match(new RegExp(`--${name}:\\s*([^;]+);`))?.[1].trim();

test("css durations and easings equal the motion tokens", () => {
  assert.equal(cssVar("duration-fast"), `${DURATION_MS.fast}ms`);
  assert.equal(cssVar("duration-base"), `${DURATION_MS.base}ms`);
  assert.equal(cssVar("duration-slow"), `${DURATION_MS.slow}ms`);
  assert.equal(cssVar("duration-reveal"), `${DURATION_MS.reveal}ms`);
  assert.equal(cssVar("duration-draw"), `${DURATION_MS.draw}ms`);
  assert.equal(cssVar("ease-standard"), `cubic-bezier(${EASE_STANDARD.join(", ")})`);
  assert.equal(cssVar("ease-exit"), `cubic-bezier(${EASE_EXIT.join(", ")})`);
});

test("the tab spring is critically damped", () => {
  const critical = 2 * Math.sqrt(TAB_SPRING.stiffness * TAB_SPRING.mass);
  assert.ok(Math.abs(TAB_SPRING.damping - critical) < 1e-9);
});

test("reduced motion shortens the enter fade and removes drift and delay", () => {
  assert.deepEqual(enterTransition(true, 0.56, 0.3), { duration: 0.12, ease: EASE_STANDARD });
  assert.equal(enterTransition(false, 0.56, 0.3).duration, 0.56);
  assert.equal(enterTransition(false, 0.56, 0.3).delay, 0.3);
  assert.equal(enterTransition(true, 0.05).duration, 0.05);
  assert.equal(drift(true, 8), 0);
  assert.equal(drift(false, 8), 8);
});

test("count-ups and draws show their final state under reduced motion", () => {
  assert.equal(countUpValue(true, 1200, 0), 1200);
  assert.equal(countUpValue(false, 1200, 340), 340);
  assert.equal(drawStart(true), 1);
  assert.equal(drawStart(false), 0);
});
