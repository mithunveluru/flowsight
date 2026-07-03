import { api, ApiError } from "@/lib/api";
import type {
  GenerateReportRequest,
  MonthlyReport,
  ReportJob,
  ReportJobPage,
  TaxSummary,
} from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function getStoredToken(): string | null {
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

export const reportsApi = {
  // triggers a browser download of the transactions CSV
  downloadCsv: async (from: string, to: string, category?: string): Promise<void> => {
    const params = new URLSearchParams({ from, to });
    if (category) params.set("category", category);

    const token = getStoredToken();
    const res = await fetch(
      `${API_BASE}/api/v1/reports/transactions.csv?${params.toString()}`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} }
    );

    if (!res.ok) {
      throw new ApiError("CSV download failed", res.status);
    }

    const blob = await res.blob();
    const url  = URL.createObjectURL(blob);

    // Use the server's suggested filename when available
    const disposition = res.headers.get("Content-Disposition") ?? "";
    const match = disposition.match(/filename="?([^"]+)"?/);
    const filename = match?.[1] ?? `flowsight-transactions-${from}-${to}.csv`;

    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  },

  getMonthlyReport: (from: string, to: string) =>
    api.get<MonthlyReport>(
      `/api/v1/reports/monthly?from=${from}&to=${to}`,
      { auth: true }
    ),

  getTaxSummary: () =>
    api.get<TaxSummary>("/api/v1/reports/tax-summary", { auth: true }),


  createIntelligenceReport: (req: GenerateReportRequest) =>
    api.post<ReportJob>("/api/v1/intelligence-reports", req, { auth: true }),

  getReportJob: (id: string) =>
    api.get<ReportJob>(`/api/v1/intelligence-reports/${id}`, { auth: true }),

  listReportJobs: (page = 0, size = 25) =>
    api.get<ReportJobPage>(
      `/api/v1/intelligence-reports?page=${page}&size=${size}`,
      { auth: true }
    ),

  deleteReportJob: (id: string) =>
    api.delete<void>(`/api/v1/intelligence-reports/${id}`, { auth: true }),

  // streams the generated PDF and triggers a browser download
  downloadIntelligenceReport: async (id: string, suggestedFilename: string): Promise<void> => {
    const token = getStoredToken();
    const res = await fetch(
      `${API_BASE}/api/v1/intelligence-reports/${id}/download`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} }
    );
    if (!res.ok) {
      throw new ApiError("Report download failed", res.status);
    }
    const blob = await res.blob();
    const url  = URL.createObjectURL(blob);
    const disposition = res.headers.get("Content-Disposition") ?? "";
    const match = disposition.match(/filename="?([^"]+)"?/);
    const filename = match?.[1] ?? suggestedFilename;
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  },
};
