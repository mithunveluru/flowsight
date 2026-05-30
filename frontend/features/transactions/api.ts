import { api } from "@/lib/api";
import type {
  BulkImportResult,
  CreateTransactionPayload,
  Transaction,
  TransactionCategory,
  TransactionPage,
} from "./types";

interface ListParams {
  page?: number;
  size?: number;
  category?: TransactionCategory;
  startDate?: string;
  endDate?: string;
}

export const transactionApi = {
  list: (params: ListParams = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined)  query.set("page", String(params.page));
    if (params.size !== undefined)  query.set("size", String(params.size));
    if (params.category)             query.set("category", params.category);
    if (params.startDate)            query.set("startDate", params.startDate);
    if (params.endDate)              query.set("endDate", params.endDate);
    const qs = query.toString();
    return api.get<TransactionPage>(
      `/api/v1/transactions${qs ? `?${qs}` : ""}`,
      { auth: true }
    );
  },

  create: (payload: CreateTransactionPayload) =>
    api.post<Transaction>("/api/v1/transactions", payload, { auth: true }),

  update: (id: string, payload: Partial<CreateTransactionPayload> & { reviewed?: boolean }) =>
    api.patch<Transaction>(`/api/v1/transactions/${id}`, payload, { auth: true }),

  delete: (id: string) =>
    api.delete<void>(`/api/v1/transactions/${id}`, { auth: true }),

  importCsv: async (file: File): Promise<BulkImportResult> => {
    const token = (() => {
      try {
        const raw = localStorage.getItem("flowsight-auth");
        if (!raw) return null;
        const parsed = JSON.parse(raw) as { state?: { token?: string } };
        return parsed.state?.token ?? null;
      } catch {
        return null;
      }
    })();

    const form = new FormData();
    form.append("file", file);

    const res = await fetch(
      `${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/api/v1/transactions/import/csv`,
      {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: form,
      }
    );

    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: "Import failed" }));
      throw new Error(err.message ?? "Import failed");
    }
    return res.json();
  },
};
