import path from "node:path";
import { expect, test } from "@playwright/test";
import { FOOD_LAST_30_DAYS, statementCsv } from "./data";
import { importStatement, login, register, uniqueEmail } from "./helpers";
import { askAssistant, openAssistantWithShortcut } from "./assistant";

const seeded = { storageState: ".e2e-state/seeded-user.json" };

test.describe("account", () => {
  test("register, import a statement, sign out and sign back in", async ({ page }) => {
    const email = uniqueEmail("account");
    await register(page, email);
    await importStatement(page);
    const rows = statementCsv().trim().split("\n").length - 1;
    await expect(page.getByText("Rows read", { exact: true }).locator("xpath=..")).toContainText(String(rows));

    await page.goto("/dashboard/settings");
    await page.getByRole("button", { name: "Sign out" }).click();
    await page.waitForURL(/\/auth\/login/);

    await login(page, email);
    await page.goto("/dashboard/transactions");
    await expect(page.getByRole("heading", { name: "Transactions", level: 1 })).toBeVisible();
    await expect(page.getByRole("checkbox", { name: /^Select (?!all)/ }).first()).toBeVisible();
  });
});

test.describe("transactions", () => {
  test.use(seeded);

  test("edit a merchant inline, then act on rows in bulk", async ({ page }) => {
    await page.goto("/dashboard/transactions");
    const edit = page.getByRole("button", { name: /^Edit merchant for / }).first();
    await expect(edit).toBeVisible();
    await edit.click();
    const input = page.getByRole("textbox", { name: /^merchant for / });
    await input.fill("E2E Corner Store");
    await input.press("Enter");
    await expect(page.getByRole("button", { name: "Edit merchant for E2E Corner Store" })).toBeVisible();

    const boxes = page.getByRole("checkbox", { name: /^Select (?!all)/ });
    await boxes.nth(0).check();
    await boxes.nth(1).check();
    await expect(page.getByText("2 selected")).toBeVisible();
    await page.getByRole("button", { name: "Mark reviewed" }).click();
    await expect(page.getByText("Updated 2 transactions.")).toBeAttached();
    await expect(page.getByText("2 selected")).toHaveCount(0);

    await boxes.nth(2).check();
    await page.getByRole("button", { name: "Delete selected" }).click();
    await page.getByRole("button", { name: "Delete 1", exact: true }).click();
    await expect(page.getByText("Deleted 1 transaction.")).toBeAttached();
  });
});

test.describe("receipts", () => {
  test.use(seeded);

  test("upload, review beside the image, confirm", async ({ page }) => {
    await page.goto("/dashboard/capture/receipt");
    await page.locator("input[type=file]:not([capture])").setInputFiles(path.join(__dirname, "fixtures", "receipt.jpg"));
    await page.waitForURL(/\/receipts\/[^/]+\/review/, { timeout: 90_000 });

    await expect(page.getByRole("heading", { name: "Review receipt", level: 1 })).toBeVisible();
    await expect(page.getByRole("img", { name: "Photo of the receipt" })).toBeVisible();
    const currency = page.locator("#rv-currency");
    await expect(currency).toBeVisible();

    const amount = page.locator("#rv-amount");
    if (!(await amount.inputValue())) await amount.fill("250");
    const date = page.locator("#rv-date");
    if (!(await date.inputValue())) await date.fill(new Date().toISOString().slice(0, 10));
    if (/Select|Choose/i.test((await currency.textContent()) ?? "")) {
      await currency.click();
      await page.getByRole("option", { name: /^INR/ }).click();
    }

    await page.getByRole("button", { name: "Confirm and save" }).click();
    await expect(page.getByRole("heading", { name: "Receipt confirmed", level: 1 })).toBeVisible();
  });
});

test.describe("what-if", () => {
  test.use(seeded);

  test("draws the range-of-outcomes fan chart", async ({ page }) => {
    await page.goto("/dashboard/what-if");
    const fan = page.getByRole("figure", { name: "Range of outcomes" });
    await expect(fan).toBeVisible();
    await expect(fan.locator(".recharts-area-area").first()).toBeAttached();
    await expect(fan.locator(".recharts-line-curve").first()).toBeAttached();
  });
});

test.describe("assistant", () => {
  test.use(seeded);

  test("Cmd+J opens Ask FlowSight and answers from supplied facts", async ({ page }) => {
    await page.goto("/dashboard");
    const panel = await openAssistantWithShortcut(page);
    await askAssistant(panel, "How much did I spend on food in the last 30 days?");
    await expect(panel.getByText(new RegExp(`You spent .*${FOOD_LAST_30_DAYS}`))).toBeVisible();
    await expect(panel.getByText("Rules", { exact: true })).toBeVisible();
  });
});
