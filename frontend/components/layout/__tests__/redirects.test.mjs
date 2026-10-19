import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import config, { renamedRoutes } from "../../../next.config.ts";
import { navSections } from "../nav.ts";

const root = fileURLToPath(new URL("../../../", import.meta.url));
const navHrefs = new Set(navSections.flatMap((s) => s.items.map((i) => i.href)));

test("every renamed route redirects temporarily to its new name", async () => {
  const redirects = await config.redirects();
  assert.deepEqual(
    redirects.map((r) => [r.source, r.destination, r.permanent]),
    [
      ["/dashboard/transactions/new", "/dashboard/capture/manual", false],
      ["/dashboard/receipts/upload", "/dashboard/capture/receipt", false],
      ["/dashboard/analytics", "/dashboard/trends", false],
      ["/dashboard/insights", "/dashboard/habits", false],
      ["/dashboard/leaks", "/dashboard/drains", false],
      ["/dashboard/recurring", "/dashboard/subscriptions", false],
      ["/dashboard/simulate", "/dashboard/what-if", false],
    ]
  );
});

test("redirects land on navigation entries and never on another old URL", () => {
  const sources = new Set(renamedRoutes.map(([from]) => from));
  for (const [, to] of renamedRoutes) {
    assert.ok(navHrefs.has(to), `${to} is in the navigation`);
    assert.ok(!sources.has(to), `${to} is not itself redirected`);
  }
  for (const href of navHrefs) assert.ok(!sources.has(href), `${href} is not an old URL`);
});

test("every redirect target has a page", () => {
  for (const [, to] of renamedRoutes) {
    assert.ok(statSync(join(root, "app", to, "page.tsx")).isFile(), `${to}/page.tsx exists`);
  }
});

function sourceFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === "node_modules" || entry.name.startsWith(".") ? [] : sourceFiles(path);
    return /\.(tsx?|mjs)$/.test(entry.name) && !entry.name.endsWith(".test.mjs") ? [path] : [];
  });
}

test("no internal link points at an old URL", () => {
  const old = new RegExp(`["'\`](${renamedRoutes.map(([from]) => from).join("|")})(?![\\w/-])`);
  const offenders = ["app", "components", "features", "lib"]
    .flatMap((d) => sourceFiles(join(root, d)))
    .filter((file) => old.test(readFileSync(file, "utf8")))
    .map((file) => relative(root, file));
  assert.deepEqual(offenders, []);
});
