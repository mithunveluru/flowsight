"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  ArrowUpRight,
  Plus,
  Receipt,
  TrendingDown,
} from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { analyticsApi } from "@/features/analytics/api";
import { insightsApi } from "@/features/insights/api";
import { leaksApi } from "@/features/leaks/api";
import { transactionApi } from "@/features/transactions/api";
import type { AnalyticsOverview } from "@/features/analytics/types";
import type { InsightsResponse } from "@/features/insights/types";
import type { LeakDetectionResponse } from "@/features/leaks/types";
import type { Transaction } from "@/features/transactions/types";
import { CategoryPill } from "@/components/ui/category-pill";
import { InsightCallout, SignalCard } from "@/components/ui/signals";
import {
  AmbientGlow,
  AnimatedNumber,
  FadeIn,
} from "@/components/motion/primitives";

const formatINRDigits = (v: number) =>
  v.toLocaleString("en-IN", { maximumFractionDigits: 0 });

const formatINR = (v: number) => `₹${formatINRDigits(Number(v))}`;

const isoDate = (d: Date) => d.toISOString().split("T")[0];

function currentMonthRange() {
  const now = new Date();
  return { from: isoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: isoDate(now) };
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

function monthLabel(): string {
  return new Date().toLocaleDateString("en-IN", { month: "long", year: "numeric" });
}

export default function DashboardPage() {
  const { user } = useAuthStore();
  const firstName = user?.fullName?.split(" ")[0] ?? "there";

  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [today, setToday] = useState<AnalyticsOverview | null>(null);
  const [insights, setInsights] = useState<InsightsResponse | null>(null);
  const [leaks, setLeaks] = useState<LeakDetectionResponse | null>(null);
  const [recent, setRecent] = useState<Transaction[] | null>(null);

  useEffect(() => {
    const { from, to } = currentMonthRange();
    const todayIso = isoDate(new Date());

    analyticsApi.getOverview(from, to).then(setOverview).catch(() => setOverview(null));
    analyticsApi.getOverview(todayIso, todayIso).then(setToday).catch(() => {});
    transactionApi.list({ page: 0, size: 5 })
      .then((p) => setRecent(p.content))
      .catch(() => setRecent([]));
    insightsApi.get().then(setInsights).catch(() => {});
    leaksApi.detect().then(setLeaks).catch(() => {});
  }, []);

  const hasData = !!overview && overview.transactionCount > 0;

  return (
    <div className="space-y-10">
      <FadeIn>
        <section className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
              {greeting()}, {firstName}.
            </h1>
            <p className="mt-2 text-base text-muted-foreground">
              Your financial snapshot for {monthLabel()}.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/dashboard/transactions/new"
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90"
            >
              <Plus className="h-3.5 w-3.5" strokeWidth={2} />
              Add transaction
            </Link>
            <Link
              href="/dashboard/receipts/upload"
              className="inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-xs font-medium text-foreground transition-colors hover:bg-muted"
              style={{ borderColor: "hsl(var(--border))" }}
            >
              <Receipt className="h-3.5 w-3.5" strokeWidth={1.75} />
              Scan receipt
            </Link>
          </div>
        </section>
      </FadeIn>

      <FadeIn delay={0.08}>
        <HeroMetric overview={overview} today={today} hasData={hasData} />
      </FadeIn>

      {hasData && (
        <>
          <WorthKnowing insights={insights} leaks={leaks} />
          <RecentActivity transactions={recent} />
        </>
      )}
    </div>
  );
}

function HeroMetric({
  overview,
  today,
  hasData,
}: {
  overview: AnalyticsOverview | null;
  today: AnalyticsOverview | null;
  hasData: boolean;
}) {
  if (!overview) return <div className="skeleton h-44 w-full rounded-xl" />;

  if (!hasData) {
    return (
      <div className="card-refined p-10 text-center">
        <p className="text-headline">No activity recorded yet this month</p>
        <p className="mt-2 text-supporting">
          Add a transaction or scan a receipt, and your snapshot builds itself from there.
        </p>
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/dashboard/transactions/new"
            className="rounded-lg bg-primary px-4 py-2 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90"
          >
            Add transaction
          </Link>
          <Link
            href="/dashboard/transactions"
            className="rounded-lg border px-4 py-2 text-xs font-medium text-foreground transition-colors hover:bg-muted"
            style={{ borderColor: "hsl(var(--border))" }}
          >
            Import a bank statement
          </Link>
        </div>
      </div>
    );
  }

  const net = overview.netCashflow;
  const positive = net >= 0;

  return (
    <div className="card-refined relative overflow-hidden p-6 sm:p-8 lg:p-10">
      <AmbientGlow />

      <div className="relative flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
        <div className="min-w-0">
          <p className="stat-label">Net cashflow</p>
          <p className="mt-3 stat-value text-3xl sm:text-4xl lg:text-5xl">
            {positive ? "" : "−"}
            <AnimatedNumber value={Math.abs(net)} format={(v) => `₹${formatINRDigits(v)}`} />
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-3 sm:gap-x-6">
            <MetricInline label="Spent today" value={today?.totalSpend ?? 0} />
            <div className="hidden h-4 w-px bg-border sm:block" />
            <MetricInline label="Spent" value={overview.totalSpend} />
            <div className="hidden h-4 w-px bg-border sm:block" />
            <MetricInline label="Earned" value={overview.totalIncome} />
            <div className="hidden h-4 w-px bg-border sm:block" />
            <MetricInline label="Transactions" value={overview.transactionCount} isCount />
          </div>
        </div>

        <Link
          href="/dashboard/analytics"
          className="inline-flex shrink-0 items-center gap-1 self-start rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          Full analytics
          <ArrowUpRight className="h-3 w-3" strokeWidth={2} />
        </Link>
      </div>
    </div>
  );
}

function MetricInline({
  label, value, isCount = false,
}: {
  label: string;
  value: number;
  isCount?: boolean;
}) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground/80">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-foreground tabular-nums">
        <AnimatedNumber
          value={value}
          format={(v) => (isCount ? String(v) : `₹${formatINRDigits(v)}`)}
        />
      </p>
    </div>
  );
}

