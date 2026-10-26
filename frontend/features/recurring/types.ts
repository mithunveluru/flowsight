export type RecurringStatus   = "ACTIVE" | "DUE_SOON" | "OVERDUE" | "MISSED";
export type RecurringPeriod   = "WEEKLY" | "BIWEEKLY" | "MONTHLY" | "QUARTERLY" | "ANNUAL";
export type ConfidenceTier    = "HIGH" | "MEDIUM" | "POSSIBLE";

export interface RecurringPattern {
  id: string;
  merchant: string;
  period: RecurringPeriod;
  periodLabel: string;
  estimatedAmount: number;
  annualCost: number;
  monthlyEquivalent: number;
  lastSeenDate: string;
  nextExpectedDate: string | null;
  occurrenceCount: number;
  confidence: number;
  confidenceTier: ConfidenceTier;
  isCancellationCandidate: boolean;
  isDismissed: boolean;
  isUserConfirmed: boolean;
  status: RecurringStatus;
  daysUntilNext: number;
  scoredBy?: "MODEL" | "RULES";
  /** Calibrated probability the group recurs; null for rule patterns. */
  probability?: number | null;
  nextExpectedLow?: string | null;
  nextExpectedHigh?: string | null;
  modelVersion?: string | null;
  chargesPerPeriod?: number;
}
