"use client";

import * as React from "react";
import { m, useReducedMotion } from "framer-motion";
import { DURATION } from "@/components/motion/primitives";
import { EASE_STANDARD } from "@/components/motion/tokens";
import { cn } from "@/lib/utils";
import { useSeen } from "./chart-parts";

export interface BarItem {
  key: string;
  label: string;
  value: number;
  // second line under the label
  detail?: string;
}

/** Horizontal bars with the value written at the end of each one; bars grow from the left, one after another. */
export function BarList({
  items,
  format,
  color = "hsl(var(--chart-1))",
  className,
}: {
  items: BarItem[];
  format: (v: number) => string;
  color?: string;
  className?: string;
}) {
  const [ref, seen] = useSeen<HTMLUListElement>();
  const reduced = useReducedMotion();
  const [active, setActive] = React.useState<string | null>(null);
  const max = Math.max(1, ...items.map((i) => i.value));

  return (
    <ul ref={ref} className={cn("space-y-sp-xl", className)} onPointerLeave={() => setActive(null)}>
      {items.map((item, i) => (
        <li
          key={item.key}
          onPointerEnter={() => setActive(item.key)}
          className={cn("transition-opacity duration-fast", active && active !== item.key && "opacity-50")}
        >
          <div className="flex items-baseline justify-between gap-sp-xl text-sm">
            <span className="min-w-0 truncate text-ink" title={item.label}>
              {item.label}
            </span>
            <span className="shrink-0 tabular-nums text-ink">{format(item.value)}</span>
          </div>
          <div className="mt-sp-xs h-2 w-full overflow-hidden rounded-sm bg-band" aria-hidden="true">
            <m.div
              className="h-full w-full origin-left"
              style={{ backgroundColor: color }}
              initial={{ scaleX: reduced ? item.value / max : 0, opacity: reduced ? 0 : 1 }}
              animate={{ scaleX: seen || reduced ? item.value / max : 0, opacity: 1 }}
              transition={{
                duration: reduced ? DURATION.fast : DURATION.reveal,
                delay: reduced ? 0 : i * 0.06,
                ease: EASE_STANDARD,
              }}
            />
          </div>
          {item.detail && <p className="mt-sp-xs text-xs text-ink-meta">{item.detail}</p>}
        </li>
      ))}
    </ul>
  );
}
