"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { leaksApi } from "./api";

export function useLeaks() {
  return useQuery({ queryKey: queryKeys.leaks(), queryFn: leaksApi.detect });
}
