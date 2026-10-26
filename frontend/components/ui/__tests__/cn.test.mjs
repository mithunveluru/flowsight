import test from "node:test";
import assert from "node:assert/strict";
import { cn } from "../../../lib/utils.ts";

test("a text-ty size role does not remove the text colour", () => {
  assert.equal(cn("text-primary-foreground", "text-ty-button"), "text-primary-foreground text-ty-button");
});

test("a later text colour still wins over an earlier one", () => {
  assert.equal(cn("text-ink", "text-primary"), "text-primary");
});
