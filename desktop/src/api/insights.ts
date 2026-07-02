import { api } from "~/lib/api";
import type { InsightsResponse } from "@/features/insights/types";

export const insightsApi = {
  get: () => api.get<InsightsResponse>("/api/v1/insights"),
};
