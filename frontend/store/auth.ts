"use client";

import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";
import type { UserProfile } from "@/features/auth/types";

interface AuthState {
  token: string | null;
  user: UserProfile | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: UserProfile) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  devtools(
    persist(
      (set) => ({
        token: null,
        user: null,
        isAuthenticated: false,

        setAuth: (token, user) =>
          set({ token, user, isAuthenticated: true }, false, "auth/setAuth"),

        clearAuth: () =>
          set(
            { token: null, user: null, isAuthenticated: false },
            false,
            "auth/clearAuth"
          ),
      }),
      {
        name: "flowsight-auth",
        // Only persist the token and user — not the action functions
        partialize: (state) => ({
          token: state.token,
          user: state.user,
          isAuthenticated: state.isAuthenticated,
        }),
      }
    ),
    { name: "flowsight/auth" }
  )
);

// Non-hook accessor — safe to call outside React components (e.g. lib/api.ts)
export const getAuthToken = () => useAuthStore.getState().token;
