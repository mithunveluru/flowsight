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
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
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

// -------------------------------------------------------------------------
// Category color palette (hex for Recharts)
// -------------------------------------------------------------------------

// Category palette — desaturated, executive-grade.
// Each color was darkened ~10% lightness and pulled 5-15% saturation to
// reduce the "rainbow dashboard" effect while keeping categories distinct.
const CATEGORY_COLORS: Record<string, string> = {
  FOOD_DINING:    "#d97a4f",   // muted coral
  GROCERIES:      "#3f9a6b",   // sage green
  SHOPPING:       "#8e5fbe",   // muted plum
  TRANSPORTATION: "#5577c4",   // dusty blue
  UTILITIES:      "#64748b",   // slate
  ENTERTAINMENT:  "#c45e96",   // muted rose
  HEALTHCARE:     "#c44b4b",   // muted red
  FINANCE:        "#5e6ad2",   // brand indigo
  EDUCATION:      "#b89020",   // muted gold
  TRAVEL:         "#2f9e94",   // teal
  SUBSCRIPTIONS:  "#7a5fc4",   // muted violet
  INCOME:         "#0f9b8e",   // positive teal
  TRANSFER:       "#3e9eb0",   // cyan-slate
  OTHER:          "#94a3b8",   // neutral
  UNCATEGORIZED:  "#cbd5e1",   // faint
};

// Refined chart palette — muted, executive-grade.
// Spend uses the refined brand indigo; income uses a muted teal.
// Neither competes with the per-category palette below.
const SPEND_COLOR  = "#5e6ad2";   // refined indigo (matches --brand)
const INCOME_COLOR = "#0f9b8e";   // muted teal-green (positive accent)

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

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

// -------------------------------------------------------------------------
// Page
// -------------------------------------------------------------------------

