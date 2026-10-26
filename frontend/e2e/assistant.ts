import { expect, type Locator, type Page } from "@playwright/test";

export async function openAssistantWithShortcut(page: Page): Promise<Locator> {
  const panel = page.getByRole("complementary", { name: "Ask FlowSight" });
  await expect(page.getByRole("button", { name: "Ask FlowSight" })).toBeVisible();
  await page.keyboard.press("Control+j");
  await expect(panel).toBeVisible();
  return panel;
}

export async function askAssistant(panel: Locator, question: string) {
  await panel.getByPlaceholder("Ask about your spending").fill(question);
  await panel.getByRole("button", { name: "Send question" }).click();
}
