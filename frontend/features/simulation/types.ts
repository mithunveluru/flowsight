import type { TransactionCategory } from "@/features/transactions/types";

export type ScenarioType =
  | "ONE_TIME_PURCHASE"
  | "RECURRING_EXPENSE"
  | "SAVINGS_ADJUSTMENT"
  | "LOAN_EMI";

export type Severity = "POSITIVE" | "NEUTRAL" | "CAUTION" | "WARNING";

export type FlexibilityTier =
  | "EXCELLENT"
  | "GOOD"
  | "FAIR"
  | "TIGHT"
  | "CONSTRAINED";

export interface ScenarioRequest {
  type: ScenarioType;
  name?: string;
  amount: number;
  category?: TransactionCategory;
  durationMonths?: number;
  annualInterestRate?: number;
  tenureMonths?: number;
}

export interface FinancialBaseline {
  monthlyIncome: number;
  monthlySpend: number;
  monthlyRecurring: number;
  monthlyDiscretionary: number;
  monthlyNetSavings: number;
  savingsRate: number;
  dataMonths: number;
  topCategoryName: string | null;
  topCategoryMonthlySpend: number;
  hasEnoughData: boolean;
}

export interface FlexibilityScore {
  currentScore: number;
  projectedScore: number;
  deltaPercent: number;
  currentTier: FlexibilityTier;
  projectedTier: FlexibilityTier;
  explanation: string;
}

export interface ProjectionPoint {
  month: number;
  cumulativeSavings: number;
  monthlyNet: number;
}

export interface Projection {
  before: ProjectionPoint[];
  after: ProjectionPoint[];
}

export interface Tradeoff {
  label: string;
  value: string;
  description: string;
}

export interface ConsequenceInsight {
  code: string;
  severity: Severity;
  title: string;
  description: string;
}

export interface GoalImpact {
  goalName: string;
  delayMonths: number;
  description: string;
}

export interface SimulationResult {
  baseline: FinancialBaseline;
  scenario: ScenarioRequest;
  monthlyImpact: number;
  yearlyImpact: number;
  fiveYearImpact: number;
  tenYearCost: number;
  tenYearOpportunityCost: number;
  flexibility: FlexibilityScore;
  projection: Projection;
  tradeoffs: Tradeoff[];
  insights: ConsequenceInsight[];
  goalImpact: GoalImpact | null;
}
