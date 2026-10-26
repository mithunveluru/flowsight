"use client";

import { useMemo } from "react";
import { ChartLegend, ChartSkeleton } from "@/components/ui/chart-frame";
import { BarList } from "@/components/insight/bar-list";
import { DonutChart } from "@/components/insight/donut-chart";
import { LineChart, type LineSeries } from "@/components/insight/line-chart";
import type { AnalyticsOverview, AnalyticsTrend } from "@/features/analytics/types";
import { rupees, rupeesCompact } from "@/features/insights/format";

const SPEND = "hsl(var(--chart-spend))";
const INCOME = "hsl(var(--chart-income))";
const INK = "hsl(var(--chart-axis))";

export function SpendTrendChart({ trend, loading }: { trend: AnalyticsTrend | null; loading: boolean }) {
  const view = useMemo(() => {
    if (!trend) return null;
    const pts = trend.points;
    const firstProjected = pts.findIndex((p) => p.projected);
    const band = (pick: "spend" | "income", edge: "lower80" | "upper80") =>
      pts.map((p, i) =>
        p.projected && p.range
          ? p.range[pick][edge]
          : firstProjected > 0 && i === firstProjected - 1
            ? p[pick]
            : null
      );
    const hasRange = firstProjected >= 0 && pts.some((p) => p.range);
    const series: LineSeries[] = [
      {
        key: "spend",
        label: "Spend",
        color: SPEND,
        marker: "circle",
        values: pts.map((p) => p.spend),
        projectedFrom: firstProjected >= 0 ? firstProjected : undefined,
        lower: hasRange ? band("spend", "lower80") : undefined,
        upper: hasRange ? band("spend", "upper80") : undefined,
      },
      {
        key: "income",
        label: "Income",
        color: INCOME,
        marker: "square",
        values: pts.map((p) => p.income),
        projectedFrom: firstProjected >= 0 ? firstProjected : undefined,
        lower: hasRange ? band("income", "lower80") : undefined,
        upper: hasRange ? band("income", "upper80") : undefined,
      },
    ];
    return { categories: pts.map((p) => p.label), series, hasProjection: firstProjected >= 0 };
  }, [trend]);

  if (loading || !view) return <ChartSkeleton height={280} />;

  return (
    <div>
      <LineChart
        categories={view.categories}
        series={view.series}
        height={280}
        format={rupees}
        formatAxis={rupeesCompact}
        ariaLabel="Monthly spend and income"
      />
      <ChartLegend
        className="mt-sp-wide justify-start"
        items={[
          { label: "Spend", color: SPEND, marker: "circle" },
          { label: "Income", color: INCOME, marker: "square" },
          ...(view.hasProjection ? [{ label: "Projected", color: INK, variant: "dashed" as const }] : []),
        ]}
      />
    </div>
  );
}

export function CategoryDonutChart({ overview, loading }: { overview: AnalyticsOverview | null; loading: boolean }) {
  if (loading || !overview) return <ChartSkeleton height={260} />;
  if (overview.categoryBreakdown.length === 0) {
    return <p className="flex h-52 items-center justify-center text-sm text-ink-meta">No spend data for this period</p>;
  }
  const items = overview.categoryBreakdown.slice(0, 8).map((c, i) => ({
    key: c.category,
    label: c.displayName,
    value: c.amount,
    color: `hsl(var(--chart-${i + 1}))`,
  }));
  return <DonutChart items={items} format={rupees} centerLabel="Total spend" />;
}

export function TopMerchantsChart({ overview, loading }: { overview: AnalyticsOverview | null; loading: boolean }) {
  if (loading || !overview) return <ChartSkeleton height={260} />;
  if (overview.topMerchants.length === 0) {
    return <p className="flex h-52 items-center justify-center text-sm text-ink-meta">No merchant data for this period</p>;
  }
  return (
    <BarList
      format={rupees}
      items={overview.topMerchants.map((m) => ({
        key: m.merchant,
        label: m.merchant,
        value: Number(m.totalAmount),
        detail: `${m.transactionCount} transaction${m.transactionCount === 1 ? "" : "s"}`,
      }))}
    />
  );
}

