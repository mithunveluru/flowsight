"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { analyticsApi } from "./api";

export function useOverview(from: string, to: string) {
  return useQuery({
    queryKey: queryKeys.analytics.overview(from, to),
    queryFn: () => analyticsApi.getOverview(from, to),
  });
}

export function useTrend(months: number) {
  return useQuery({
    queryKey: queryKeys.analytics.trend(months),
    queryFn: () => analyticsApi.getTrend(months),
  });
}

export function useActivityBounds() {
  return useQuery({
    queryKey: queryKeys.analytics.activityBounds(),
    queryFn: analyticsApi.getActivityBounds,
  });
}
