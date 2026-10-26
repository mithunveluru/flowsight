"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { insightsApi } from "./api";
import { applyFeedback, type FeedbackAction } from "./feedback";
import type { InsightsResponse } from "./types";

export function useInsights() {
  return useQuery({ queryKey: queryKeys.insights(), queryFn: insightsApi.get });
}

/** Accept or dismiss a recommendation: the list updates at once and rolls back if the call fails. */
export function useRecommendationFeedback() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: FeedbackAction }) =>
      action === "ACCEPTED" ? insightsApi.accept(id) : insightsApi.dismiss(id),
    onMutate: async ({ id, action }) => {
      const key = queryKeys.insights();
      await qc.cancelQueries({ queryKey: key });
      const previous = qc.getQueryData<InsightsResponse>(key);
      if (previous) qc.setQueryData(key, applyFeedback(previous, id, action));
      return { previous };
    },
    onError: (_e, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(queryKeys.insights(), ctx.previous);
    },
  });
}
