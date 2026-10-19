"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { insightsApi } from "./api";

export function useInsights() {
  return useQuery({ queryKey: queryKeys.insights(), queryFn: insightsApi.get });
}
