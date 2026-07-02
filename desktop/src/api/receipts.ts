import { api } from "~/lib/api";
import type { Receipt, ReceiptConfirmPayload } from "@/features/receipts/types";

export const receiptApi = {
  upload: (file: File | Blob, fileName = "receipt.png") => {
    const form = new FormData();
    form.append("file", file, fileName);
    return api.upload<Receipt>("/api/v1/receipts/upload", form);
  },

  getById: (id: string) => api.get<Receipt>(`/api/v1/receipts/${id}`),

  confirm: (id: string, payload: ReceiptConfirmPayload) =>
    api.post<Receipt>(`/api/v1/receipts/${id}/confirm`, payload),
};
