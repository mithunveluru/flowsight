import { api } from "@/lib/api";
import type { AuthResponse, LoginPayload, RegisterPayload, UserProfile } from "./types";

type MessageResponse = { message: string };

export const authApi = {
  register: (payload: RegisterPayload) =>
    api.post<AuthResponse>("/api/v1/auth/register", payload),

  login: (payload: LoginPayload) =>
    api.post<AuthResponse>("/api/v1/auth/login", payload),

  me: () => api.get<UserProfile>("/api/v1/auth/me", { auth: true }),

  forgotPassword: (email: string) =>
    api.post<MessageResponse>("/api/v1/auth/forgot-password", { email }),

  resetPassword: (payload: { token: string; password: string }) =>
    api.post<MessageResponse>("/api/v1/auth/reset-password", payload),

  // server-side logout: revokes the refresh token; fire-and-forget on sign-out
  logout: (refreshToken: string) =>
    api.post<void>("/api/v1/auth/logout", { refreshToken }),
};