export default function AnalyticsPage() {
  const searchParams = useSearchParams();
  // Support deep links like /analytics?from=2026-04-01&to=2026-04-30
  // (used by the CSV import success banner to land users on the right view).
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

  // If the current view is empty but the user has data in other months,
  // surface a one-click jump to the most recent month with activity.
  // This is the *core fix* for the reported issue: users uploading last-month CSVs
  // and not seeing them on the default "this month" dashboard.
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
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">Financial overview</h1>
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

// -------------------------------------------------------------------------
// Date range selector
// -------------------------------------------------------------------------

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

// -------------------------------------------------------------------------
// Summary cards
// -------------------------------------------------------------------------

function SummaryCards({
  overview,
  loading,
}: {
  overview: AnalyticsOverview | null;
  loading: boolean;
}) {
  // Each card carries its raw numeric value for the animated counter.
  // The "Net cashflow" card preserves negative sign via the formatter.
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

// -------------------------------------------------------------------------
// Behavioral alerts
// -------------------------------------------------------------------------

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
            alert.severity === "HIGH"   ? "bg-red-500"
            : alert.severity === "MEDIUM" ? "bg-amber-500"
            : "bg-blue-500";
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

// -------------------------------------------------------------------------
// Monthly trend chart
// -------------------------------------------------------------------------

function SpendTrendChart({
  trend,
  loading,
}: {
  trend: AnalyticsTrend | null;
  loading: boolean;
}) {
  if (loading || !trend) {
    return <ChartSkeleton height={260} />;
  }

  const historical  = trend.points.filter((p) => !p.projected);
  const projected   = trend.points.filter((p) => p.projected);
  // Merge for rendering: last historical + all projected (so lines connect)
  const combined: (MonthlyTrendPoint & { projSpend?: number; projIncome?: number })[] =
    trend.points.map((p) => ({
      ...p,
      projSpend:  p.projected ? p.spend  : undefined,
      projIncome: p.projected ? p.income : undefined,
      spend:      p.projected ? undefined as unknown as number : p.spend,
      income:     p.projected ? undefined as unknown as number : p.income,
    }));

  // Bridge: make the last historical point also appear in the projected series
  const lastHistIdx = trend.points.findIndex((p) => p.projected) - 1;
  if (lastHistIdx >= 0) {
    combined[lastHistIdx].projSpend  = trend.points[lastHistIdx].spend;
    combined[lastHistIdx].projIncome = trend.points[lastHistIdx].income;
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={combined} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="gradSpend" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={SPEND_COLOR}  stopOpacity={0.15} />
            <stop offset="95%" stopColor={SPEND_COLOR}  stopOpacity={0}    />
          </linearGradient>
          <linearGradient id="gradIncome" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={INCOME_COLOR} stopOpacity={0.15} />
            <stop offset="95%" stopColor={INCOME_COLOR} stopOpacity={0}    />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
        <XAxis
          dataKey="label"
          tick={{ fontSize: 11, fill: "#94a3b8" }}
          axisLine={false} tickLine={false}
          interval="preserveStartEnd"
        />
        <YAxis
          tick={{ fontSize: 11, fill: "#94a3b8" }}
          axisLine={false} tickLine={false}
          tickFormatter={(v) => formatINR(v, true)}
          width={52}
        />
        <Tooltip
          formatter={(v: unknown, name: unknown) => {
            const label =
              name === "spend" ? "Spend" :
              name === "income" ? "Income" :
              name === "projSpend" ? "Projected spend" : "Projected income";
            return `${label}: ${formatINR(Number(v))}`;
          }}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #e2e8f0" }}
          labelStyle={{ color: "#475569", fontWeight: 600 }}
        />
        <Legend
          iconType="circle" iconSize={8}
          formatter={(v) =>
            v === "spend" ? "Spend" : v === "income" ? "Income"
            : v === "projSpend" ? "Projected spend" : "Projected income"}
          wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
        />
        <Area type="monotone" dataKey="spend"      stroke={SPEND_COLOR}  fill="url(#gradSpend)"  strokeWidth={2} dot={false} connectNulls />
        <Area type="monotone" dataKey="income"     stroke={INCOME_COLOR} fill="url(#gradIncome)" strokeWidth={2} dot={false} connectNulls />
        <Area type="monotone" dataKey="projSpend"  stroke={SPEND_COLOR}  fill="none" strokeWidth={1.5} strokeDasharray="4 3" dot={false} connectNulls />
        <Area type="monotone" dataKey="projIncome" stroke={INCOME_COLOR} fill="none" strokeWidth={1.5} strokeDasharray="4 3" dot={false} connectNulls />
      </AreaChart>
    </ResponsiveContainer>
  );
}

// -------------------------------------------------------------------------
// Category donut chart
// -------------------------------------------------------------------------

function CategoryDonutChart({
  overview,
  loading,
}: {
  overview: AnalyticsOverview | null;
  loading: boolean;
}) {
  if (loading || !overview) return <ChartSkeleton height={260} />;
  if (overview.categoryBreakdown.length === 0) {
    return <EmptyChart message="No spend data for this period" />;
  }

  const data = overview.categoryBreakdown.slice(0, 8); // cap at 8 slices

  return (
    <ResponsiveContainer width="100%" height={260}>
      <PieChart>
        <Pie
          data={data}
          cx="50%"
          cy="50%"
          innerRadius={60}
          outerRadius={100}
          dataKey="amount"
          nameKey="displayName"
          paddingAngle={2}
        >
          {data.map((entry) => (
            <Cell
              key={entry.category}
              fill={CATEGORY_COLORS[entry.category] ?? "#94a3b8"}
            />
          ))}
        </Pie>
        <Tooltip
          formatter={(v: unknown) => formatINR(Number(v))}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #e2e8f0" }}
        />
        <Legend
          iconType="circle" iconSize={8}
          formatter={(name) => {
            const item = data.find((d) => d.displayName === name);
            return `${name} ${item ? `(${item.percentage.toFixed(0)}%)` : ""}`;
          }}
          wrapperStyle={{ fontSize: 11, paddingTop: 8 }}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}

// -------------------------------------------------------------------------
// Top merchants bar chart
// -------------------------------------------------------------------------

function TopMerchantsChart({
  overview,
  loading,
}: {
  overview: AnalyticsOverview | null;
  loading: boolean;
}) {
  if (loading || !overview) return <ChartSkeleton height={260} />;
  if (overview.topMerchants.length === 0) {
    return <EmptyChart message="No merchant data for this period" />;
  }

  const data = [...overview.topMerchants]
    .reverse() // highest bar at top in horizontal chart
    .map((m) => ({ name: m.merchant, amount: Number(m.totalAmount) }));

  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={data} layout="vertical" margin={{ left: 0, right: 16, top: 4, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
        <XAxis
          type="number"
          tick={{ fontSize: 11, fill: "#94a3b8" }}
          axisLine={false} tickLine={false}
          tickFormatter={(v) => formatINR(v, true)}
        />
        <YAxis
          type="category" dataKey="name"
          tick={{ fontSize: 11, fill: "#64748b" }}
          axisLine={false} tickLine={false}
          width={90}
          tickFormatter={(v: string) => v.length > 12 ? v.slice(0, 12) + "…" : v}
        />
        <Tooltip
          formatter={(v: unknown) => `Spend: ${formatINR(Number(v))}`}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #e2e8f0" }}
          cursor={{ fill: "#f8fafc" }}
        />
        <Bar dataKey="amount" fill={SPEND_COLOR} radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

// -------------------------------------------------------------------------
// Shared layout pieces
// -------------------------------------------------------------------------

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

function ChartSkeleton({ height }: { height: number }) {
  return (
    <div
      className="skeleton w-full rounded-md"
      style={{ height }}
    />
  );
}

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="flex h-52 items-center justify-center text-sm text-slate-400">
      {message}
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-6 py-12 text-center">
      <XCircle className="mx-auto h-10 w-10 text-red-300 mb-3" />
      <p className="text-sm font-medium text-red-900">We could not load your overview</p>
      <p className="mt-1 text-xs text-red-700 max-w-md mx-auto">{message}</p>
      <button
        onClick={onRetry}
        className="mt-5 inline-flex items-center gap-1.5 rounded-md bg-red-600 px-4 py-2 text-xs font-medium text-white hover:bg-red-700 transition-colors"
      >
        <RefreshCw className="h-3 w-3" />
        Retry
      </button>
    </div>
  );
}

/**
 * Shown when the *current* default view is empty but the user has data in other
 * months. This is the core fix for the reported issue: after a CSV import lands
 * in a past month, the user lands on "this month" and sees nothing — we now hand
 * them a one-click jump to where their data actually is.
 */
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
            href={`/dashboard/analytics?from=${fmt(monthStart)}&to=${fmt(monthEnd)}`}
            className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3.5 py-2 text-xs font-medium text-primary-foreground hover:opacity-90 transition-opacity"
          >
            View {label} <ArrowRight className="h-3 w-3" />
          </Link>
        </div>
      </div>
    </div>
  );
}

/**
 * Pill shown at the top when the page was opened with explicit ?from=&to= params.
 * Helps the user understand they're not on the default view.
 */
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
        href="/dashboard/analytics"
        className="font-medium text-foreground hover:underline"
      >
        Back to default
      </Link>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-6 py-16 text-center">
      <BarChart3 className="mx-auto h-10 w-10 text-slate-200 mb-3" />
      <p className="text-sm font-medium text-slate-900">Nothing to chart yet</p>
      <p className="mt-1 text-xs text-slate-400 max-w-xs mx-auto">
        Add a transaction, import a CSV, or scan a receipt to see your overview take shape.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Link
          href="/dashboard/transactions"
          className="rounded-md bg-slate-900 px-4 py-2 text-xs font-medium text-white hover:bg-slate-800 transition-colors"
        >
          Add transactions
        </Link>
        <Link
          href="/dashboard/receipts/upload"
          className="rounded-md border border-slate-200 px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors"
        >
          Scan receipt
        </Link>
      </div>
    </div>
  );
}
