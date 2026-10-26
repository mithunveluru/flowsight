export type LeakType =
  | "DUPLICATE_SUBSCRIPTIONS"
  | "SUBSCRIPTION_CREEP"
  | "HIGH_FREQUENCY_SMALL_SPEND"
  | "BANK_FEES";

export type LeakSeverity = "HIGH" | "MEDIUM" | "LOW";

export interface LeakEvidence {
  source: "RULES" | "MODEL";
  method: string;
  windowStart: string | null;
  windowEnd: string | null;
  inputs: Record<string, string | number | boolean | null> | null;
  confidence: number | null;
}

export interface LeakItem {
  merchant: string;
  amount: number;
  detail: string;
  category: string;
  categoryLabel: string;
  evidence?: LeakEvidence | null;
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
  evidence?: LeakEvidence | null;
}

export interface LeakDetectionResponse {
  totalLeaksFound: number;
  totalMonthlyImpact: number;
  totalAnnualImpact: number;
  leaks: LeakInsight[];
}
