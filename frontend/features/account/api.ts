import { api } from "@/lib/api";
import type { Account, AuditLogPage } from "./types";

export const accountApi = {
  get: () => api.get<Account>("/api/v1/account", { auth: true }),

  getAuditLog: (page = 0, size = 50) =>
    api.get<AuditLogPage>(
      `/api/v1/account/audit-log?page=${page}&size=${size}`,
      { auth: true }
    ),
};
