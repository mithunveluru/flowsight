import { execFileSync } from "node:child_process";
import { expect, test } from "@playwright/test";
import { askAssistant, openAssistantWithShortcut } from "./assistant";

const container = process.env.E2E_ML_CONTAINER;

test.describe("ml-service stopped", () => {
  test.skip(!container, "set E2E_ML_CONTAINER to the ml-service container name");
  test.use({ storageState: ".e2e-state/seeded-user.json" });

  test.beforeAll(() => {
    execFileSync("docker", ["stop", container!]);
  });

  test.afterAll(async () => {
    execFileSync("docker", ["start", container!]);
    await expect
      .poll(() => execFileSync("docker", ["inspect", "-f", "{{.State.Health.Status}}", container!]).toString().trim(), {
        timeout: 60_000,
      })
      .toBe("healthy");
  });

  test("transactions, what-if and the assistant still work", async ({ page }) => {
    await page.goto("/dashboard/transactions");
    await expect(page.getByRole("checkbox", { name: /^Select (?!all)/ }).first()).toBeVisible();

    await page.goto("/dashboard/what-if");
    await expect(page.getByText("Monthly cashflow impact")).toBeVisible();
    await expect(page.getByRole("heading", { name: "The simulation did not run" })).toHaveCount(0);

    await page.goto("/dashboard");
    const panel = await openAssistantWithShortcut(page);
    await askAssistant(panel, "How much did I spend on food in the last 30 days?");
    await expect(panel.getByText(/You spent /)).toBeVisible();
  });
});
