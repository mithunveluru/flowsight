"use client";

import * as React from "react";
import { AlertTriangle, Inbox, RotateCw } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div aria-hidden="true" className={cn("skeleton rounded-sm", className)} {...props} />;
}

type LoadingVariant = "card" | "list" | "table" | "stats" | "chart" | "text";

function LoadingShape({ variant, rows }: { variant: LoadingVariant; rows: number }) {
  switch (variant) {
    case "stats":
      return (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: rows }, (_, i) => (
            <div key={i} className="card-refined space-y-3 p-5">
              <Skeleton className="h-3 w-20" />
              <Skeleton className="h-7 w-28" />
            </div>
          ))}
        </div>
      );
    case "list":
      return (
        <div className="space-y-2">
          {Array.from({ length: rows }, (_, i) => (
            <div key={i} className="flex items-center gap-3 py-2">
              <Skeleton className="h-8 w-8 rounded-md" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-3 w-1/3" />
                <Skeleton className="h-3 w-1/5" />
              </div>
              <Skeleton className="h-4 w-16" />
            </div>
          ))}
        </div>
      );
    case "table":
      return (
        <div className="divide-y divide-border">
          <div className="flex gap-6 px-4 py-3">
            {[16, 32, 20, 12].map((w, i) => (
              <Skeleton key={i} className="h-3" style={{ width: `${w}%` }} />
            ))}
          </div>
          {Array.from({ length: rows }, (_, i) => (
            <div key={i} className="flex gap-6 px-4 py-4">
              {[16, 32, 20, 12].map((w, j) => (
                <Skeleton key={j} className="h-3.5" style={{ width: `${w}%` }} />
              ))}
            </div>
          ))}
        </div>
      );
    case "chart":
      return <ChartBars />;
    case "text":
      return (
        <div className="space-y-2.5">
          {Array.from({ length: rows }, (_, i) => (
            <Skeleton key={i} className={cn("h-3.5", i === rows - 1 ? "w-2/3" : "w-full")} />
          ))}
        </div>
      );
    case "card":
    default:
      return (
        <div className="card-refined space-y-4 p-6">
          <Skeleton className="h-4 w-40" />
          {Array.from({ length: rows }, (_, i) => (
            <Skeleton key={i} className={cn("h-3.5", i === rows - 1 ? "w-1/2" : "w-full")} />
          ))}
        </div>
      );
  }
}

const BAR_HEIGHTS = [45, 70, 55, 85, 60, 75, 40, 90, 65, 50, 80, 58];

function ChartBars({ height = 240 }: { height?: number }) {
  return (
    <div className="flex items-end gap-2" style={{ height }} aria-hidden="true">
      {BAR_HEIGHTS.map((h, i) => (
        <Skeleton key={i} className="flex-1 rounded-sm" style={{ height: `${h}%` }} />
      ))}
    </div>
  );
}

/** Loading placeholder for a chart; use it as the `loading` fallback of next/dynamic. */
export function ChartSkeleton({ height = 240, label = "Loading chart" }: { height?: number; label?: string }) {
  return (
    <div role="status" aria-busy="true">
      <span className="sr-only">{label}</span>
      <ChartBars height={height} />
    </div>
  );
}

/**
 * Skeleton shaped like the content it stands in for. Announces `label` once
 * to screen readers; the shapes themselves are hidden from them.
 */
export function LoadingState({
  variant = "card",
  rows = 3,
  label = "Loading",
  className,
}: {
  variant?: LoadingVariant;
  rows?: number;
  label?: string;
  className?: string;
}) {
  return (
    <div role="status" aria-busy="true" className={className}>
      <span className="sr-only">{label}</span>
      <LoadingShape variant={variant} rows={rows} />
    </div>
  );
}

type HeadingLevel = "h2" | "h3" | "h4";

export function EmptyState({
  icon,
  title,
  description,
  action,
  secondaryAction,
  compact,
  as: Heading = "h3",
  className,
}: {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  secondaryAction?: React.ReactNode;
  // no card chrome, smaller padding; for use inside an existing card or chart
  compact?: boolean;
  as?: HeadingLevel;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center",
        compact ? "px-4 py-8" : "rounded-lg bg-chrome p-sp-huge",
        className
      )}
    >
      <span
        aria-hidden="true"
        className="mb-4 flex h-10 w-10 items-center justify-center rounded-md bg-band text-muted-foreground [&_svg]:h-5 [&_svg]:w-5"
      >
        {icon ?? <Inbox strokeWidth={1.75} />}
      </span>
      <Heading className={compact ? "text-title" : "text-headline"}>{title}</Heading>
      {description && <p className="mt-1.5 max-w-sm text-supporting">{description}</p>}
      {(action || secondaryAction) && (
        <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
          {action}
          {secondaryAction}
        </div>
      )}
    </div>
  );
}

function errorMessage(error: unknown): string | null {
  if (!error) return null;
  if (typeof error === "string") return error;
  if (error instanceof Error && error.message) return error.message;
  return null;
}

/**
 * Failure state with an optional retry. If `onRetry` returns a promise the
 * button shows progress until it settles. Rendered as role="alert" so the
 * failure is announced when it appears.
 */
export function ErrorState({
  title = "This didn't load",
  description = "Something went wrong on our side. Your data is safe.",
  error,
  onRetry,
  retryLabel = "Try again",
  compact,
  className,
}: {
  title?: React.ReactNode;
  description?: React.ReactNode;
  // shown as a detail line when it carries a message
  error?: unknown;
  onRetry?: () => void | Promise<unknown>;
  retryLabel?: string;
  compact?: boolean;
  className?: string;
}) {
  const [retrying, setRetrying] = React.useState(false);
  const detail = errorMessage(error);

  const retry = async () => {
    if (!onRetry) return;
    setRetrying(true);
    try {
      await onRetry();
    } finally {
      setRetrying(false);
    }
  };

  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center text-center",
        compact ? "px-4 py-8" : "rounded-lg bg-chrome p-sp-huge",
        className
      )}
    >
      <span
        aria-hidden="true"
        className="mb-4 flex h-10 w-10 items-center justify-center rounded-md bg-warning-soft text-warning"
      >
        <AlertTriangle className="h-5 w-5" strokeWidth={1.75} />
      </span>
      <p className={compact ? "text-title" : "text-headline"}>{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-supporting">{description}</p>}
      {detail && <p className="mt-2 max-w-sm text-caption">{detail}</p>}
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-5" onClick={retry} disabled={retrying}>
          <RotateCw className={cn(retrying && "animate-spin")} aria-hidden="true" />
          {retrying ? "Retrying" : retryLabel}
        </Button>
      )}
    </div>
  );
}
