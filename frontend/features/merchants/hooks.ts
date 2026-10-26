"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { merchantRuleApi } from "./api";

export function usePreviewRule() {
  return useMutation({ mutationFn: (ruleId: string) => merchantRuleApi.preview(ruleId) });
}

// Applying renames or recategorizes many rows, so every derived view is refreshed.
export function useApplyRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (ruleId: string) => merchantRuleApi.apply(ruleId),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
