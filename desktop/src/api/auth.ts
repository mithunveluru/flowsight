import { api } from "~/lib/api";
import type { AuthResponse, LoginPayload } from "@/features/auth/types";

export const authApi = {
  login: (payload: LoginPayload) =>
    api.post<AuthResponse>("/api/v1/auth/login", payload, { auth: false }),
};
