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

export interface ForecastInterval {
  lower80: number;
  upper80: number;
  lower95: number;
  upper95: number;
}

// How a forecast was made (evidence drawer). Accuracy fields come from the user's own
// rolling-origin backtest and are null when the history is too short to run one.
export interface ForecastEvidence {
  method: string;
  window: [string, string] | null;   // first and last complete month used, "YYYY-MM"
  dataMonths: number;
  lowHistory: boolean;
  intervalLevels: number[];          // [80, 95]
  intervalSource: "GLOBAL_PRIOR" | "GLOBAL_PRIOR_SHRUNK_TO_OWN_ERRORS";
  backtestPoints: number;
  backtestMae: number | null;
  backtestWape: number | null;
  ownWeight: number;
  priorsVersion: string;
  source: "in-app" | "ml-service";
  modelVersion: string | null;
  note: string;
}

export interface MonthlyTrendPoint {
  month: string;     // "2024-01"
  label: string;     // "Jan 2024"
  spend: number;
  income: number;
  net: number;
  projected: boolean;
  // prediction intervals of a projected point; null on actual months
  range?: { spend: ForecastInterval; income: ForecastInterval; net: ForecastInterval } | null;
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

export interface TrendForecast {
  asOf: string;
  dataMonths: number;
  lowHistory: boolean;
  source: "in-app" | "ml-service";
  spend: ForecastEvidence;
  income: ForecastEvidence;
  net: ForecastEvidence;
}

export interface AnalyticsTrend {
  points: MonthlyTrendPoint[];
  forecast?: TrendForecast;
}

// GET /api/v1/forecast?months=N: the current month (partial) then N future months per series.
export interface ForecastPoint {
  month: string;
  partial: boolean;
  point: number;
  interval: ForecastInterval;
}

export interface ForecastSeries {
  series: string;     // "income" | "spend" | "net" | a TransactionCategory
  label: string;
  points: ForecastPoint[];
  evidence: ForecastEvidence;
}

export interface CashFlowForecast {
  asOf: string;
  dataMonths: number;
  lowHistory: boolean;
  source: "in-app" | "ml-service";
  income: ForecastSeries;
  spend: ForecastSeries;
  net: ForecastSeries;
  categories: ForecastSeries[];
}

export type DateRangePreset = "1M" | "3M" | "6M" | "12M";

export interface ActivityBounds {
  earliestTransactionDate: string | null;
  latestTransactionDate:   string | null;
  currentMonthHasData:     boolean;
  totalTransactionCount:   number;
  monthsWithActivity:      string[]; // "YYYY-MM", newest first
}
