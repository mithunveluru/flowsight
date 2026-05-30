export interface SpendAlert {
  type: string;
  severity: "HIGH" | "MEDIUM" | "LOW";
  category: string;
  categoryDisplayName: string;
  message: string;
  currentAmount: number;
  averageAmount: number;
  changePercent: number;
}

export interface CategoryBreakdownItem {
  category: string;
  displayName: string;
  amount: number;
  percentage: number;
  transactionCount: number;
}

export interface MonthlyTrendPoint {
  month: string;     // "2024-01"
  label: string;     // "Jan 2024"
  spend: number;
  income: number;
  net: number;
  projected: boolean;
}

export interface MerchantSummary {
  merchant: string;
  totalAmount: number;
  transactionCount: number;
}

export interface AnalyticsOverview {
  from: string;
  to: string;
  totalSpend: number;
  totalIncome: number;
  netCashflow: number;
  transactionCount: number;
  categoryBreakdown: CategoryBreakdownItem[];
  topMerchants: MerchantSummary[];
  alerts: SpendAlert[];
}

export interface AnalyticsTrend {
  points: MonthlyTrendPoint[];
}

export type DateRangePreset = "1M" | "3M" | "6M" | "12M";

export interface ActivityBounds {
  earliestTransactionDate: string | null;
  latestTransactionDate:   string | null;
  currentMonthHasData:     boolean;
  totalTransactionCount:   number;
  monthsWithActivity:      string[]; // "YYYY-MM", newest first
}
