import { expect, type Page } from "@playwright/test";
import { statementCsv } from "./data";

export const PASSWORD = "E2e-check-1234";

export function uniqueEmail(tag: string): string {
  return `e2e-${tag}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}@example.com`;
}

export async function register(page: Page, email: string) {
  await page.goto("/auth/register");
  await page.locator("#fullName").fill("E2E Check");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("button[type=submit]").click();
  await page.waitForURL(/\/dashboard/);
}

export async function login(page: Page, email: string) {
  await page.goto("/auth/login");
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("button[type=submit]").click();
  await page.waitForURL(/\/dashboard/);
}

export async function importStatement(page: Page) {
  await page.goto("/dashboard/capture/import");
  await page.locator("input[type=file]").setInputFiles({
    name: "statement.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(statementCsv()),
  });
  await page.getByRole("button", { name: "Import", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Import finished" })).toBeVisible();
}
