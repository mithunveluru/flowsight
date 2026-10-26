import { test } from "node:test";
import assert from "node:assert/strict";
import { cubicBezier } from "../tween.ts";
import { EASE_STANDARD } from "../tokens.ts";

test("easing fixes both ends and never decreases", () => {
  const ease = cubicBezier(...EASE_STANDARD);
  assert.equal(ease(0), 0);
  assert.equal(ease(1), 1);
  let prev = 0;
  for (let i = 1; i <= 100; i++) {
    const v = ease(i / 100);
    assert.ok(v >= prev - 1e-9, `step ${i}`);
    prev = v;
  }
});

test("the standard curve decelerates: most of the distance is covered by half time", () => {
  const ease = cubicBezier(...EASE_STANDARD);
  assert.ok(ease(0.5) > 0.85);
});

test("a linear bezier is the identity", () => {
  const ease = cubicBezier(0, 0, 1, 1);
  assert.ok(Math.abs(ease(0.37) - 0.37) < 1e-4);
});
