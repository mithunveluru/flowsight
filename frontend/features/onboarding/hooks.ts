"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { onboardingApi } from "./api";

// An error (including 404 from a backend without the endpoint) hides the sample-data UI.
export function useSampleDataStatus() {
  return useQuery({
    queryKey: queryKeys.onboarding.sampleData(),
    queryFn: onboardingApi.sampleDataStatus,
    retry: false,
  });
}

export function useLoadSampleData() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: onboardingApi.loadSampleData,
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

export function useRemoveSampleData() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: onboardingApi.removeSampleData,
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
