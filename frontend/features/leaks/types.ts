export type LeakType =
  | "DUPLICATE_SUBSCRIPTIONS"
  | "SUBSCRIPTION_CREEP"
  | "HIGH_FREQUENCY_SMALL_SPEND"
  | "BANK_FEES";

export type LeakSeverity = "HIGH" | "MEDIUM" | "LOW";

export interface LeakItem {
  merchant: string;
  amount: number;
  detail: string;
  category: string;
  categoryLabel: string;
}

export interface LeakInsight {
  type: LeakType;
  severity: LeakSeverity;
  title: string;
  description: string;
  recommendation: string;
  monthlyImpact: number;
  annualImpact: number;
  affectedItemsCount: number;
  items: LeakItem[];
}

export interface LeakDetectionResponse {
  totalLeaksFound: number;
  totalMonthlyImpact: number;
  totalAnnualImpact: number;
  leaks: LeakInsight[];
}
