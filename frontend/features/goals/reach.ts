import type { ForecastSeries } from "../analytics/types";

export type Reach = "reachable" | "likely" | "unlikely" | "unreachable";

export interface ReachAssessment {
  reach: Reach;
  requiredPerMonth: number;
  months: number;
  forecastMonths: number;
  expected: number;
  low80: number;
  high80: number;
}

const DAYS_PER_MONTH = 30.44;
const mean = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;

/**
 * Compares what the goal still needs each month with the forecast net savings
 * over the same months (at most as many as the forecast covers). Null when
 * there is nothing to assess.
 */
export function assessReach(
  goal: { remaining: number; daysRemaining: number; status: string },
  net: ForecastSeries | undefined
): ReachAssessment | null {
  if (!net || goal.status !== "ACTIVE" || goal.remaining <= 0 || goal.daysRemaining <= 0) return null;
  const future = net.points.filter((p) => !p.partial);
  if (future.length === 0) return null;
  const months = Math.max(1, Math.ceil(goal.daysRemaining / DAYS_PER_MONTH));
  const used = future.slice(0, months);
  const requiredPerMonth = goal.remaining / months;
  const expected = mean(used.map((p) => p.point));
  const low80 = mean(used.map((p) => p.interval.lower80));
  const high80 = mean(used.map((p) => p.interval.upper80));
  const reach: Reach =
    low80 >= requiredPerMonth ? "reachable" : high80 < requiredPerMonth ? "unreachable" : expected >= requiredPerMonth ? "likely" : "unlikely";
  return { reach, requiredPerMonth, months, forecastMonths: used.length, expected, low80, high80 };
}
