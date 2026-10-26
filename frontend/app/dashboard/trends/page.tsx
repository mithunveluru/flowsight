"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { AlertTriangle, ArrowRight, BarChart3, Info } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { forecastEvidence } from "@/features/insights/evidence";
import { EmptyState, ErrorState } from "@/components/ui/states";
import { TabPanel, Tabs } from "@/components/ui/tabs";
import { analyticsApi } from "@/features/analytics/api";
import type {
  ActivityBounds,
  AnalyticsOverview,
  AnalyticsTrend,
  DateRangePreset,
} from "@/features/analytics/types";
import { CountUp, Reveal } from "@/components/motion/primitives";
import { ContentBand, PageTitle, SectionHead, Stat, Surface } from "@/components/insight/page-kit";
import { CategoryDonutChart, SpendTrendChart, TopMerchantsChart } from "./charts";
import { ChartSkeleton } from "@/components/ui/chart-frame";

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

  const body = error ? (
    <ErrorState title="We could not load your overview" description={error} onRetry={() => load(preset)} />
  ) : dataElsewhere ? (
    <DataElsewhereHint bounds={bounds!} />
  ) : isEmpty ? (
    <EmptyState
      icon={<BarChart3 />}
      title="Nothing to chart yet"
      description="Add a transaction, import a CSV, or scan a receipt to see your overview take shape."
      action={
        <Button asChild>
          <Link href="/dashboard/transactions">Add transactions</Link>
        </Button>
      }
      secondaryAction={
        <Button variant="outline" asChild>
          <Link href="/dashboard/capture/receipt">Scan receipt</Link>
        </Button>
      }
    />
  ) : (
    <div className="space-y-sp-hero">
      <SummaryCards overview={overview} loading={loading} />

      {overview && overview.alerts.length > 0 && (
        <Reveal>
          <AlertsPanel alerts={overview.alerts} />
        </Reveal>
      )}

      <Reveal>
        <section>
          <SectionHead title="Monthly cashflow" lead="Spend against income, over time" />
          <SpendTrendChart trend={trend} loading={loading} />
          {trend && <ForecastNote trend={trend} />}
        </section>
      </Reveal>

      <ContentBand tone="blue">
        <div className="grid gap-sp-hero lg:grid-cols-2">
          <section>
            <SectionHead title="Where your money went" lead={preset === "1M" ? "This month" : "Selected period"} />
            <CategoryDonutChart overview={overview} loading={loading} />
          </section>
          <section>
            <SectionHead title="Where you spent most" lead="By total outflow" />
            <TopMerchantsChart overview={overview} loading={loading} />
          </section>
        </div>
      </ContentBand>
    </div>
  );

  return (
    <div className="space-y-sp-max">
      <PageTitle
        title="Trends"
        lead="How your money moves, where it goes, and the patterns behind it."
      />

      <Tabs items={PRESETS} value={preset} onValueChange={setPreset} label="Date range" idPrefix="range" />

      {hasCustomRange && customFrom && customTo && <CustomRangeBanner from={customFrom} to={customTo} />}

      {PRESETS.map((p) => (
        <TabPanel key={p.value} idPrefix="range" value={p.value} active={p.value === preset} className="focus:outline-none">
          {body}
        </TabPanel>
      ))}
    </div>
  );
}

const PRESETS: { value: DateRangePreset; label: string }[] = [
  { value: "1M",  label: "This month" },
  { value: "3M",  label: "3 months"   },
  { value: "6M",  label: "6 months"   },
  { value: "12M", label: "12 months"  },
];

function SummaryCards({
  overview,
  loading,
}: {
  overview: AnalyticsOverview | null;
  loading: boolean;
}) {
  if (loading || !overview) {
    return (
      <Surface className="grid gap-sp-giant sm:grid-cols-2 lg:grid-cols-4" aria-busy="true">
        {[0, 1, 2, 3].map((i) => (
          <div key={i}>
            <div className="skeleton h-4 w-20" />
            <div className="skeleton mt-sp-sm h-8 w-28" />
          </div>
        ))}
      </Surface>
    );
  }
  const cards = [
    { label: "Total spend",  value: overview.totalSpend,       format: (v: number) => `₹${v.toLocaleString("en-IN")}` },
    { label: "Total income", value: overview.totalIncome,      format: (v: number) => `₹${v.toLocaleString("en-IN")}` },
    { label: "Net cashflow", value: overview.netCashflow,      format: (v: number) => `${v < 0 ? "−" : ""}₹${Math.abs(v).toLocaleString("en-IN")}` },
    { label: "Transactions", value: overview.transactionCount, format: (v: number) => String(v) },
  ];
  return (
    <Surface className="grid gap-sp-giant sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <Stat key={card.label} label={card.label}>
          <CountUp value={card.value} format={card.format} />
        </Stat>
      ))}
    </Surface>
  );
}

