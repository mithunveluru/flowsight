import { api } from "@/lib/api";
import type { Receipt, ReceiptConfirmPayload, ReceiptPage } from "./types";

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("flowsight-auth");
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { state?: { token?: string } };
    return parsed.state?.token ?? null;
  } catch {
    return null;
  }
}

export const receiptApi = {
  upload: async (file: File): Promise<Receipt> => {
    const form = new FormData();
    form.append("file", file);
    const token = getToken();

    const res = await fetch(
      `${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/api/v1/receipts/upload`,
      {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: form,
      }
    );

    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: "Upload failed" }));
      throw new Error(err.message ?? "Upload failed");
    }
    return res.json();
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
