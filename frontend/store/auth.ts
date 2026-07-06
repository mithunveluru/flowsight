"use client";

import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";
import type { UserProfile } from "@/features/auth/types";

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: UserProfile | null;
  isAuthenticated: boolean;
  setAuth: (token: string, refreshToken: string, user: UserProfile) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  devtools(
    persist(
      (set) => ({
        token: null,
        refreshToken: null,
        user: null,
        isAuthenticated: false,

        setAuth: (token, refreshToken, user) =>
          set(
            { token, refreshToken, user, isAuthenticated: true },
            false,
            "auth/setAuth"
          ),

        clearAuth: () =>
          set(
            { token: null, refreshToken: null, user: null, isAuthenticated: false },
            false,
            "auth/clearAuth"
          ),
      }),
      {
        name: "flowsight-auth",
        // Only persist the tokens and user — not the action functions
        partialize: (state) => ({
          token: state.token,
          refreshToken: state.refreshToken,
          user: state.user,
          isAuthenticated: state.isAuthenticated,
        }),
      }
    ),
    // devtools off in production: the store holds the JWT, and Redux DevTools
    // would expose it to any extension/tab inspecting the page
    { name: "flowsight/auth", enabled: process.env.NODE_ENV !== "production" }
  )
);

// Non-hook accessor — safe to call outside React components (e.g. lib/api.ts)
export const getAuthToken = () => useAuthStore.getState().token;
