import { AnimatedNumber } from "@/components/motion/primitives";
import type { FlexibilityTier } from "@/features/simulation/types";

// Tier -> token color. Healthy tiers read positive; tighter tiers warm up.
const TIER_COLOR: Record<FlexibilityTier, string> = {
  EXCELLENT: "var(--positive)",
  GOOD: "var(--positive)",
  FAIR: "var(--signal)",
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
  const numCls = size < 84 ? "text-xl" : "text-2xl";

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
          stroke="hsl(var(--muted))"
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
          strokeDashoffset={circ * (1 - pct)}
          style={{ transition: "stroke-dashoffset 700ms cubic-bezier(0.22,1,0.36,1)" }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <AnimatedNumber
          value={score}
          className={`${numCls} font-semibold tabular-nums text-foreground`}
          format={(v) => String(v)}
        />
        <span className="text-[10px] font-medium" style={{ color }}>
          {TIER_LABEL[tier]}
        </span>
      </div>
    </div>
  );
}
