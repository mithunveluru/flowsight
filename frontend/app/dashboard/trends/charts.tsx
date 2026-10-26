"use client";

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
import { ChartSkeleton } from "@/components/ui/chart-frame";
import type {
  AnalyticsOverview,
  AnalyticsTrend,
  MonthlyTrendPoint,
} from "@/features/analytics/types";

// category palette from CSS chart tokens
const CATEGORY_COLORS: Record<string, string> = {
  FOOD_DINING:    "hsl(var(--chart-1))",   // soft coral
  GROCERIES:      "hsl(var(--chart-2))",   // sage mint
  SHOPPING:       "hsl(var(--chart-3))",   // soft plum
  TRANSPORTATION: "hsl(var(--chart-4))",   // dusty blue
  UTILITIES:      "hsl(var(--chart-5))",   // slate
  ENTERTAINMENT:  "hsl(var(--chart-6))",   // muted rose
  HEALTHCARE:     "hsl(var(--chart-7))",   // soft red
  FINANCE:        "hsl(var(--chart-8))",   // lavender (brand family)
  EDUCATION:      "hsl(var(--chart-9))",   // warm sand
  TRAVEL:         "hsl(var(--chart-10))",  // pastel teal
  SUBSCRIPTIONS:  "hsl(var(--chart-11))",  // lilac
  INCOME:         "hsl(var(--chart-12))",  // mint
  TRANSFER:       "hsl(var(--chart-13))",  // sky cyan
  OTHER:          "hsl(var(--chart-14))",  // neutral
  UNCATEGORIZED:  "hsl(var(--chart-15))",  // faint
};

// time-series line colors from CSS tokens (theme-adaptive)
const SPEND_COLOR  = "hsl(var(--chart-spend))";   // soft lavender (brand family)
const INCOME_COLOR = "hsl(var(--chart-income))";  // pastel teal
const GRID_COLOR   = "hsl(var(--chart-grid))";
const AXIS_COLOR   = "hsl(var(--chart-axis))";

function formatINR(value: number, compact = false): string {
  if (compact) {
    if (value >= 100_000) return `₹${(value / 100_000).toFixed(1)}L`;
    if (value >= 1_000)   return `₹${(value / 1_000).toFixed(0)}K`;
    return `₹${value.toFixed(0)}`;
  }
  return `₹${Number(value).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="flex h-52 items-center justify-center text-sm text-muted-foreground/70">
      {message}
    </div>
  );
}

export function SpendTrendChart({
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
        <CartesianGrid strokeDasharray="3 3" stroke={GRID_COLOR} vertical={false} />
        <XAxis
          dataKey="label"
          tick={{ fontSize: 11, fill: AXIS_COLOR }}
          axisLine={false} tickLine={false}
          interval="preserveStartEnd"
        />
        <YAxis
          tick={{ fontSize: 11, fill: AXIS_COLOR }}
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
          contentStyle={{ fontSize: 12, borderRadius: 8, border: `1px solid ${GRID_COLOR}` }}
          labelStyle={{ color: "hsl(var(--foreground))", fontWeight: 600 }}
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

export function CategoryDonutChart({
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
              fill={CATEGORY_COLORS[entry.category] ?? "hsl(var(--chart-14))"}
            />
          ))}
        </Pie>
        <Tooltip
          formatter={(v: unknown) => formatINR(Number(v))}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: `1px solid ${GRID_COLOR}` }}
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

export function TopMerchantsChart({
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
        <CartesianGrid strokeDasharray="3 3" stroke={GRID_COLOR} horizontal={false} />
        <XAxis
          type="number"
          tick={{ fontSize: 11, fill: AXIS_COLOR }}
          axisLine={false} tickLine={false}
          tickFormatter={(v) => formatINR(v, true)}
        />
        <YAxis
          type="category" dataKey="name"
          tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }}
          axisLine={false} tickLine={false}
          width={90}
          tickFormatter={(v: string) => v.length > 12 ? v.slice(0, 12) + "…" : v}
        />
        <Tooltip
          formatter={(v: unknown) => `Spend: ${formatINR(Number(v))}`}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: `1px solid ${GRID_COLOR}` }}
          cursor={{ fill: "hsl(var(--muted) / 0.5)" }}
        />
        <Bar dataKey="amount" fill={SPEND_COLOR} radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
