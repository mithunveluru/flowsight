"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import { ChartSkeleton, EmptyState, ErrorState } from "./states";

export { ChartSkeleton };

export type ChartState = "loading" | "empty" | "error" | "ready";

/**
 * Card-framed chart with a screen-reader `summary` and loading/empty/error states.
 * It imports no chart library, so pages can pass a next/dynamic recharts child.
 */
export function ChartFrame({
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
  // what the chart shows, in words; read by screen readers instead of the SVG
  summary?: string;
  legend?: React.ReactNode;
  actions?: React.ReactNode;
  state?: ChartState;
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
  const titleId = `${id}-title`;
  const summaryId = summary ? `${id}-summary` : undefined;

  return (
    <figure
      aria-labelledby={titleId}
      aria-describedby={summaryId}
      className={cn("card-refined m-0 p-6", className)}
    >
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Heading id={titleId} className="text-title">
            {title}
          </Heading>
          {description && <p className="mt-0.5 text-caption">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>

      <div style={{ minHeight: height }} aria-busy={state === "loading" || undefined}>
        {state === "loading" && <ChartSkeleton height={height} />}
        {state === "empty" && (
          <EmptyState compact title={emptyTitle} description={emptyDescription} className="h-full" />
        )}
        {state === "error" && (
          <ErrorState compact title="This chart didn't load" error={error} onRetry={onRetry} />
        )}
        {state === "ready" && <div style={{ height }}>{children}</div>}
      </div>

      {state === "ready" && legend && <div className="mt-4">{legend}</div>}
      {summary && (
        <p id={summaryId} className="sr-only">
          {summary}
        </p>
      )}
    </figure>
  );
}

export interface LegendItem {
  label: string;
  // any CSS color, normally hsl(var(--chart-*))
  color: string;
  // dashed for projections/forecasts so the series differ by more than color
  variant?: "solid" | "dashed";
}

export function ChartLegend({ items, className }: { items: LegendItem[]; className?: string }) {
  return (
    <ul className={cn("flex flex-wrap items-center justify-center gap-x-5 gap-y-2", className)}>
      {items.map((item) => (
        <li key={item.label} className="inline-flex items-center gap-2 text-xs text-muted-foreground">
          <svg width="18" height="8" aria-hidden="true" className="shrink-0">
            <line
              x1="1"
              y1="4"
              x2="17"
              y2="4"
              stroke={item.color}
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeDasharray={item.variant === "dashed" ? "3 3" : undefined}
            />
          </svg>
          {item.label}
        </li>
      ))}
    </ul>
  );
}
