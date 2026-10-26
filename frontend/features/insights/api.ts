import { api } from "@/lib/api";
import type { InsightsResponse, RecommendationFeedbackResult } from "./types";

export const insightsApi = {
  get: () => api.get<InsightsResponse>("/api/v1/insights", { auth: true }),

  accept: (id: string) =>
    api.post<RecommendationFeedbackResult>(`/api/v1/insights/recommendations/${id}/accept`, {}, { auth: true }),

  dismiss: (id: string) =>
    api.post<RecommendationFeedbackResult>(`/api/v1/insights/recommendations/${id}/dismiss`, {}, { auth: true }),
};
