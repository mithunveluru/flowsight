"use client";

import * as React from "react";
import { useReducedMotion } from "framer-motion";
import { DURATION } from "@/components/motion/css-motion";
import { EASE_STANDARD } from "@/components/motion/tokens";
import { cubicBezier, tween } from "@/components/motion/tween";
import { useInViewOnce } from "@/components/motion/use-in-view";
import { ChartSkeleton } from "@/components/ui/chart-frame";
import { EmptyState, ErrorState } from "@/components/ui/states";
import { cn } from "@/lib/utils";
import { blend, type MarkerShape } from "./chart-core";

const standard = cubicBezier(...EASE_STANDARD);

export function useWidth<T extends HTMLElement>() {
  const ref = React.useRef<T>(null);
  const [width, setWidth] = React.useState(0);
  React.useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(el.clientWidth);
    const ro = new ResizeObserver(([entry]) => setWidth(Math.round(entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/** True once the element has been on screen; charts draw in from that moment. */
export function useSeen<T extends Element>() {
  const ref = React.useRef<T>(null);
  const seen = useInViewOnce(ref);
  return [ref, seen] as const;
}

/**
 * Returns `target` after easing from the previous values, so a chart whose data changes
 * moves to its new shape instead of redrawing. The first value, a change in length and
 * reduced motion all apply at once.
 */
export function useMorph(target: number[]): number[] {
  const reduced = useReducedMotion();
  const [shown, setShown] = React.useState(target);
  const current = React.useRef(target);

  React.useEffect(() => {
    const from = current.current;
    if (from === target) return;
    if (reduced || from.length !== target.length) {
      current.current = target;
      setShown(target);
      return;
    }
    return tween(DURATION.slow, standard, (t) => {
      const next = blend(from, target, t);
      current.current = next;
      setShown(next);
    });
  }, [target, reduced]);

  return shown;
}

export function Marker({
  shape,
  x,
  y,
  r = 3.5,
  color,
}: {
  shape: MarkerShape;
  x: number;
  y: number;
  r?: number;
  color: string;
}) {
  const common = { fill: color, stroke: "hsl(var(--canvas))", strokeWidth: 1.5 } as const;
  if (shape === "square") return <rect x={x - r} y={y - r} width={r * 2} height={r * 2} {...common} />;
  if (shape === "triangle")
    return <polygon points={`${x},${y - r - 0.5} ${x + r + 0.5},${y + r} ${x - r - 0.5},${y + r}`} {...common} />;
  if (shape === "diamond")
    return <polygon points={`${x},${y - r - 1} ${x + r + 1},${y} ${x},${y + r + 1} ${x - r - 1},${y}`} {...common} />;
  return <circle cx={x} cy={y} r={r} {...common} />;
}

/** Hover card for a chart; positioned by the chart, never animated, so it tracks the pointer exactly. */
export function TooltipCard({
  x,
  width,
  top = 0,
  children,
}: {
  x: number;
  width: number;
  top?: number;
  children: React.ReactNode;
}) {
  const flip = x > width * 0.6;
  return (
    <div
      role="presentation"
      className="pointer-events-none absolute z-10 min-w-36 max-w-56 rounded-md border border-border bg-popover p-sp-xl text-xs text-popover-foreground"
      style={{ left: x, top, transform: `translateX(${flip ? "calc(-100% - 12px)" : "12px"})` }}
    >
      {children}
    </div>
  );
}

/** Chart section on the canvas: title, description, the chart, a legend and a screen-reader summary. */
export function ChartPanel({
  title,
  description,
  summary,
  legend,
  actions,
  state = "ready",
  height = 280,
  emptyTitle = "Not enough data yet",
  emptyDescription,
  error,
  onRetry,
  as: Heading = "h3",
  className,
  children,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  summary?: string;
  legend?: React.ReactNode;
  actions?: React.ReactNode;
  state?: "loading" | "empty" | "error" | "ready";
  height?: number;
  emptyTitle?: React.ReactNode;
  emptyDescription?: React.ReactNode;
  error?: unknown;
  onRetry?: () => void | Promise<unknown>;
  as?: "h2" | "h3" | "h4";
  className?: string;
  children?: React.ReactNode;
}) {
  const id = React.useId();
  const summaryId = summary ? `${id}-summary` : undefined;
  return (
    <figure aria-labelledby={`${id}-title`} aria-describedby={summaryId} className={cn("m-0 min-w-0", className)}>
      <div className="mb-sp-wide flex flex-wrap items-start justify-between gap-sp-xl">
        <div className="min-w-0">
          <Heading id={`${id}-title`} className="text-ty-subhead text-ink">
            {title}
          </Heading>
          {description && <p className="mt-sp-xs max-w-prose text-sm text-ink-meta">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-sp-sm">{actions}</div>}
      </div>
      <div style={{ minHeight: height }} aria-busy={state === "loading" || undefined}>
        {state === "loading" && <ChartSkeleton height={height} />}
        {state === "empty" && <EmptyState compact title={emptyTitle} description={emptyDescription} />}
        {state === "error" && <ErrorState compact title="This chart didn't load" error={error} onRetry={onRetry} />}
        {state === "ready" && children}
      </div>
      {state === "ready" && legend && <div className="mt-sp-wide">{legend}</div>}
      {summary && (
        <p id={summaryId} className="sr-only">
          {summary}
        </p>
      )}
    </figure>
  );
}
