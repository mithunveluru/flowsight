import type { TransactionCategory } from "@/features/transactions/types";

export type { MerchantRuleSuggestion } from "@/features/transactions/types";

export interface RulePreviewSample {
  id: string;
  transactionDate: string;
  merchant: string | null;
  category: TransactionCategory | null;
  amount: number;
  currency: string;
}

export interface RulePreview {
  ruleId: string;
  matchCount: number;
  // at most ten
  sample: RulePreviewSample[];
}

export interface RuleApplyResult {
  ruleId: string;
  updated: number;
}
