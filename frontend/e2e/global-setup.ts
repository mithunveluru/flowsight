import { chromium, type FullConfig } from "@playwright/test";
import { importStatement, register, uniqueEmail } from "./helpers";

export const STATE_FILE = ".e2e-state/seeded-user.json";

export default async function globalSetup(config: FullConfig) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: config.projects[0].use.baseURL });
  const page = await context.newPage();
  await register(page, uniqueEmail("seed"));
  await importStatement(page);
  await context.storageState({ path: STATE_FILE });
  await browser.close();
}
