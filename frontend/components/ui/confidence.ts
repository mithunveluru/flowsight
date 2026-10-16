export type ConfidenceLevel = "high" | "medium" | "low";

export interface ConfidenceThresholds {
  high: number;
  medium: number;
}

// Presentation buckets only; features should pass the thresholds their backend uses.
export const DEFAULT_CONFIDENCE_THRESHOLDS: ConfidenceThresholds = { high: 0.8, medium: 0.5 };

export function confidenceLevel(
  probability: number | null | undefined,
  thresholds: ConfidenceThresholds = DEFAULT_CONFIDENCE_THRESHOLDS
): ConfidenceLevel | null {
  if (probability == null || !Number.isFinite(probability)) return null;
  const p = clampProbability(probability);
  if (p >= thresholds.high) return "high";
  if (p >= thresholds.medium) return "medium";
  return "low";
}

export function formatProbability(probability: number): string {
  return `${Math.round(clampProbability(probability) * 100)}%`;
}

function clampProbability(p: number): number {
  return Math.min(1, Math.max(0, p));
}
