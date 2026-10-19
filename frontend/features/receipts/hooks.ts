"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { receiptApi } from "./api";

export function useReceipts(page = 0, size = 20) {
  return useQuery({
    queryKey: queryKeys.receipts.list(page, size),
    queryFn: () => receiptApi.list(page, size),
  });
}

// Shared by the sidebar badge and Today so both read one cached page.
export const RECENT_RECEIPTS_SIZE = 50;

export function useRecentReceipts() {
  return useReceipts(0, RECENT_RECEIPTS_SIZE);
}
