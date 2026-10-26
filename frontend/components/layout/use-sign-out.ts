"use client";

import { useRouter } from "next/navigation";
import { authApi } from "@/features/auth/api";
import { useAuthStore } from "@/store/auth";

export function useSignOut() {
  const router = useRouter();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  return () => {
    // revoke the refresh token server-side; local logout proceeds regardless
    const refreshToken = useAuthStore.getState().refreshToken;
    if (refreshToken) void authApi.logout(refreshToken).catch(() => {});
    clearAuth();
    router.replace("/auth/login");
  };
}