function AlertsPanel({ alerts }: { alerts: AnalyticsOverview["alerts"] }) {
  return (
    <section>
      <SectionHead title="Worth noticing" />
      <ul className="divide-y divide-border">
        {alerts.map((alert, i) => (
          <li key={i} className="flex items-start gap-sp-xl py-sp-xl">
            {alert.severity === "LOW" ? (
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
            ) : (
              <AlertTriangle
                className={alert.severity === "HIGH" ? "mt-0.5 h-4 w-4 shrink-0 text-warning" : "mt-0.5 h-4 w-4 shrink-0 text-caution"}
                aria-hidden="true"
              />
            )}
            <div className="min-w-0">
              <p className="text-sm text-ink">
                <span className="font-medium">{alert.categoryDisplayName}:</span> {alert.message}
              </p>
              <p className="mt-sp-xs text-xs text-ink-meta">
                {formatINR(alert.currentAmount)} this period · avg {formatINR(alert.averageAmount)}/mo
              </p>
            </div>
          </li>
        ))}
      </ul>
    </section>
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
    <div className="mt-sp-giant space-y-sp-xs text-sm text-ink-meta">
      <p>Dashed lines are projections for {next.label}, with the likely 80% range shaded.</p>
      {rows.map((r) => (
        <p key={r.key} className="flex flex-wrap items-center gap-x-sp-xs">
          <span>
            {r.label}: <span className="font-medium tabular-nums text-ink">{formatINR(r.point)}</span> (
            {formatINR(r.r.lower80)} to {formatINR(r.r.upper80)})
          </span>
          <EvidenceDrawer
            title={`${r.label} projection for ${next.label}`}
            value={formatINR(r.point)}
            evidence={forecastEvidence(r.ev, f.asOf)}
            trigger={<EvidenceTrigger label={`the ${r.label.toLowerCase()} projection`} className="coarse:h-11 coarse:min-w-11" />}
          />
        </p>
      ))}
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
    <Surface tone="blue" className="p-sp-max">
      <h2 className="text-ty-subhead text-ink">Your data is here, just outside the current range</h2>
      <p className="mt-sp-sm max-w-prose text-sm text-ink-body">
        You have <span className="font-medium tabular-nums text-ink">{bounds.totalTransactionCount}</span> transaction
        {bounds.totalTransactionCount === 1 ? "" : "s"} across{" "}
        <span className="font-medium text-ink">{bounds.monthsWithActivity.length}</span> month
        {bounds.monthsWithActivity.length === 1 ? "" : "s"}. Most recent activity was in{" "}
        <span className="font-medium text-ink">{label}</span>.
      </p>
      <Button asChild className="mt-sp-giant">
        <Link href={`/dashboard/trends?from=${fmt(monthStart)}&to=${fmt(monthEnd)}`}>
          View {label} <ArrowRight aria-hidden="true" />
        </Link>
      </Button>
    </Surface>
  );
}

// shown when the page was opened with explicit ?from=&to= params
function CustomRangeBanner({ from, to }: { from: string; to: string }) {
  const fmt = (iso: string) =>
    new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  return (
    <div className="flex flex-wrap items-center justify-between gap-sp-xl rounded-md bg-chrome px-sp-wide py-sp-xl text-sm">
      <span className="text-ink-meta">
        Viewing custom range: <span className="font-medium text-ink">{fmt(from)} to {fmt(to)}</span>
      </span>
      <Button variant="link" size="sm" asChild>
        <Link href="/dashboard/trends">Back to default</Link>
      </Button>
    </div>
  );
}
