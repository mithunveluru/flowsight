import { create } from "zustand";
import { load, type Store } from "@tauri-apps/plugin-store";
import type { UserProfile } from "@/features/auth/types";

// JWT + profile persisted to the OS app-data dir; desktop has no shared localStorage.
const STORE_FILE = "auth.json";

let storePromise: Promise<Store> | null = null;
function authFile(): Promise<Store> {
  if (!storePromise) storePromise = load(STORE_FILE, { autoSave: true, defaults: {} });
  return storePromise;
}

interface AuthState {
  token: string | null;
  user: UserProfile | null;
  ready: boolean;
  hydrate: () => Promise<void>;
  setAuth: (token: string, user: UserProfile) => Promise<void>;
  clear: () => Promise<void>;
}

export const useAuth = create<AuthState>((set) => ({
  token: null,
  user: null,
  ready: false,

  hydrate: async () => {
    try {
      const store = await authFile();
      const token = (await store.get<string>("token")) ?? null;
      const user = (await store.get<UserProfile>("user")) ?? null;
      set({ token, user, ready: true });
    } catch {
      // First run / store unavailable — treat as logged out but ready.
      set({ token: null, user: null, ready: true });
    }
  },

  setAuth: async (token, user) => {
    const store = await authFile();
    await store.set("token", token);
    await store.set("user", user);
    set({ token, user });
  },

  clear: async () => {
    try {
      const store = await authFile();
      await store.delete("token");
      await store.delete("user");
    } finally {
      set({ token: null, user: null });
    }
  },
}));

// non-hook accessor for the API layer
export function getToken(): string | null {
  return useAuth.getState().token;
}

// the API layer calls this on a 401 to drop the session
export function forceLogout(): void {
  void useAuth.getState().clear();
}
