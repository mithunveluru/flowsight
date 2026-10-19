"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { recurringApi } from "./api";

export function useRecurring() {
  return useQuery({ queryKey: queryKeys.recurring(), queryFn: () => recurringApi.list() });
}
