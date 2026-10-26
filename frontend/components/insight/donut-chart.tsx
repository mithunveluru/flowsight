"use client";

import * as React from "react";
import { m, useReducedMotion } from "framer-motion";
import { DURATION } from "@/components/motion/css-motion";
import { EASE_STANDARD } from "@/components/motion/tokens";
import { cn } from "@/lib/utils";
import { donutSlices } from "./chart-core";
import { useSeen } from "./chart-parts";

export interface DonutItem {
  key: string;
  label: string;
  value: number;
  // CSS colour from the chart ramp
  color: string;
}

const SIZE = 200;
const STROKE = 28;
const R = (SIZE - STROKE) / 2;
const C = 2 * Math.PI * R;

/** Donut that sweeps round once on first view, with every slice named, valued and shared out beside it. */
export function DonutChart({
  items,
  format,
  centerLabel,
  className,
}: {
  items: DonutItem[];
  format: (v: number) => string;
  centerLabel: string;
  className?: string;
}) {
  const [ref, seen] = useSeen<HTMLDivElement>();
  const reduced = useReducedMotion();
  const [active, setActive] = React.useState<string | null>(null);
  const total = items.reduce((a, b) => a + b.value, 0);
  const slices = donutSlices(items.map((i) => i.value), C, 3);
  const current = items.find((i) => i.key === active);
  const maskId = React.useId();

  return (
    <div ref={ref} className={cn("flex flex-col items-center gap-sp-max sm:flex-row sm:items-center", className)}>
      <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
        <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label={`${centerLabel}: ${format(total)} across ${items.length} categories`}>
          <defs>
            <mask id={maskId}>
              <m.circle
                cx={SIZE / 2}
                cy={SIZE / 2}
                r={R}
                fill="none"
                stroke="white"
                strokeWidth={STROKE + 2}
                transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
                initial={{ pathLength: reduced ? 1 : 0, opacity: reduced ? 0 : 1 }}
                animate={{ pathLength: seen || reduced ? 1 : 0, opacity: 1 }}
                transition={{ duration: reduced ? DURATION.fast : DURATION.draw, ease: EASE_STANDARD }}
              />
            </mask>
          </defs>
          <g mask={`url(#${maskId})`}>
            {items.map((item, i) => (
              <circle
                key={item.key}
                cx={SIZE / 2}
                cy={SIZE / 2}
                r={R}
                fill="none"
                stroke={item.color}
                strokeWidth={active === item.key ? STROKE + 4 : STROKE}
                strokeDasharray={`${slices[i].length} ${C - slices[i].length}`}
                strokeDashoffset={-slices[i].start}
                transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
                opacity={active && active !== item.key ? 0.4 : 1}
                onPointerEnter={() => setActive(item.key)}
                onPointerLeave={() => setActive(null)}
              />
            ))}
          </g>
        </svg>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center px-sp-giant text-center">
          <span className="max-w-full truncate text-xs text-ink-meta">{current ? current.label : centerLabel}</span>
          <span className="text-ty-subhead tabular-nums text-ink">{format(current ? current.value : total)}</span>
          {current && total > 0 && <span className="text-xs tabular-nums text-ink-meta">{((current.value / total) * 100).toFixed(0)}%</span>}
        </div>
      </div>

      <ul className="w-full min-w-0 flex-1 space-y-sp-sm" onPointerLeave={() => setActive(null)}>
        {items.map((item) => (
          <li
            key={item.key}
            onPointerEnter={() => setActive(item.key)}
            className={cn(
              "flex items-center justify-between gap-sp-xl rounded-sm px-sp-sm py-sp-xs text-sm transition-colors duration-fast",
              active === item.key && "bg-chrome"
            )}
          >
            <span className="flex min-w-0 items-center gap-sp-sm">
              <span aria-hidden="true" className="h-3 w-3 shrink-0 rounded-sm" style={{ backgroundColor: item.color }} />
              <span className="truncate text-ink">{item.label}</span>
            </span>
            <span className="shrink-0 tabular-nums text-ink-meta">
              {total > 0 ? `${((item.value / total) * 100).toFixed(0)}%` : "0%"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
