"use client";

import * as React from "react";
import { m, useReducedMotion } from "framer-motion";
import { DURATION } from "@/components/motion/css-motion";
import { EASE_STANDARD } from "@/components/motion/tokens";
import { useInViewOnce } from "@/components/motion/use-in-view";
import { cn } from "@/lib/utils";

export type ProgressTone = "primary" | "navy" | "warning";

const FILL: Record<ProgressTone, string> = {
  primary: "bg-primary",
  navy: "bg-navy",
  warning: "bg-warning",
};

/**
 * Bar that grows to its value from the left the first time it is seen, then follows later
 * changes. Scales with transform only; under reduced motion it fades instead.
 */
export function ProgressBar({
  value,
  tone = "primary",
  label,
  valueText,
  className,
  size = "md",
  children,
}: {
  // 0 to 100
  value: number;
  tone?: ProgressTone;
  label: string;
  valueText?: string;
  className?: string;
  size?: "sm" | "md";
  // extra marks drawn over the track, in the same coordinate space as the bar
  children?: React.ReactNode;
}) {
  const ref = React.useRef<HTMLDivElement>(null);
  const seen = useInViewOnce(ref);
  const reduced = useReducedMotion();
  const pct = Math.min(100, Math.max(0, Number.isFinite(value) ? value : 0));
  const target = seen ? pct / 100 : reduced ? pct / 100 : 0;

  return (
    <div
      ref={ref}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct)}
      aria-valuetext={valueText}
      className={cn("relative w-full overflow-hidden rounded-sm bg-band", size === "sm" ? "h-1.5" : "h-2", className)}
    >
      <m.div
        className={cn("h-full w-full origin-left", FILL[tone])}
        initial={{ scaleX: reduced ? pct / 100 : 0, opacity: reduced ? 0 : 1 }}
        animate={{ scaleX: target, opacity: 1 }}
        transition={{ duration: reduced ? DURATION.fast : DURATION.draw, ease: EASE_STANDARD }}
      />
      {children}
    </div>
  );
}
