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

  importCsv: (file: File): Promise<BulkImportResult> => {
    const form = new FormData();
    form.append("file", file);
    return api.upload<BulkImportResult>("/api/v1/transactions/import/csv", form);
  },
};
