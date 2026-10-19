// Query keys and cache policy shared by every features/*/hooks.ts. Kept free of
// imports so the node test runner can load it directly.

export const queryKeys = {
  analytics: {
    overview: (from: string, to: string) => ["analytics", "overview", from, to] as const,
    trend: (months: number) => ["analytics", "trend", months] as const,
    activityBounds: () => ["analytics", "activity-bounds"] as const,
  },
  transactions: {
    all: () => ["transactions"] as const,
    list: (params: Record<string, string | number | undefined>) => ["transactions", "list", params] as const,
  },
  receipts: {
    list: (page: number, size: number) => ["receipts", "list", page, size] as const,
  },
  insights: () => ["insights"] as const,
  leaks: () => ["leaks"] as const,
  recurring: () => ["recurring"] as const,
  budgets: () => ["budgets"] as const,
  goals: () => ["goals"] as const,
  onboarding: {
    sampleData: () => ["onboarding", "sample-data"] as const,
  },
};

export const STALE_TIME_MS = 30_000;
const MAX_RETRIES = 1;

// Retry network failures and 5xx once; a 4xx will not change on retry.
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  const status = (error as { status?: unknown } | null)?.status;
  return !(typeof status === "number" && status >= 400 && status < 500);
}

export function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function monthToDate(now: Date): { from: string; to: string } {
  return { from: isoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: isoDate(now) };
}
