export type Severity   = "HIGH" | "MEDIUM" | "LOW";

export interface BehavioralPattern {
  code: string;
  title: string;
  description: string;
  severity: Severity;
  value: number;
  unit: string;
  context: string;
}

export interface BehavioralProfile {
  summary: string;
  patterns: BehavioralPattern[];
}

export interface Recommendation {
  type: "REDUCE_CATEGORY" | "CANCEL_SUBSCRIPTION" | "SHIFT_HABIT" | "REDIRECT_SAVINGS" | "REVIEW_INFLATION";
  title: string;
  description: string;
  suggestedAction: string;
  potentialMonthlySaving: number | null;
  potentialAnnualSaving: number | null;
  confidence: Severity;
  evidence: string[];
}

export interface ConsequenceProjection {
  label: string;
  category: string;
  monthlyAmount: number;
  yearCost: number;
  fiveYearCost: number;
  tenYearCost: number;
  tenYearOpportunityCost: number;
  reflection: string;
}

export interface InsightsResponse {
  profile: BehavioralProfile;
  recommendations: Recommendation[];
  topConsequences: ConsequenceProjection[];
  totalPotentialMonthlySaving: number;
  totalPotentialAnnualSaving: number;
}
