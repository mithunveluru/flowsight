"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  CreditCard,
  RefreshCw,
  TrendingDown,
  TrendingUp,
  XCircle,
} from "lucide-react";
import dynamic from "next/dynamic";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { forecastEvidence } from "@/features/insights/evidence";
import { ChartSkeleton } from "@/components/ui/chart-frame";
import { analyticsApi } from "@/features/analytics/api";
import type {
  ActivityBounds,
  AnalyticsOverview,
  AnalyticsTrend,
  DateRangePreset,
  MonthlyTrendPoint,
} from "@/features/analytics/types";
import {
  AnimatedNumber,
  FadeIn,
  RevealOnScroll,
  StaggerContainer,
  StaggerItem,
} from "@/components/motion/primitives";
import { cn } from "@/lib/utils";

const chartLoading = () => <ChartSkeleton height={260} />;
const SpendTrendChart = dynamic(() => import("./charts").then((m) => m.SpendTrendChart), { ssr: false, loading: chartLoading });
const CategoryDonutChart = dynamic(() => import("./charts").then((m) => m.CategoryDonutChart), { ssr: false, loading: chartLoading });
const TopMerchantsChart = dynamic(() => import("./charts").then((m) => m.TopMerchantsChart), { ssr: false, loading: chartLoading });

