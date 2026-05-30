import { api } from "@/lib/api";
import type { AuthResponse, LoginPayload, RegisterPayload, UserProfile } from "./types";

export const authApi = {
  register: (payload: RegisterPayload) =>
    api.post<AuthResponse>("/api/v1/auth/register", payload),

  login: (payload: LoginPayload) =>
    api.post<AuthResponse>("/api/v1/auth/login", payload),

  me: () => api.get<UserProfile>("/api/v1/auth/me", { auth: true }),
};
