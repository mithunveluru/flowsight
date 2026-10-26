"use client";

import { useCallback, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/features/query-core";
import { receiptApi } from "./api";
import { createObjectUrlSlot } from "./capture-logic";
import type { Receipt, ReceiptConfirmPayload } from "./types";

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

const receiptKey = (id: string) => ["receipts", "detail", id] as const;

export function useReceipt(id: string) {
  return useQuery({
    queryKey: receiptKey(id),
    queryFn: () => receiptApi.getById(id),
    // a draft can still be in flight when the review page opens
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "PENDING" || s === "PROCESSING" ? 3000 : false;
    },
  });
}

// Confirming creates a transaction, so every derived view is refreshed.
export function useConfirmReceipt(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReceiptConfirmPayload) => receiptApi.confirm(id, payload),
    onSuccess: (receipt: Receipt) => {
      queryClient.setQueryData(receiptKey(id), receipt);
      void queryClient.invalidateQueries({ predicate: (q) => !(q.queryKey[0] === "receipts" && q.queryKey[1] === "detail") });
    },
  });
}

export function useDeleteReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => receiptApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["receipts"] }),
  });
}

export type ReceiptImageState =
  | { status: "loading"; url: null }
  | { status: "ready"; url: string }
  | { status: "missing"; url: null }
  | { status: "error"; url: null; error: unknown };

/**
 * Loads the review copy with the bearer token into an object URL that is revoked when
 * the id changes or the component unmounts. `missing` covers expired, deleted and lost.
 */
export function useReceiptImage(id: string, enabled: boolean) {
  const [state, setState] = useState<ReceiptImageState>({ status: "loading", url: null });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) {
      setState({ status: "missing", url: null });
      return;
    }
    const slot = createObjectUrlSlot();
    const controller = new AbortController();
    setState({ status: "loading", url: null });
    receiptApi
      .image(id, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return;
        const url = slot.set(blob);
        setState(url ? { status: "ready", url } : { status: "missing", url: null });
      })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ status: "error", url: null, error });
      });
    return () => {
      controller.abort();
      slot.dispose();
    };
  }, [id, enabled, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { ...state, retry };
}
