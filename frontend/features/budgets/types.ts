import type { TransactionCategory } from "@/features/transactions/types";

export type BudgetStatus = "ON_TRACK" | "NEAR_LIMIT" | "OVER" | "PROJECTED_OVER";

export interface Budget {
  id: string;
  category: TransactionCategory | null;   // null = overall budget
  categoryDisplayName: string;
  monthlyLimit: number;
  rollover: boolean;
  isActive: boolean;
  currentSpend: number;
  remaining: number;
  percentUsed: number;
  projectedTotal: number;
  daysRemaining: number;
  status: BudgetStatus;
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
