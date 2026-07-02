import { api } from "~/lib/api";
import type {
  CreateTransactionPayload,
  Transaction,
  TransactionPage,
} from "@/features/transactions/types";

export const transactionApi = {
  recent: (size = 5) =>
    api.get<TransactionPage>(`/api/v1/transactions?page=0&size=${size}`),

  create: (payload: CreateTransactionPayload) =>
    api.post<Transaction>("/api/v1/transactions", payload),
};
