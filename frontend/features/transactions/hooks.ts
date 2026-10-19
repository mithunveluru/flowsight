"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { transactionApi } from "./api";
import type { TransactionCategory } from "./types";

export function useTransactions(params: { page?: number; size?: number; category?: TransactionCategory } = {}) {
  return useQuery({
    queryKey: queryKeys.transactions.list(params),
    queryFn: () => transactionApi.list(params),
  });
}

export function useUncategorizedCount() {
  return useQuery({
    queryKey: queryKeys.transactions.list({ category: "UNCATEGORIZED", size: 1 }),
    queryFn: () => transactionApi.list({ category: "UNCATEGORIZED", size: 1 }),
    select: (page) => page.totalElements,
  });
}

// An import changes every derived view, so the whole cache is refreshed.
export function useImportCsv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => transactionApi.importCsv(file),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
