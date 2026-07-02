import {
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from "@tauri-apps/plugin-notification";
import { load, type Store } from "@tauri-apps/plugin-store";
import { bumpRefresh } from "~/lib/refresh";
import { leaksApi } from "~/api/leaks";
import { insightsApi } from "~/api/insights";

// Background sync: a modest heartbeat that (1) keeps the visible data fresh and
// (2) fires a native notification when a NEW high-priority signal appears since
// the last check. Comparison is done against a persisted set of seen signal ids
// so the user is notified once per signal, never spammed.

const INTERVAL_MS = 5 * 60 * 1000;
const SEEN_FILE = "sync.json";
const SEEN_KEY = "seenHighSignals";

let storePromise: Promise<Store> | null = null;
function syncStore(): Promise<Store> {
  if (!storePromise) storePromise = load(SEEN_FILE, { autoSave: true, defaults: {} });
  return storePromise;
}

/** Collect the ids of every currently-active HIGH-severity signal. */
async function currentHighSignals(): Promise<{ ids: string[]; titles: Record<string, string> }> {
  const ids: string[] = [];
  const titles: Record<string, string> = {};

  const [leaks, insights] = await Promise.all([
    leaksApi.detect().catch(() => null),
    insightsApi.get().catch(() => null),
  ]);

  for (const leak of leaks?.leaks ?? []) {
    if (leak.severity === "HIGH") {
      const id = `leak:${leak.type}`;
      ids.push(id);
      titles[id] = leak.title;
    }
  }
  for (const p of insights?.profile.patterns ?? []) {
    if (p.severity === "HIGH") {
      const id = `pattern:${p.code}`;
      ids.push(id);
      titles[id] = p.title;
    }
  }
  return { ids, titles };
}

async function ensureNotifyPermission(): Promise<boolean> {
  let granted = await isPermissionGranted();
  if (!granted) granted = (await requestPermission()) === "granted";
  return granted;
}

async function check(firstRun: boolean): Promise<void> {
  bumpRefresh();
  try {
    const store = await syncStore();
    const { ids, titles } = await currentHighSignals();
    const seen = (await store.get<string[]>(SEEN_KEY)) ?? [];
    const seenSet = new Set(seen);
    const fresh = ids.filter((id) => !seenSet.has(id));

    await store.set(SEEN_KEY, ids);

    // Never notify on the very first check of a session — only on genuinely new
    // signals that appear while the app is running.
    if (firstRun || fresh.length === 0) return;
    if (!(await ensureNotifyPermission())) return;

    const body =
      fresh.length === 1
        ? titles[fresh[0]]
        : `${fresh.length} new high-priority signals need your attention`;
    sendNotification({ title: "FlowSight", body });
  } catch {
    /* sync failures are non-fatal */
  }
}

export function startBackgroundSync(): () => void {
  void check(true);
  const id = window.setInterval(() => void check(false), INTERVAL_MS);
  return () => window.clearInterval(id);
}
