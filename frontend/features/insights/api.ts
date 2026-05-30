import { api } from "@/lib/api";
import type { InsightsResponse } from "./types";

export const insightsApi = {
  get: () => api.get<InsightsResponse>("/api/v1/insights", { auth: true }),
};
