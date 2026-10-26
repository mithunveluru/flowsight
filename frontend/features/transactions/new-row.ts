// The transaction a person just saved, so the table can flash its row once when they open it.
const WINDOW_MS = 5 * 60 * 1000;

let latest: { id: string; at: number } | null = null;

export function markNewTransaction(id: string, now = Date.now()) {
  latest = { id, at: now };
}

/** True once for the marked id while it is fresh; the mark is spent either way. */
export function takeNewTransaction(id: string, now = Date.now()): boolean {
  if (!latest || latest.id !== id) return false;
  const fresh = now - latest.at <= WINDOW_MS;
  latest = null;
  return fresh;
}
