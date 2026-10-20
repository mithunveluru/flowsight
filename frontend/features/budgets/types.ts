import type { ForecastEvidence } from "@/features/analytics/types";
import type { TransactionCategory } from "@/features/transactions/types";

export type BudgetStatus = "ON_TRACK" | "NEAR_LIMIT" | "OVER" | "PROJECTED_OVER";

// How projectedTotal was made: spent so far plus the forecast's share of the month still ahead.
export interface BudgetPace {
  method: "forecast-remaining" | "month-to-date-pace" | "none";
  remainingShare: number;
  lower80: number;
  upper80: number;
  lower95: number;
  upper95: number;
  evidence: ForecastEvidence | null;
}

export interface Budget {
  id: string;
  category: TransactionCategory | null;   // null = overall budget
  categoryDisplayName: string;
  monthlyLimit: number;
  rollover: boolean;
  rolloverAmount?: number;   // carried from earlier months; 0 without rollover
  effectiveLimit?: number;   // monthlyLimit + rolloverAmount, never below 0
  isActive: boolean;
  currentSpend: number;
  remaining: number;
  percentUsed: number;
  projectedTotal: number;
  daysRemaining: number;
  status: BudgetStatus;
  pace?: BudgetPace;
}

export interface BudgetSummary {
  totalBudgeted: number;
  totalSpent: number;
  overallPercentUsed: number;
  budgetCount: number;
  overBudgetCount: number;
  budgets: Budget[];
}

export interface BudgetPayload {
  category?: TransactionCategory;
  monthlyLimit: number;
  rollover?: boolean;
}
