import { api } from "@/lib/api";
import type { Receipt, ReceiptConfirmPayload, ReceiptPage } from "./types";

export const receiptApi = {
  upload: (file: File): Promise<Receipt> => {
    const form = new FormData();
    form.append("file", file);
    return api.upload<Receipt>("/api/v1/receipts/upload", form);
  },

  list: (page = 0, size = 20) =>
    api.get<ReceiptPage>(
      `/api/v1/receipts?page=${page}&size=${size}`,
      { auth: true }
    ),

  getById: (id: string) =>
    api.get<Receipt>(`/api/v1/receipts/${id}`, { auth: true }),

  confirm: (id: string, payload: ReceiptConfirmPayload) =>
    api.post<Receipt>(`/api/v1/receipts/${id}/confirm`, payload, { auth: true }),

  delete: (id: string) =>
    api.delete<void>(`/api/v1/receipts/${id}`, { auth: true }),
};
