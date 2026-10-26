import { useEffect, useState } from "react";
import { AnimatedNumber } from "@/components/motion/primitives";
import type { FlexibilityTier } from "@/features/simulation/types";

// The arc is the one blue; only the two tight tiers switch to a status colour, and the label names the tier either way.
const TIER_COLOR: Record<FlexibilityTier, string> = {
  EXCELLENT: "var(--primary)",
  GOOD: "var(--primary)",
  FAIR: "var(--primary)",
  TIGHT: "var(--caution)",
  CONSTRAINED: "var(--warning)",
};

const TIER_LABEL: Record<FlexibilityTier, string> = {
  EXCELLENT: "Excellent",
  GOOD: "Good",
  FAIR: "Fair",
  TIGHT: "Tight",
  CONSTRAINED: "Constrained",
};

export function ScoreGauge({
  score,
  tier,
  size = 96,
}: {
  score: number;
  tier: FlexibilityTier;
  size?: number;
}) {
  const stroke = size < 84 ? 6 : 8;
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, score)) / 100;
  const color = `hsl(${TIER_COLOR[tier]})`;
  const numCls = size < 84 ? "text-ty-subhead" : "text-ty-lead";
  // start empty and fill on mount so the arc animates to its value
  const [filled, setFilled] = useState(false);
  useEffect(() => setFilled(true), []);

  return (
    <div
      className="relative flex shrink-0 items-center justify-center"
      style={{ height: size, width: size }}
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="hsl(var(--canvas))"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circ}
          strokeDashoffset={filled ? circ * (1 - pct) : circ}
          style={{ transition: "stroke-dashoffset var(--duration-reveal) var(--ease-standard)" }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <AnimatedNumber
          value={score}
          className={`${numCls} font-medium tabular-nums text-ink`}
          format={(v) => String(v)}
        />
        <span className="text-2xs font-medium" style={{ color }}>
          {TIER_LABEL[tier]}
        </span>
      </div>
    </div>
  );
}
