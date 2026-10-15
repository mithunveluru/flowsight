export type TrendIntent = "higher-is-better" | "lower-is-better" | "neutral";
export type TrendTone = "good" | "bad" | "flat" | "neutral";

// Below this a change rounds to 0.0% at one decimal, so it reads as flat.
const FLAT_EPSILON = 0.05;

export function trendTone(percent: number, intent: TrendIntent): TrendTone {
  if (Math.abs(percent) < FLAT_EPSILON) return "flat";
  if (intent === "neutral") return "neutral";
  const up = percent > 0;
  return up === (intent === "higher-is-better") ? "good" : "bad";
}

export function formatPercentDelta(percent: number, digits = 1): string {
  if (Math.abs(percent) < FLAT_EPSILON) return "0%";
  const sign = percent > 0 ? "+" : "−";
  return `${sign}${Math.abs(percent).toFixed(digits)}%`;
}
