import { api } from "@/lib/api";
import type { ActivityBounds, AnalyticsOverview, AnalyticsTrend } from "./types";

export const analyticsApi = {
  getOverview: (from: string, to: string) =>
    api.get<AnalyticsOverview>(
      `/api/v1/analytics/overview?from=${from}&to=${to}`,
      { auth: true }
    ),

  getTrend: (months = 12) =>
    api.get<AnalyticsTrend>(
      `/api/v1/analytics/trend?months=${months}`,
      { auth: true }
    ),

  /** Surfaces what date range the user actually has data in. Used to guide the
      UI when the current month is empty but data exists elsewhere. */
  getActivityBounds: () =>
    api.get<ActivityBounds>("/api/v1/analytics/activity-bounds", { auth: true }),
};
