import { CircleAlert, CircleCheck, CircleDot } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  confidenceLevel,
  formatProbability,
  type ConfidenceLevel,
  type ConfidenceThresholds,
} from "./confidence";

export type { ConfidenceLevel, ConfidenceThresholds };

const LEVEL_STYLE: Record<ConfidenceLevel, { className: string; icon: typeof CircleCheck }> = {
  high: { className: "border-positive/25 bg-positive-soft text-positive", icon: CircleCheck },
  medium: { className: "border-caution/25 bg-caution-soft text-caution", icon: CircleDot },
  low: { className: "border-warning/25 bg-warning-soft text-warning", icon: CircleAlert },
};

const DEFAULT_LABELS: Record<ConfidenceLevel, string> = {
  high: "High confidence",
  medium: "Medium confidence",
  low: "Low confidence",
};

/**
 * Shows a calibrated probability as a level (icon shape + text + color, never
 * color alone). Pass the feature's own `thresholds`, or `level` when the
 * backend has already decided the bucket. Renders nothing without a value.
 */
export function ConfidenceChip({
  value,
  level: levelOverride,
  thresholds,
  labels,
  showValue = false,
  className,
}: {
  value?: number | null;
  level?: ConfidenceLevel;
  thresholds?: ConfidenceThresholds;
  labels?: Partial<Record<ConfidenceLevel, string>>;
  showValue?: boolean;
  className?: string;
}) {
  const level = levelOverride ?? confidenceLevel(value, thresholds);
  if (!level) return null;
  const { className: tone, icon: Icon } = LEVEL_STYLE[level];
  const label = labels?.[level] ?? DEFAULT_LABELS[level];
  const pct = showValue && value != null && Number.isFinite(value) ? formatProbability(value) : null;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 whitespace-nowrap rounded-sm border px-2 py-0.5 text-xs font-medium",
        tone,
        className
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" strokeWidth={2} aria-hidden="true" />
      {label}
      {pct && <span className="tabular-nums">· {pct}</span>}
    </span>
  );
}
