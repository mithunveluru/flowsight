export type Severity   = "HIGH" | "MEDIUM" | "LOW";

export interface BehavioralPattern {
  code: string;
  title: string;
  description: string;
  severity: Severity;
  value: number;
  unit: string;
  context: string;
  /** The per-user level the value had to reach to be flagged (same unit as value). */
  threshold?: number | null;
  /** Monthly amount that would bring the pattern back under the user's own threshold. */
  monthlySavingEstimate?: number | null;
  /** CATEGORY_DRIFT only. */
  category?: string | null;
}

export interface CategoryDrift {
  category: string;
  displayName: string;
  direction: "UP" | "DOWN";
  since: string;
  weeksSince: number;
  baselineWeekly: number;
  recentWeekly: number;
  monthlyChange: number;
}

export interface BehavioralProfile {
  summary: string;
  patterns: BehavioralPattern[];
  historyDays?: number;
  /** Pattern codes not evaluated yet because the history is too short. */
  needsMoreHistory?: string[];
  drifts?: CategoryDrift[];
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
  /** Stable id for accept/dismiss (POST /api/v1/insights/recommendations/{id}/accept|dismiss). */
  id?: string;
  kind?: string;
  source?: "LEAK" | "BEHAVIOR" | "GOAL";
  acceptanceRate?: number;
  rankScore?: number;
  rankingReason?: string;
  feedback?: "ACCEPTED" | null;
}

export interface RecommendationFeedbackResult {
  recommendationId: string;
  kind: string;
  action: "ACCEPTED" | "DISMISSED";
}

export interface InsightNarrative {
  text: string;
  source: "LLM" | "TEMPLATE";
}

export interface AssistantCitation {
  id: string;
  tool: string;
  title: string;
  facts: Record<string, string | number>;
}

export interface AssistantAnswer {
  answer: string;
  answered: boolean;
  mode: "LLM" | "RULES";
  intent: string | null;
  citations: AssistantCitation[];
  fallbackReason?: string | null;
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
  narrative?: InsightNarrative;
}
