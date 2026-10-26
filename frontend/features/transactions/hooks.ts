"use client";

import { useMemo } from "react";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type Query,
  type QueryClient,
} from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { transactionApi } from "./api";
import type {
  CreateTransactionPayload,
  Transaction,
  TransactionCategory,
  TransactionPage,
  UpdateTransactionPayload,
} from "./types";

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

// The API's maximum page size.
export const TABLE_PAGE_SIZE = 100;

export interface ServerFilters {
  category?: TransactionCategory;
  startDate?: string;
  endDate?: string;
}

const pagesKey = (f: ServerFilters) => ["transactions", "pages", f] as const;
const reviewQueueKey = (limit: number) => ["transactions", "review-queue", limit] as const;

/** All matching transactions, loaded 100 at a time as the table scrolls (newest first). */
export function useTransactionPages(filters: ServerFilters) {
  return useInfiniteQuery({
    queryKey: pagesKey(filters),
    initialPageParam: 0,
    queryFn: ({ pageParam }) => transactionApi.list({ ...filters, page: pageParam, size: TABLE_PAGE_SIZE }),
    getNextPageParam: (last: TransactionPage) => (last.last ? undefined : last.number + 1),
  });
}

export function useReviewQueue(limit = 20) {
  return useQuery({
    queryKey: reviewQueueKey(limit),
    queryFn: () => transactionApi.reviewQueue(limit),
  });
}

const isTableQuery = (q: Query) =>
  q.queryKey[0] === "transactions" && (q.queryKey[1] === "pages" || q.queryKey[1] === "review-queue");

function patchPages(queryClient: QueryClient, change: (rows: Transaction[]) => Transaction[]) {
  queryClient.setQueriesData<InfiniteData<TransactionPage>>({ queryKey: ["transactions", "pages"] }, (data) =>
    data && { ...data, pages: data.pages.map((p) => ({ ...p, content: change(p.content) })) }
  );
}

/**
 * Keeps the loaded table in step with edits without refetching every page. Everything
 * derived from transactions (Today, trends, budgets) is marked stale and refetched when shown.
 */
export function useTransactionCache() {
  const queryClient = useQueryClient();
  return useMemo(() => ({
    updated(rows: Transaction[]) {
      const byId = new Map(rows.map(({ ruleSuggestion: _s, ...row }) => [row.id, row as Transaction]));
      patchPages(queryClient, (list) => list.map((r) => byId.get(r.id) ?? r));
      void queryClient.invalidateQueries({ predicate: (q) => !isTableQuery(q) });
    },
    removed(ids: string[]) {
      const gone = new Set(ids);
      patchPages(queryClient, (list) => list.filter((r) => !gone.has(r.id)));
      // totals and page boundaries shift after a delete
      void queryClient.invalidateQueries();
    },
  }), [queryClient]);
}

export function useUpdateTransaction() {
  const cache = useTransactionCache();
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: UpdateTransactionPayload }) => transactionApi.update(id, patch),
    onSuccess: (updated) => cache.updated([updated]),
  });
}

export function useCreateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTransactionPayload) => transactionApi.create(payload),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
