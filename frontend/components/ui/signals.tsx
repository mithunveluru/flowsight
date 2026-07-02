"use client";

// =============================================================================
// AI SIGNALS — the design language for FlowSight's intelligence layer.
//
// One vocabulary for everything the platform *infers* rather than records:
// behavioral signals, recommendations, predictions, detected anomalies, and
// long-range forecasts. Every page that surfaces an inference should compose
// these primitives instead of hand-rolling severity dots and card markup, so
// the "intelligence" reads as one coherent system.
//
// All color flows through the token system (--signal, --severity-*); nothing
// here hardcodes a Tailwind palette color, so dark mode and palette changes
// propagate automatically.
// =============================================================================

import * as React from "react";
import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { PulseDot } from "@/components/motion/primitives";

export type Severity = "HIGH" | "MEDIUM" | "LOW";

// Severity → token mapping. `var` channels resolve lazily, so each adapts to
// dark mode through the underlying base token it aliases.
const SEVERITY_TOKEN: Record<Severity, { fg: string; soft: string; label: string }> = {
  HIGH:   { fg: "var(--severity-high)",   soft: "var(--severity-high-soft)",   label: "High signal" },
  MEDIUM: { fg: "var(--severity-medium)", soft: "var(--severity-medium-soft)", label: "Moderate signal" },
  LOW:    { fg: "var(--severity-low)",    soft: "var(--severity-low-soft)",    label: "Low signal" },
};

// -----------------------------------------------------------------------------
// SignalDot — the canonical severity indicator. Static by default: severity is
// carried by color (and surrounding rail/badge), not motion. Pulsing is opt-in
// via `pulse` and intentionally rare — we avoid attention-seeking animation so
// the intelligence layer reads calm and premium. Replaces ad-hoc dot maps.
// -----------------------------------------------------------------------------
export function SignalDot({
  severity,
  pulse,
  className,
}: {
  severity: Severity;
  /** Opt into the pulsing ring. Off by default; use sparingly. */
  pulse?: boolean;
  className?: string;
}) {
  const { fg } = SEVERITY_TOKEN[severity];
  const shouldPulse = pulse ?? false;
  const color = `hsl(${fg})`;

  if (shouldPulse) return <PulseDot color={color} className={className} />;
  return (
    <span
      className={cn("inline-flex h-1.5 w-1.5 shrink-0 rounded-full", className)}
      style={{ backgroundColor: color }}
      aria-hidden="true"
    />
  );
}

// -----------------------------------------------------------------------------
// ConfidenceBadge — small pill stating how strongly a signal is held.
// -----------------------------------------------------------------------------
export function ConfidenceBadge({
  severity,
  label,
  className,
}: {
  severity: Severity;
  label?: string;
  className?: string;
}) {
  const { fg, soft, label: defaultLabel } = SEVERITY_TOKEN[severity];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium",
        className
      )}
      style={{ backgroundColor: `hsl(${soft})`, color: `hsl(${fg})` }}
    >
      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: `hsl(${fg})` }} />
      {label ?? defaultLabel}
    </span>
  );
}

// -----------------------------------------------------------------------------
// StatusMarker — the calm alternative to a status pill. A small dot (inheriting
// the current text color) plus a compact uppercase label. Communicates status
// through color + typography instead of a boxed badge, so tables and cards stay
// quiet and premium. Pass a Tailwind text-color via `className`, or an explicit
// CSS `color` (e.g. a design token) for the signals palette.
// -----------------------------------------------------------------------------
export function StatusMarker({
  label,
  color,
  className,
}: {
  label: string;
  color?: string;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap text-[11px] font-semibold uppercase tracking-wide",
        className
      )}
      style={color ? { color } : undefined}
    >
      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-current" aria-hidden="true" />
      {label}
    </span>
  );
}

// -----------------------------------------------------------------------------
// SignalCard — the premium container for a single inferred finding. A hairline
// severity rail on the left gives the intelligence layer its identity without
// painting whole surfaces. Compose the children freely; `metric` floats to the
// top-right for impact figures.
// -----------------------------------------------------------------------------
export function SignalCard({
  severity = "LOW",
  icon,
  title,
  metric,
  children,
  className,
  interactive,
}: {
  severity?: Severity;
  icon?: React.ReactNode;
  title: React.ReactNode;
  /** Right-aligned impact figure (e.g. ₹420/mo). */
  metric?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  /** Adds the tactile hover lift for clickable cards. */
  interactive?: boolean;
}) {
  const { fg, soft } = SEVERITY_TOKEN[severity];
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl pl-5 pr-5 py-5",
        interactive ? "card-tactile" : "card-refined",
        className
      )}
    >
      {/* Severity rail */}
      <span
        aria-hidden="true"
        className="absolute inset-y-0 left-0 w-[3px]"
        style={{ background: `hsl(${fg})`, opacity: 0.9 }}
      />
      <div className="flex items-start gap-3.5">
        {icon && (
          <span
            className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
            style={{ backgroundColor: `hsl(${soft})`, color: `hsl(${fg})` }}
          >
            {icon}
          </span>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <p className="text-sm font-semibold text-foreground leading-snug">{title}</p>
              <SignalDot severity={severity} className="mt-px" />
            </div>
            {metric && <div className="shrink-0 text-right">{metric}</div>}
          </div>
          {children && <div className="mt-1.5">{children}</div>}
        </div>
      </div>
    </div>
  );
}

