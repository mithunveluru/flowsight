import { test } from "node:test";
import assert from "node:assert/strict";
import { THEME_INIT_SCRIPT, parseThemePreference, resolveTheme } from "../theme-core.ts";

test("theme preference parsing falls back to the default", () => {
  assert.equal(parseThemePreference("dark"), "dark");
  assert.equal(parseThemePreference("system"), "system");
  assert.equal(parseThemePreference("blue"), "light");
  assert.equal(parseThemePreference(null), "light");
});

test("resolveTheme follows the system only for the system preference", () => {
  assert.equal(resolveTheme("system", true), "dark");
  assert.equal(resolveTheme("system", false), "light");
  assert.equal(resolveTheme("light", true), "light");
  assert.equal(resolveTheme("dark", false), "dark");
});

function runInitScript(stored, systemDark) {
  const classes = new Set();
  const root = {
    classList: { toggle: (c, on) => (on ? classes.add(c) : classes.delete(c)) },
    style: {},
  };
  const window = { matchMedia: () => ({ matches: systemDark }) };
  const localStorage = { getItem: () => stored };
  new Function("window", "document", "localStorage", THEME_INIT_SCRIPT)(window, { documentElement: root }, localStorage);
  return { dark: classes.has("dark"), scheme: root.style.colorScheme };
}

test("init script applies the stored theme before paint", () => {
  assert.deepEqual(runInitScript("dark", false), { dark: true, scheme: "dark" });
  assert.deepEqual(runInitScript("system", true), { dark: true, scheme: "dark" });
  assert.deepEqual(runInitScript("system", false), { dark: false, scheme: "light" });
  assert.deepEqual(runInitScript(null, true), { dark: false, scheme: "light" });
  assert.deepEqual(runInitScript("garbage", true), { dark: false, scheme: "light" });
});

test("init script never throws when storage is blocked", () => {
  const root = { classList: { toggle() {} }, style: {} };
  const localStorage = { getItem: () => { throw new Error("blocked"); } };
  assert.doesNotThrow(() =>
    new Function("window", "document", "localStorage", THEME_INIT_SCRIPT)({}, { documentElement: root }, localStorage)
  );
});