// the single highest-value recommendation plus the biggest drain
function WorthKnowing({
  insights,
  leaks,
}: {
  insights: InsightsResponse | null;
  leaks: LeakDetectionResponse | null;
}) {
  const topRec = insights?.recommendations?.[0] ?? null;
  const topLeak = leaks?.leaks?.[0] ?? null;

  if (!insights && !leaks) {
    return (
      <section>
        <SectionHeader title="Worth knowing" />
        <div className="grid gap-3 lg:grid-cols-2">
          <div className="skeleton h-32 rounded-xl" />
          <div className="skeleton h-32 rounded-xl" />
        </div>
      </section>
    );
  }

  if (!topRec && !topLeak) {
    return (
      <section>
        <SectionHeader title="Worth knowing" />
        <div className="card-refined px-5 py-6">
          <p className="text-sm text-foreground">Nothing needs your attention right now.</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Patterns sharpen with a few more months of activity.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section>
      <SectionHeader
        title="Worth knowing"
        href="/dashboard/insights"
        hrefLabel="All observations"
      />
      <div className="grid gap-3 lg:grid-cols-2">
        {topRec && (
          <SignalCard
            severity={topRec.confidence}
            title={topRec.title}
            metric={
              topRec.potentialMonthlySaving ? (
                <>
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {formatINR(topRec.potentialMonthlySaving)}
                  </p>
                  <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                    per month
                  </p>
                </>
              ) : undefined
            }
          >
            <p className="text-xs leading-relaxed text-muted-foreground">{topRec.description}</p>
            <InsightCallout tone="signal" className="mt-2.5">
              {topRec.suggestedAction}
            </InsightCallout>
          </SignalCard>
        )}

        {topLeak && (
          <SignalCard
            severity={topLeak.severity}
            icon={<TrendingDown className="h-4 w-4" strokeWidth={1.75} />}
            title={topLeak.title}
            metric={
              <>
                <p className="text-sm font-semibold tabular-nums text-foreground">
                  {formatINR(topLeak.monthlyImpact)}
                </p>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                  per month
                </p>
              </>
            }
          >
            <p className="text-xs leading-relaxed text-muted-foreground">{topLeak.description}</p>
            <Link
              href="/dashboard/leaks"
              className="mt-2.5 inline-flex items-center gap-1 text-xs font-medium text-foreground hover:underline"
            >
              See what is recoverable
              <ArrowRight className="h-3 w-3" strokeWidth={2} />
            </Link>
          </SignalCard>
        )}
      </div>
    </section>
  );
}

function RecentActivity({ transactions }: { transactions: Transaction[] | null }) {
  if (!transactions) {
    return (
      <section>
        <SectionHeader title="Recent activity" />
        <div className="skeleton h-48 rounded-xl" />
      </section>
    );
  }

  if (!transactions.length) return null;

  return (
    <section>
      <SectionHeader
        title="Recent activity"
        href="/dashboard/transactions"
        hrefLabel="All transactions"
      />
      <div className="card-refined divide-y divide-border/60 overflow-hidden">
        {transactions.map((tx) => (
          <div key={tx.id} className="flex items-center gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-foreground">
                {tx.merchant ?? tx.description}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {new Date(tx.transactionDate).toLocaleDateString("en-IN", {
                  day: "numeric",
                  month: "short",
                })}
              </p>
            </div>
            <CategoryPill category={tx.category} className="hidden sm:inline-flex" />
            <span
              className={`shrink-0 text-sm font-medium tabular-nums ${
                tx.type === "DEBIT" ? "text-foreground" : "text-positive"
              }`}
            >
              {tx.type === "DEBIT" ? "−" : "+"}
              {formatINR(tx.amount)}
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}

function SectionHeader({
  title,
  href,
  hrefLabel,
}: {
  title: string;
  href?: string;
  hrefLabel?: string;
}) {
  return (
    <div className="section-header items-center">
      <p className="section-title">{title}</p>
      {href && (
        <Link
          href={href}
          className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          {hrefLabel}
          <ArrowUpRight className="h-3 w-3" strokeWidth={2} />
        </Link>
      )}
    </div>
  );
}
