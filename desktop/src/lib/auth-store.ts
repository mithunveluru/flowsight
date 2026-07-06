import { create } from "zustand";
import { load, type Store } from "@tauri-apps/plugin-store";
import type { UserProfile } from "@/features/auth/types";

// Tokens + profile persisted to the OS app-data dir; desktop has no shared localStorage.
// The access token is short-lived (15 min); the refresh token rotates at /auth/refresh.
const STORE_FILE = "auth.json";

let storePromise: Promise<Store> | null = null;
function authFile(): Promise<Store> {
  if (!storePromise) storePromise = load(STORE_FILE, { autoSave: true, defaults: {} });
  return storePromise;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: UserProfile | null;
  ready: boolean;
  hydrate: () => Promise<void>;
  setAuth: (token: string, refreshToken: string, user: UserProfile) => Promise<void>;
  clear: () => Promise<void>;
}

export const useAuth = create<AuthState>((set) => ({
  token: null,
  refreshToken: null,
  user: null,
  ready: false,

  hydrate: async () => {
    try {
      const store = await authFile();
      const token = (await store.get<string>("token")) ?? null;
      const refreshToken = (await store.get<string>("refreshToken")) ?? null;
      const user = (await store.get<UserProfile>("user")) ?? null;
      set({ token, refreshToken, user, ready: true });
    } catch {
      // First run / store unavailable — treat as logged out but ready.
      set({ token: null, refreshToken: null, user: null, ready: true });
    }
  },

  setAuth: async (token, refreshToken, user) => {
    const store = await authFile();
    await store.set("token", token);
    await store.set("refreshToken", refreshToken);
    await store.set("user", user);
    set({ token, refreshToken, user });
  },

  clear: async () => {
    try {
      const store = await authFile();
      await store.delete("token");
      await store.delete("refreshToken");
      await store.delete("user");
    } finally {
      set({ token: null, refreshToken: null, user: null });
    }
  },
}));

// non-hook accessors for the API layer
export function getToken(): string | null {
  return useAuth.getState().token;
}

export function getRefreshToken(): string | null {
  return useAuth.getState().refreshToken;
}

export function setTokens(token: string, refreshToken: string): Promise<void> {
  const user = useAuth.getState().user;
  // user is always set once logged in; keep it as-is on silent refresh
  return useAuth.getState().setAuth(token, refreshToken, user as UserProfile);
}

// the API layer calls this when the session can no longer be refreshed
export function forceLogout(): void {
  void useAuth.getState().clear();
}
