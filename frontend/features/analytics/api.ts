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

  // date range the user has data in; guides the UI when the current view is empty
  getActivityBounds: () =>
    api.get<ActivityBounds>("/api/v1/analytics/activity-bounds", { auth: true }),
};
