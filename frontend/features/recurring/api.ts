import { api } from "@/lib/api";
import type { RecurringPattern } from "./types";

export const recurringApi = {
  list: (refresh = false) =>
    api.get<RecurringPattern[]>(
      `/api/v1/recurring${refresh ? "?refresh=true" : ""}`,
      { auth: true }
    ),

  detect: () =>
    api.post<RecurringPattern[]>("/api/v1/recurring/detect", {}, { auth: true }),

  dismiss: (id: string) =>
    api.delete<RecurringPattern>(`/api/v1/recurring/${id}`, { auth: true }),

  restore: (id: string) =>
    api.patch<RecurringPattern>(`/api/v1/recurring/${id}/restore`, {}, { auth: true }),

  confirm: (id: string) =>
    api.patch<RecurringPattern>(`/api/v1/recurring/${id}/confirm`, {}, { auth: true }),

  unconfirm: (id: string) =>
    api.patch<RecurringPattern>(`/api/v1/recurring/${id}/unconfirm`, {}, { auth: true }),
};
