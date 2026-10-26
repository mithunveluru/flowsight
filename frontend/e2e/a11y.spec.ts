import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { askAssistant, openAssistantWithShortcut } from "./assistant";

const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

let reviewUrl: string | undefined;

async function receiptReviewUrl(page: Page): Promise<string> {
  if (reviewUrl) return reviewUrl;
  await page.goto("/dashboard/capture/receipt");
  await page.locator("input[type=file]:not([capture])").setInputFiles(path.join(__dirname, "fixtures", "receipt.jpg"));
  await page.waitForURL(/\/receipts\/[^/]+\/review/, { timeout: 90_000 });
  reviewUrl = new URL(page.url()).pathname;
  return reviewUrl;
}

async function audit(page: Page, label: string) {
  // let entrance fades finish so contrast is measured on settled colours
  await page.waitForTimeout(1500);
  const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  for (const v of violations) {
    console.log(`axe ${label} ${v.impact} ${v.id} x${v.nodes.length}`);
    for (const n of v.nodes) console.log(`    ${n.target.join(" ")} :: ${n.html.slice(0, 160)} :: ${(n.any[0] ?? n.all[0] ?? n.none[0])?.message ?? ""}`);
  }
  const blocking = violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(blocking.map((v) => `${v.id} (${v.impact}) ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`)).toEqual([]);
}

for (const scheme of ["light", "dark"] as const) {
  test.describe(`accessibility ${scheme}`, () => {
    test.use({ colorScheme: scheme });

    test("login", async ({ browser }) => {
      const context = await browser.newContext({ colorScheme: scheme });
      const page = await context.newPage();
      await page.goto("/auth/login");
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await audit(page, `${scheme} login`);
      await context.close();
    });

    test.describe("signed in", () => {
      test.use({ storageState: ".e2e-state/seeded-user.json" });

      const routes: [string, string][] = [
        ["today", "/dashboard"],
        ["transactions", "/dashboard/transactions"],
        ["what-if", "/dashboard/what-if"],
        ["habits", "/dashboard/habits"],
        ["leaks", "/dashboard/drains"],
        ["recurring", "/dashboard/subscriptions"],
        ["budgets", "/dashboard/budgets"],
        ["goals", "/dashboard/goals"],
        ["trends", "/dashboard/trends"],
        ["reports", "/dashboard/reports"],
        ["settings", "/dashboard/settings"],
      ];
      for (const [name, url] of routes) {
        test(name, async ({ page }) => {
          await page.goto(url);
          await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
          if (name === "transactions") {
            await expect(page.getByRole("checkbox", { name: /^Select (?!all)/ }).first()).toBeVisible();
          }
          if (name === "what-if") {
            await expect(page.getByRole("figure", { name: "Range of outcomes" })).toBeVisible();
          }
          await audit(page, `${scheme} ${name}`);
        });
      }

      test("receipt review", async ({ page }) => {
        await page.goto(await receiptReviewUrl(page));
        await expect(page.getByRole("img", { name: "Photo of the receipt" })).toBeVisible();
        await audit(page, `${scheme} receipt-review`);
      });

      test("assistant panel open", async ({ page }) => {
        await page.goto("/dashboard");
        const panel = await openAssistantWithShortcut(page);
        await audit(page, `${scheme} assistant-open`);
        await askAssistant(panel, "How much did I spend on food in the last 30 days?");
        await expect(panel.getByText(/You spent /)).toBeVisible();
        await audit(page, `${scheme} assistant-answered`);
      });
    });
  });
}
