"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { recurringApi } from "./api";
import type { RecurringPattern } from "./types";

export function useRecurring() {
  return useQuery({ queryKey: queryKeys.recurring(), queryFn: () => recurringApi.list() });
}

/** Re-runs detection on the server and replaces the cached list with the result. */
export function useRescanRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => recurringApi.list(true),
    onSuccess: (patterns) => qc.setQueryData(queryKeys.recurring(), patterns),
  });
}

export type RecurringAction = "confirm" | "unconfirm" | "dismiss";

/** Confirm, unconfirm or dismiss a pattern: the list changes at once and rolls back on failure. */
export function useRecurringAction() {
  const qc = useQueryClient();
  const key = queryKeys.recurring();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: RecurringAction }) =>
      action === "confirm" ? recurringApi.confirm(id) : action === "unconfirm" ? recurringApi.unconfirm(id) : recurringApi.dismiss(id),
    onMutate: async ({ id, action }) => {
      await qc.cancelQueries({ queryKey: key });
      const previous = qc.getQueryData<RecurringPattern[]>(key);
      if (previous) {
        qc.setQueryData<RecurringPattern[]>(
          key,
          action === "dismiss"
            ? previous.filter((p) => p.id !== id)
            : previous.map((p) => (p.id === id ? { ...p, isUserConfirmed: action === "confirm" } : p))
        );
      }
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(key, ctx.previous);
    },
    onSuccess: (updated, { action }) => {
      if (action !== "dismiss") {
        qc.setQueryData<RecurringPattern[]>(key, (prev) => prev?.map((p) => (p.id === updated.id ? updated : p)));
      }
    },
  });
}