function formatINR(value: number, compact = false): string {
  if (compact) {
    if (value >= 100_000) return `₹${(value / 100_000).toFixed(1)}L`;
    if (value >= 1_000)   return `₹${(value / 1_000).toFixed(0)}K`;
    return `₹${value.toFixed(0)}`;
  }
  return `₹${Number(value).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function dateRange(preset: DateRangePreset): { from: string; to: string } {
  const today = new Date();
  const fmt = (d: Date) => d.toISOString().split("T")[0];
  const to = fmt(today);

  const from = new Date(today);
  switch (preset) {
    case "1M": from.setDate(1);                         break;
    case "3M": from.setMonth(from.getMonth() - 2); from.setDate(1); break;
    case "6M": from.setMonth(from.getMonth() - 5); from.setDate(1); break;
    case "12M":from.setMonth(from.getMonth() - 11); from.setDate(1);break;
  }
  return { from: fmt(from), to };
}

export default function AnalyticsPage() {
  const searchParams = useSearchParams();
  // deep links like ?from=&to= (from the CSV import banner)
  const customFrom = searchParams.get("from");
  const customTo   = searchParams.get("to");
  const hasCustomRange = !!(customFrom && customTo);

  const [preset, setPreset]     = useState<DateRangePreset>("1M");
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [trend, setTrend]       = useState<AnalyticsTrend | null>(null);
  const [bounds, setBounds]     = useState<ActivityBounds | null>(null);
  const [loading, setLoading]   = useState(true);
  const [error,   setError]     = useState<string | null>(null);

  const load = useCallback(async (p: DateRangePreset, overrideFrom?: string, overrideTo?: string) => {
    setLoading(true);
    setError(null);
    const { from, to } = overrideFrom && overrideTo
      ? { from: overrideFrom, to: overrideTo }
      : dateRange(p);
    const months = p === "1M" ? 12 : p === "3M" ? 12 : p === "6M" ? 12 : 24;
    try {
      const [ov, tr, ab] = await Promise.all([
        analyticsApi.getOverview(from, to),
        analyticsApi.getTrend(months),
        analyticsApi.getActivityBounds(),
      ]);
      setOverview(ov);
      setTrend(tr);
      setBounds(ab);
    } catch (e) {
      console.error("Analytics fetch failed", e);
      setError(e instanceof Error ? e.message : "Failed to load analytics data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (hasCustomRange) {
      load(preset, customFrom!, customTo!);
    } else {
      load(preset);
    }
  }, [load, preset, hasCustomRange, customFrom, customTo]);

  const isEmpty = !loading && !error && overview && overview.transactionCount === 0;

  // empty view but data in other months: offer a jump to the latest active month
  const dataElsewhere =
    isEmpty &&
    bounds &&
    !bounds.currentMonthHasData &&
    bounds.totalTransactionCount > 0 &&
    bounds.monthsWithActivity.length > 0;

  return (
    <div className="space-y-10">
      {/* Page header */}
      <FadeIn>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">Trends</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              How your money moves, where it goes, and the patterns behind it.
            </p>
          </div>
          <DateRangeSelector value={preset} onChange={(p) => setPreset(p)} />
        </div>
      </FadeIn>

      {/* Custom range banner — shown when arriving from a deep link */}
      {hasCustomRange && customFrom && customTo && (
        <FadeIn delay={0.04}>
          <CustomRangeBanner from={customFrom} to={customTo} />
        </FadeIn>
      )}

      {error ? (
        <FadeIn><ErrorState message={error} onRetry={() => load(preset)} /></FadeIn>
      ) : dataElsewhere ? (
        <FadeIn><DataElsewhereHint bounds={bounds!} /></FadeIn>
      ) : isEmpty ? (
        <FadeIn><EmptyState /></FadeIn>
      ) : (
        <>
          {/* Summary cards — animated counter values */}
          <FadeIn delay={0.08}>
            <SummaryCards overview={overview} loading={loading} />
          </FadeIn>

          {/* Behavioral alerts */}
          {overview && overview.alerts.length > 0 && (
            <RevealOnScroll delay={0.04}>
              <AlertsPanel alerts={overview.alerts} />
            </RevealOnScroll>
          )}

          {/* Monthly trend — full width, scroll-reveal */}
          <RevealOnScroll delay={0.06}>
            <Section title="Monthly cashflow" subtitle="Spend against income, over time">
              <SpendTrendChart trend={trend} loading={loading} />
              {trend && <ForecastNote trend={trend} />}
            </Section>
          </RevealOnScroll>

          {/* Category breakdown + Top merchants — scroll-reveal */}
          <div className="grid gap-6 lg:grid-cols-2">
            <RevealOnScroll delay={0.08}>
              <Section title="Where your money went" subtitle={`${preset === "1M" ? "This month" : "Selected period"}`}>
                <CategoryDonutChart overview={overview} loading={loading} />
              </Section>
            </RevealOnScroll>
            <RevealOnScroll delay={0.12}>
              <Section title="Where you spent most" subtitle="By total outflow">
                <TopMerchantsChart overview={overview} loading={loading} />
              </Section>
            </RevealOnScroll>
          </div>
        </>
      )}
    </div>
  );
}

const PRESETS: { value: DateRangePreset; label: string }[] = [
  { value: "1M",  label: "This month" },
  { value: "3M",  label: "3 months"   },
  { value: "6M",  label: "6 months"   },
  { value: "12M", label: "12 months"  },
];

function DateRangeSelector({
  value,
  onChange,
}: {
  value: DateRangePreset;
  onChange: (p: DateRangePreset) => void;
}) {
  return (
    <div
      className="flex items-center gap-0.5 rounded-lg border bg-card p-1"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      {PRESETS.map((p) => (
        <button
          key={p.value}
          onClick={() => onChange(p.value)}
          className={cn(
            "rounded-md px-3 py-1 text-xs font-medium transition-all duration-150",
            value === p.value
              ? "bg-primary text-primary-foreground"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          {p.label}
        </button>
      ))}
    </div>
  );
}

function SummaryCards({
  overview,
  loading,
}: {
  overview: AnalyticsOverview | null;
  loading: boolean;
}) {
  // each card carries its raw value for the animated counter
  const cards = overview
    ? [
        { label: "Total spend",  value: overview.totalSpend,        format: (v: number) => `₹${v.toLocaleString("en-IN")}` },
        { label: "Total income", value: overview.totalIncome,       format: (v: number) => `₹${v.toLocaleString("en-IN")}` },
        { label: "Net cashflow", value: overview.netCashflow,       format: (v: number) => `${v < 0 ? "−" : ""}₹${Math.abs(v).toLocaleString("en-IN")}` },
        { label: "Transactions", value: overview.transactionCount,  format: (v: number) => String(v) },
      ]
    : [];

  return (
    <div
      className="grid divide-x divide-y sm:divide-y-0 sm:grid-cols-2 lg:grid-cols-4 overflow-hidden rounded-xl border bg-card"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      {(loading || !overview ? [0, 1, 2, 3] : cards).map((card, idx) => (
        <div key={idx} className="px-6 py-5">
          {loading || !overview ? (
            <>
              <div className="skeleton h-3.5 w-20 rounded" />
              <div className="skeleton mt-2.5 h-8 w-28 rounded-md" />
            </>
          ) : (
            <>
              <p className="stat-label">{(card as {label: string}).label}</p>
              <p className="mt-1.5 stat-value">
                <AnimatedNumber
                  value={(card as {value: number}).value}
                  format={(card as {format: (v: number) => string}).format}
                />
              </p>
            </>
          )}
        </div>
      ))}
    </div>
  );
}

function AlertsPanel({ alerts }: { alerts: AnalyticsOverview["alerts"] }) {
  return (
    <div className="space-y-3">
      <p className="text-sm font-medium text-foreground">Worth noticing</p>
      <div
        className="rounded-xl border bg-card divide-y overflow-hidden"
        style={{ borderColor: "hsl(var(--border))" }}
      >
        {alerts.map((alert, i) => {
          const dotCls =
            alert.severity === "HIGH"   ? "bg-warning"
            : alert.severity === "MEDIUM" ? "bg-caution"
            : "bg-brand";
          return (
            <div key={i} className="flex items-center gap-3 px-5 py-3.5">
              <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", dotCls)} />
              <div className="min-w-0 flex-1">
                <p className="text-sm text-foreground">
                  <span className="font-medium">{alert.categoryDisplayName}:</span>{" "}
                  {alert.message}
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {formatINR(alert.currentAmount)} this period · avg {formatINR(alert.averageAmount)}/mo
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ForecastNote({ trend }: { trend: AnalyticsTrend }) {
  const f = trend.forecast;
  const next = trend.points.find((p) => p.projected && p.range);
  if (!f || !next?.range) return null;
  const rows = [
    { key: "spend", label: "Spend", ev: f.spend, point: next.spend, r: next.range.spend },
    { key: "income", label: "Income", ev: f.income, point: next.income, r: next.range.income },
  ];
  return (
    <div className="mt-4 space-y-1 border-t border-border pt-3 text-xs text-muted-foreground">
      <p>Dashed lines are projections for {next.label}, with the likely 80% range.</p>
      {rows.map((r) => (
        <p key={r.key} className="flex flex-wrap items-center gap-x-1">
          <span>
            {r.label}: <span className="font-medium tabular-nums text-foreground">{formatINR(r.point)}</span> (
            {formatINR(r.r.lower80)} to {formatINR(r.r.upper80)})
          </span>
          <EvidenceDrawer
            title={`${r.label} projection for ${next.label}`}
            value={formatINR(r.point)}
            evidence={forecastEvidence(r.ev, f.asOf)}
            trigger={<EvidenceTrigger label={`the ${r.label.toLowerCase()} projection`} />}
          />
        </p>
      ))}
    </div>
  );
}

function Section({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className="rounded-xl border bg-card p-6"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="mb-5">
        <p className="text-sm font-semibold text-foreground">{title}</p>
        {subtitle && <p className="text-xs text-muted-foreground mt-1">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="rounded-lg border border-warning/25 bg-warning-soft px-6 py-12 text-center">
      <XCircle className="mx-auto h-10 w-10 text-warning/50 mb-3" />
      <p className="text-sm font-medium text-warning">We could not load your overview</p>
      <p className="mt-1 text-xs text-warning max-w-md mx-auto">{message}</p>
      <button
        onClick={onRetry}
        className="mt-5 inline-flex items-center gap-1.5 rounded-md bg-warning px-4 py-2 text-xs font-medium text-white hover:bg-warning transition-colors"
      >
        <RefreshCw className="h-3 w-3" />
        Retry
      </button>
    </div>
  );
}

// jump to the latest active month when the default view is empty
function DataElsewhereHint({ bounds }: { bounds: ActivityBounds }) {
  // monthsWithActivity is "YYYY-MM" newest first
  const mostRecent = bounds.monthsWithActivity[0];
  const [yr, mo] = mostRecent.split("-").map(Number);
  const monthStart = new Date(yr, mo - 1, 1);
  const monthEnd   = new Date(yr, mo, 0); // last day of month
  const fmt = (d: Date) => d.toISOString().split("T")[0];
  const label = monthStart.toLocaleDateString("en-IN", { month: "long", year: "numeric" });

  return (
    <div
      className="rounded-xl border bg-card p-8 lg:p-10"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
          <BarChart3 className="h-4 w-4" strokeWidth={1.75} />
        </div>
        <div className="flex-1">
          <p className="text-sm font-semibold text-foreground">
            Your data is here, just outside the current range
          </p>
          <p className="mt-1.5 text-sm text-muted-foreground">
            You have <span className="font-medium text-foreground tabular-nums">
              {bounds.totalTransactionCount}
            </span> transaction{bounds.totalTransactionCount === 1 ? "" : "s"} across{" "}
            <span className="font-medium text-foreground">
              {bounds.monthsWithActivity.length}
            </span> month{bounds.monthsWithActivity.length === 1 ? "" : "s"}.
            Most recent activity was in <span className="font-medium text-foreground">{label}</span>.
          </p>
          <Link
            href={`/dashboard/trends?from=${fmt(monthStart)}&to=${fmt(monthEnd)}`}
            className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3.5 py-2 text-xs font-medium text-primary-foreground hover:opacity-90 transition-opacity"
          >
            View {label} <ArrowRight className="h-3 w-3" />
          </Link>
        </div>
      </div>
    </div>
  );
}

// pill shown when the page was opened with explicit ?from=&to= params
function CustomRangeBanner({ from, to }: { from: string; to: string }) {
  const fmt = (iso: string) =>
    new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  return (
    <div
      className="flex items-center justify-between rounded-lg border bg-muted/40 px-4 py-2.5 text-xs"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <span className="text-muted-foreground">
        Viewing custom range: <span className="font-medium text-foreground">{fmt(from)} – {fmt(to)}</span>
      </span>
      <Link
        href="/dashboard/trends"
        className="font-medium text-foreground hover:underline"
      >
        Back to default
      </Link>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-lg border border-border bg-card px-6 py-16 text-center">
      <BarChart3 className="mx-auto h-10 w-10 text-muted-foreground/30 mb-3" />
      <p className="text-sm font-medium text-foreground">Nothing to chart yet</p>
      <p className="mt-1 text-xs text-muted-foreground/70 max-w-xs mx-auto">
        Add a transaction, import a CSV, or scan a receipt to see your overview take shape.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Link
          href="/dashboard/transactions"
          className="rounded-md bg-primary px-4 py-2 text-xs font-medium text-white hover:bg-primary/90 transition-colors"
        >
          Add transactions
        </Link>
        <Link
          href="/dashboard/capture/receipt"
          className="rounded-md border border-border px-4 py-2 text-xs font-medium text-foreground/80 hover:bg-muted/50 transition-colors"
        >
          Scan receipt
        </Link>
      </div>
    </div>
  );
}
