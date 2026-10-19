// Per-user, per-device preferences for Today and onboarding. Storage can be
// blocked (private mode, quota); every call degrades to "nothing stored".

export type StoredName = "today-visit" | "welcome-dismissed" | "checklist-dismissed" | "hidden-drains";

const storageKey = (userId: string, name: StoredName) => `flowsight:${userId}:${name}`;

export function readStored<T>(userId: string | undefined, name: StoredName): T | null {
  if (!userId) return null;
  try {
    const raw = window.localStorage.getItem(storageKey(userId, name));
    return raw == null ? null : (JSON.parse(raw) as T);
  } catch {
    return null;
  }
}

export function writeStored(userId: string | undefined, name: StoredName, value: unknown): void {
  if (!userId) return;
  try {
    window.localStorage.setItem(storageKey(userId, name), JSON.stringify(value));
  } catch {
    // ignored: the preference only lasts for this page view
  }
}