// -----------------------------------------------------------------------------
// InsightCallout — the "here's what to do" box. Quiet by default; pass
// `tone="signal"` to tint it with the intelligence accent.
// -----------------------------------------------------------------------------
export function InsightCallout({
  children,
  icon,
  tone = "neutral",
  className,
}: {
  children: React.ReactNode;
  icon?: React.ReactNode;
  tone?: "neutral" | "signal";
  className?: string;
}) {
  const tinted = tone === "signal";
  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-lg px-3 py-2.5 text-xs leading-relaxed",
        tinted ? "text-foreground" : "bg-muted/50 text-foreground",
        className
      )}
      style={tinted ? { backgroundColor: "hsl(var(--signal-soft))" } : undefined}
    >
      <span
        className="mt-0.5 shrink-0"
        style={tinted ? { color: "hsl(var(--signal))" } : { color: "hsl(var(--muted-foreground))" }}
      >
        {icon ?? <ArrowRight className="h-3 w-3" strokeWidth={2} />}
      </span>
      <span>{children}</span>
    </div>
  );
}

// -----------------------------------------------------------------------------
// EvidenceChips — the supporting "why we think this" trail, rendered as a
// dotted inline list. Keeps reasoning visible and trustworthy.
// -----------------------------------------------------------------------------
export function EvidenceChips({ items, className }: { items: string[]; className?: string }) {
  if (!items.length) return null;
  return (
    <div className={cn("flex flex-wrap items-center gap-y-1 text-[11px] text-muted-foreground", className)}>
      {items.map((e, i) => (
        <span key={i} className="inline-flex items-center">
          {i > 0 && <span className="mx-1.5 text-muted-foreground/40">·</span>}
          {e}
        </span>
      ))}
    </div>
  );
}

// -----------------------------------------------------------------------------
// ForecastPanel / ForecastCell — the projection timeline. Generalizes the
// consequence-projection grid: a row of labelled future values with one
// optional accented "headline" cell.
// -----------------------------------------------------------------------------
export function ForecastPanel({
  children,
  columns = 4,
  className,
}: {
  children: React.ReactNode;
  columns?: 2 | 3 | 4;
  className?: string;
}) {
  const cols = columns === 2 ? "grid-cols-2" : columns === 3 ? "grid-cols-2 sm:grid-cols-3" : "grid-cols-2 lg:grid-cols-4";
  return (
    <div
      className={cn("grid divide-x divide-y border-t lg:divide-y-0", cols, className)}
      style={{ borderColor: "hsl(var(--border))" }}
    >
      {children}
    </div>
  );
}

export function ForecastCell({
  label,
  value,
  hint,
  accent,
}: {
  label: string;
  value: React.ReactNode;
  hint?: string;
  /** Render in the intelligence accent — use for the single headline figure. */
  accent?: boolean;
}) {
  return (
    <div className="px-5 py-3.5" style={{ borderColor: "hsl(var(--border))" }}>
      <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">{label}</p>
      <p
        className="mt-1 text-base font-semibold tabular-nums"
        style={accent ? { color: "hsl(var(--signal))" } : { color: "hsl(var(--foreground))" }}
      >
        {value}
      </p>
      {hint && <p className="mt-0.5 text-[10px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

// -----------------------------------------------------------------------------
// SignalSectionHeader — consistent eyebrow + title + subtitle across every
// intelligence section.
// -----------------------------------------------------------------------------
export function SignalSectionHeader({
  icon,
  title,
  subtitle,
  action,
  className,
}: {
  icon?: React.ReactNode;
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("section-header items-center", className)}>
      <div className="flex items-center gap-2 min-w-0">
        {icon && <span style={{ color: "hsl(var(--signal))" }}>{icon}</span>}
        <p className="text-sm font-semibold text-foreground">{title}</p>
        {subtitle && <span className="truncate text-xs text-muted-foreground">· {subtitle}</span>}
      </div>
      {action}
    </div>
  );
}
