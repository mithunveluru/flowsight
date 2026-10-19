"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { goalsApi } from "./api";

export function useGoals() {
  return useQuery({ queryKey: queryKeys.goals(), queryFn: goalsApi.list });
}
