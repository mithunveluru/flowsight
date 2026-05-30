import { api } from "@/lib/api";
import type { Budget, BudgetPayload, BudgetSummary } from "./types";

export const budgetsApi = {
  summary: () =>
    api.get<BudgetSummary>("/api/v1/budgets", { auth: true }),

  create: (payload: BudgetPayload) =>
    api.post<Budget>("/api/v1/budgets", payload, { auth: true }),

  update: (id: string, payload: BudgetPayload) =>
    api.put<Budget>(`/api/v1/budgets/${id}`, payload, { auth: true }),

  delete: (id: string) =>
    api.delete<void>(`/api/v1/budgets/${id}`, { auth: true }),
};
