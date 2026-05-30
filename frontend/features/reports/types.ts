import type {
  CategoryBreakdownItem,
  MerchantSummary,
  SpendAlert,
} from "@/features/analytics/types";

export interface TaxDeductionEntry {
  merchant: string;
  description: string;
  amount: number;
  date: string;
  detectedBy: string;
}

export interface TaxSection {
  code: string;          // "80C" | "80D" | "80E"
  name: string;
  description: string;
  totalAmount: number;
  limit: number | null;
  remainingLimit: number | null;
  entries: TaxDeductionEntry[];
}

export interface TaxSummary {
  financialYear: number;
  periodStart: string;
  periodEnd: string;
  totalEligible: number;
  sections: TaxSection[];
}

export interface MonthlyReport {
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  totalSpend: number;
  totalIncome: number;
  netCashflow: number;
  transactionCount: number;
  categoryBreakdown: CategoryBreakdownItem[];
  topMerchants: MerchantSummary[];
  alerts: SpendAlert[];
  taxSummary: TaxSummary;
}

// -------------------------------------------------------------------------
// Phase 12: Financial intelligence reports
// -------------------------------------------------------------------------

export type ReportPreset =
  | "LAST_7_DAYS"
  | "LAST_30_DAYS"
  | "LAST_90_DAYS"
  | "THIS_MONTH"
  | "LAST_MONTH"
  | "THIS_YEAR"
  | "CUSTOM";

export type ReportStatus = "PENDING" | "GENERATING" | "READY" | "FAILED";

export interface GenerateReportRequest {
  preset: ReportPreset;
  from?: string;   // ISO yyyy-MM-dd, only for CUSTOM
  to?:   string;
}

export interface ReportJob {
  id: string;
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  status: ReportStatus;
  pdfSizeBytes: number | null;
  errorMessage: string | null;
  downloadCount: number;
  createdAt: string;
  completedAt: string | null;
}

export interface ReportJobPage {
  content: ReportJob[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
