import { api } from "~/lib/api";
import type { AnalyticsOverview } from "@/features/analytics/types";

export const analyticsApi = {
  overview: (from: string, to: string) =>
    api.get<AnalyticsOverview>(`/api/v1/analytics/overview?from=${from}&to=${to}`),
};
