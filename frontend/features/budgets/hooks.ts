"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { budgetsApi } from "./api";

export function useBudgetSummary() {
  return useQuery({ queryKey: queryKeys.budgets(), queryFn: budgetsApi.summary });
}
