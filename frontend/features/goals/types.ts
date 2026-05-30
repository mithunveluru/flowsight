export type GoalStatus     = "ACTIVE" | "COMPLETED" | "ABANDONED";
export type PaceStatus     = "ON_PACE" | "AHEAD" | "BEHIND" | "COMPLETED" | "OVERDUE" | "ABANDONED";

export interface Goal {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  remaining: number;
  targetDate: string;
  icon: string | null;
  status: GoalStatus;
  percentComplete: number;
  daysRemaining: number;
  dailyPaceRequired: number;
  paceStatus: PaceStatus;
}

export interface GoalPayload {
  name: string;
  targetAmount: number;
  targetDate: string;
  currentAmount?: number;
  icon?: string;
}

export interface GoalContributionPayload {
  amount: number;
}
