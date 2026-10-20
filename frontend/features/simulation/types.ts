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
  annualReturnPercent?: number;  // 0-30; 8 when omitted
  paths?: number;                // 200-10000; 2000 when omitted
  seed?: number;
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
  monthlyNetVolatility?: number | null;
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
  // false when the scenario leaves no savings for the goal; delayMonths is then 0
  reachable?: boolean;
  description: string;
}

export interface SimulationAssumptions {
  annualReturnPercent: number;
  annualReturnSource: "DEFAULT" | "USER";
  label: string;
}

// One fan-chart row: cumulative savings from today after `month` months.
export interface FanPoint {
  month: number;
  p10: number;
  p50: number;
  p90: number;
}

export interface Percentiles {
  p10: number;
  p50: number;
  p90: number;
}

// Months from now; null when that share of paths does not get there within the horizon.
export interface ReachMonths {
  p10: number | null;
  p50: number | null;
  p90: number | null;
}

export interface GoalOdds {
  goalName: string;
  remaining: number;
  targetDate: string;
  monthsToTarget: number;
  probabilityByTargetDate: number;
  baselineProbabilityByTargetDate: number;
  probabilityWithinHorizon: number;
  reachMonth: ReachMonths;
  baselineReachMonth: ReachMonths;
  reachProbabilityByMonth: number[];   // index 0 = month 1
}

export interface MonteCarloEvidence {
  method: string;
  paths: number;
  seed: number;
  horizonMonths: number;
  blockLength: number;
  residualMonths: number;
  window: [string, string] | null;
  baseMonthlyNet: number;
  ownMonthlySd: number | null;
  priorMonthlySd: number;
  monthlySd: number;
  ownWeight: number;
  volatilitySource: "OWN_HISTORY" | "SHRUNK_TO_PRIOR" | "PRIOR";
  note: string;
}

export interface MonteCarloResult {
  fan: FanPoint[];
  baselineFan: FanPoint[];
  goal: GoalOdds | null;
  flexibility: Percentiles;
  baselineFlexibility: Percentiles;
  evidence: MonteCarloEvidence;
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
  assumptions?: SimulationAssumptions;
  monteCarlo?: MonteCarloResult | null;
}
