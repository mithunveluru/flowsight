"use client";

// Shared primitives for the intelligence layer. Colors flow through --signal/--severity tokens.
import * as React from "react";
import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { PulseDot } from "@/components/motion/primitives";

export type Severity = "HIGH" | "MEDIUM" | "LOW";

// severity -> token; var() resolves lazily so dark mode adapts automatically
const SEVERITY_TOKEN: Record<Severity, { fg: string; soft: string; label: string }> = {
  HIGH:   { fg: "var(--severity-high)",   soft: "var(--severity-high-soft)",   label: "High signal" },
  MEDIUM: { fg: "var(--severity-medium)", soft: "var(--severity-medium-soft)", label: "Moderate signal" },
  LOW:    { fg: "var(--severity-low)",    soft: "var(--severity-low-soft)",    label: "Low signal" },
};

// SignalDot: canonical severity indicator. Static by default; pulse is opt-in and rare.
export function SignalDot({
  severity,
  pulse,
  className,
}: {
  severity: Severity;
  // opt into the pulsing ring; use sparingly
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

// ConfidenceBadge — small pill stating how strongly a signal is held.
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

// StatusMarker: calm alternative to a status pill (dot + uppercase label).
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

// SignalCard: container for a single inferred finding, with a left severity rail.
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
  // right-aligned impact figure
  metric?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  // tactile hover lift for clickable cards
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

// InsightCallout: the "what to do" box; tone="signal" tints it with the accent.
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

// EvidenceChips: dotted inline list of the supporting reasons.
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

// ForecastPanel/ForecastCell: a row of labelled future values with one optional accent cell.
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
  // accent for the single headline figure
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

// SignalSectionHeader: eyebrow + title + subtitle for intelligence sections.
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
