"use client";

import { useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { ArrowDownRight, ArrowUpRight, Check, CircleDot, Sparkles, TrendingDown, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CategoryPill } from "@/components/ui/category-pill";
import { CountUp } from "@/components/motion/primitives";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/data-table";
import { ChartFrame, ChartLegend, ChartSkeleton } from "@/components/ui/chart-frame";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { ImpactFigure, SignalCard } from "@/components/ui/signals";
import { useAnnounce } from "@/components/ui/announcer";
import { useActivityBounds, useOverview, useTrend } from "@/features/analytics/hooks";
import { useBudgetSummary } from "@/features/budgets/hooks";
import { useGoals } from "@/features/goals/hooks";
import { useLeaks } from "@/features/leaks/hooks";
import { leakEvidence } from "@/features/insights/evidence";
import type { LeakInsight } from "@/features/leaks/types";
import { useRecentReceipts } from "@/features/receipts/hooks";
import { useRecurring } from "@/features/recurring/hooks";
import { useTransactions, useUncategorizedCount } from "@/features/transactions/hooks";
import { monthToDate } from "@/features/query-core";
import { ListItem, ListMotion } from "@/components/motion/list";
import { TABLE_LOOK } from "@/features/shell-pages/table-look";
import { cn } from "@/lib/utils";
import {
  advanceVisit,
  attentionItems,
  changesSince,
  checklist,
  drainKey,
  formatRupees,
  monthKey,
  takeSnapshot,
  typicalMonthlySpend,
  visibleDrains,
  type Change,
  type VisitState,
} from "./logic";
import { readStored, writeStored } from "./storage";
import type { MonthBar } from "./month-chart";

const MonthChart = dynamic(() => import("./month-chart"), {
  ssr: false,
  loading: () => <ChartSkeleton height={180} />,
});

export const RECENT_PAGE = { page: 0, size: 50 } as const;

const formatDay = (iso: string) => new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

interface QueryLike {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => Promise<unknown>;
}

function combine(queries: QueryLike[]) {
  const failed = queries.filter((q) => q.isError);
  return {
    loading: queries.some((q) => q.isPending && !q.isError),
    failed,
    retry: () => Promise.all(failed.map((q) => q.refetch())),
  };
}

function Section({
  id,
  title,
  action,
  className,
  children,
}: {
  id: string;
  title: string;
  action?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-labelledby={`${id}-title`} className={cn("space-y-sp-wide", className)}>
      <div className="flex min-h-7 items-center justify-between gap-3 border-b border-border pb-sp-sm">
        <h2 id={`${id}-title`} tabIndex={-1} className="text-ty-subhead focus:outline-none">
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function SeeAll({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-sm text-primary transition-colors duration-fast hover:underline coarse:min-h-11"
    >
      {children}
      <ArrowUpRight className="h-3 w-3" strokeWidth={2} aria-hidden="true" />
    </Link>
  );
}

export function WhatChanged({ userId }: { userId: string | undefined }) {
  const transactions = useTransactions(RECENT_PAGE);
  const receipts = useRecentReceipts();
  const recurring = useRecurring();
  const leaks = useLeaks();
  const budgets = useBudgetSummary();
  const state = combine([transactions, receipts, recurring, leaks, budgets]);
  const [visit, setVisit] = useState<VisitState | null>(null);
  const recorded = useRef(false);

  const ready = transactions.data && receipts.data && recurring.data && leaks.data && budgets.data;

  useEffect(() => {
    if (!ready || recorded.current || !userId) return;
    recorded.current = true;
    const current = takeSnapshot({
      now: Date.now(),
      recurring: recurring.data,
      leaks: leaks.data.leaks,
      budgets: budgets.data.budgets,
    });
    const next = advanceVisit(readStored<VisitState>(userId, "today-visit"), current);
    writeStored(userId, "today-visit", next);
    setVisit(next);
  }, [ready, userId, recurring.data, leaks.data, budgets.data]);

  let body: React.ReactNode;
  if (state.failed.length > 0) {
    body = <ErrorState compact title="We couldn't check what changed" error={state.failed[0].error} onRetry={state.retry} />;
  } else if (state.loading || !ready || !visit) {
    body = <LoadingState variant="list" rows={3} label="Checking what changed" />;
  } else if (!visit.baseline) {
    body = (
      <EmptyState
        compact
        as="h3"
        icon={<Sparkles strokeWidth={1.75} />}
        title="This is your first look"
        description="From your next visit, new transactions, confirmed receipts, new subscriptions, price increases and budget alerts show up here."
      />
    );
  } else {
    const changes = changesSince(visit.baseline, {
      transactions: transactions.data.content,
      transactionsCapped: transactions.data.totalElements > transactions.data.content.length,
      receipts: receipts.data.content,
      recurring: recurring.data,
      leaks: leaks.data.leaks,
      budgets: budgets.data.budgets,
    });
    body =
      changes.length === 0 ? (
        <EmptyState
          compact
          as="h3"
          icon={<Check strokeWidth={1.75} />}
          title={`Nothing new since ${formatDay(new Date(visit.baseline.at).toISOString())}`}
          description="Add a receipt or import a statement and the changes will show here."
        />
      ) : (
        <ChangeList changes={changes} />
      );
  }

  return (
    <Section id="changed" title="What changed since your last visit">
      {body}
    </Section>
  );
}

function ChangeList({ changes }: { changes: Change[] }) {
  return (
    <ul className="space-y-sp-xxs">
      {changes.map((c) => (
        <li key={c.id}>
          <Link href={c.href} className="flex items-center gap-3 rounded-md px-sp-xl py-sp-xl transition-colors duration-fast hover:bg-chrome">
            <CircleDot className="h-4 w-4 shrink-0 text-primary" strokeWidth={1.75} aria-hidden="true" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium text-ink">{c.label}</span>
              {c.detail && <span className="block truncate text-caption">{c.detail}</span>}
            </span>
            <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" strokeWidth={2} aria-hidden="true" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

const SOURCE_NAMES = ["receipts", "uncategorized transactions", "budgets", "subscriptions", "goals"];

export function NeedsAttention() {
  const receipts = useRecentReceipts();
  const uncategorized = useUncategorizedCount();
  const budgets = useBudgetSummary();
  const recurring = useRecurring();
  const goals = useGoals();
  const sources = [receipts, uncategorized, budgets, recurring, goals];
  const state = combine(sources);

  let body: React.ReactNode;
  if (state.loading) {
    body = <LoadingState variant="list" rows={3} label="Checking what needs attention" />;
  } else if (state.failed.length === sources.length) {
    body = <ErrorState compact title="We couldn't check what needs attention" error={state.failed[0].error} onRetry={state.retry} />;
  } else {
    const items = attentionItems({
      receipts: receipts.data?.content,
      uncategorized: uncategorized.data,
      budgets: budgets.data?.budgets,
      recurring: recurring.data,
      goals: goals.data,
    });
    const missing = sources.flatMap((q, i) => (q.isError ? [SOURCE_NAMES[i]] : []));
    body = (
      <div className="space-y-3">
        {items.length === 0 ? (
          <EmptyState
            as="h3"
            icon={<Check strokeWidth={1.75} />}
            title="You're all caught up"
            description="No receipts waiting, no budgets near their limit, no missed payments and no goals behind."
          />
        ) : (
          <ul className="relative space-y-3">
            <ListMotion>
            {items.map((item) => (
              <ListItem key={item.id}>
                <SignalCard
                  severity={item.severity}
                  title={item.title}
                  metric={
                    <Button variant="outline" size="sm" asChild>
                      <Link href={item.action.href}>{item.action.label}</Link>
                    </Button>
                  }
                >
                  <p className="text-xs leading-relaxed text-muted-foreground">{item.description}</p>
                </SignalCard>
              </ListItem>
            ))}
            </ListMotion>
          </ul>
        )}
        {missing.length > 0 && (
          <p role="status" className="flex flex-wrap items-center gap-2 text-caption">
            We couldn&apos;t check {missing.join(", ")}.
            <Button variant="link" size="sm" className="h-auto p-0 text-xs" onClick={() => void state.retry()}>
              Try again
            </Button>
          </p>
        )}
      </div>
    );
  }

  return (
    <Section id="attention" title="Needs attention">
      {body}
    </Section>
  );
}

export function YouCouldRecover({ userId }: { userId: string | undefined }) {
  const leaks = useLeaks();
  const announce = useAnnounce();
  const [hidden, setHidden] = useState<string[]>([]);

  useEffect(() => {
    setHidden(readStored<string[]>(userId, "hidden-drains") ?? []);
  }, [userId]);

  const hide = (leak: LeakInsight) => {
    const next = [...hidden, drainKey(leak)];
    setHidden(next);
    writeStored(userId, "hidden-drains", next);
    announce(`${leak.title} hidden from Today on this device.`);
    document.getElementById("recover-title")?.focus();
  };

  let body: React.ReactNode;
  if (leaks.isPending) {
    body = <LoadingState variant="list" rows={3} label="Looking for money you could recover" />;
  } else if (leaks.isError) {
    body = <ErrorState compact title="We couldn't check for money drains" error={leaks.error} onRetry={leaks.refetch} />;
  } else {
    const drains = visibleDrains(leaks.data.leaks, hidden);
    body =
      drains.length === 0 ? (
        <EmptyState
          as="h3"
          icon={<TrendingDown strokeWidth={1.75} />}
          title="No money drains found"
          description="We look at the last three months for overlapping subscriptions, price increases, frequent small spends and bank fees. Nothing stood out."
        />
      ) : (
        <ul className="relative space-y-3">
          <ListMotion>
          {drains.map((leak) => (
            <ListItem key={drainKey(leak)}>
              <div className="flex flex-col gap-3 rounded-md bg-chrome p-5 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <h3 className="text-title">{leak.title}</h3>
                  <p className="mt-1 text-supporting">{leak.description}</p>
                </div>
                <div className="flex shrink-0 items-start gap-1 sm:flex-col sm:items-end">
                  <ImpactFigure monthly={leak.monthlyImpact} annual={leak.annualImpact} className="text-left sm:text-right" />
                  <EvidenceDrawer
                    title={leak.title}
                    value={`${formatRupees(leak.monthlyImpact)} a month`}
                    evidence={leakEvidence(leak, new Date(leaks.dataUpdatedAt))}
                    trigger={<EvidenceTrigger label={`${leak.title} could save ${formatRupees(leak.monthlyImpact)} a month`} className="ml-auto sm:ml-0" />}
                    footer={
                      <Button variant="ghost" size="sm" onClick={() => hide(leak)}>
                        <X aria-hidden="true" />
                        Not a drain
                      </Button>
                    }
                  >
                    <p className="px-6 pb-5 text-caption">
                      &ldquo;Not a drain&rdquo; hides this from Today on this device. It stays on the Money drains page.
                    </p>
                  </EvidenceDrawer>
                </div>
              </div>
            </ListItem>
          ))}
          </ListMotion>
        </ul>
      );
  }

  return (
    <Section id="recover" title="You could recover" action={<SeeAll href="/dashboard/drains">See all money drains</SeeAll>}>
      {body}
    </Section>
  );
}

export function ThisMonth({ earliest }: { earliest: string | null | undefined }) {
  const now = new Date();
  const range = monthToDate(now);
  const overview = useOverview(range.from, range.to);
  const trend = useTrend(4);

  let body: React.ReactNode;
  if (overview.isPending) {
    body = <LoadingState variant="card" rows={3} label="Loading this month" />;
  } else if (overview.isError) {
    body = <ErrorState compact title="This month didn't load" error={overview.error} onRetry={overview.refetch} />;
  } else if (overview.data.transactionCount === 0) {
    body = (
      <EmptyState
        compact
        as="h3"
        title="No transactions this month yet"
        description="Scan a receipt or import a statement that covers this month."
      />
    );
  } else {
    const o = overview.data;
    const typical = trend.data ? typicalMonthlySpend(trend.data.points, monthKey(now), earliest) : null;
    const monthLabel = now.toLocaleDateString("en-IN", { month: "short" });
    const bars: MonthBar[] = [
      ...(typical?.months ?? []).map((m) => ({ label: m.label.split(" ")[0], spend: m.spend })),
      { label: `${monthLabel} so far`, spend: o.totalSpend, partial: true },
    ];
    const net = o.netCashflow;
    body = (
      <div className="space-y-5">
        <div className="rounded-md bg-band-blue p-5">
          <dl className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <dt className="stat-label flex items-center gap-1">
                Net cashflow
                <EvidenceDrawer
                  title="Net cashflow this month"
                  value={`${net < 0 ? "−" : ""}${formatRupees(Math.abs(net))}`}
                  evidence={{
                    method: "Money in minus money out for every transaction dated this month.",
                    inputs: [
                      { label: "Money in", value: formatRupees(o.totalIncome) },
                      { label: "Money out", value: formatRupees(o.totalSpend) },
                      { label: "Transactions", value: o.transactionCount.toLocaleString("en-IN"), href: "/dashboard/transactions" },
                    ],
                    window: { from: o.from, to: o.to },
                  }}
                  trigger={<EvidenceTrigger label="net cashflow this month" />}
                />
              </dt>
              <dd className={cn("stat-value mt-1 flex items-center gap-1.5", net < 0 && "text-warning")}>
                {net < 0 && <ArrowDownRight className="h-6 w-6 shrink-0" strokeWidth={2} aria-hidden="true" />}
                <span>
                  {net < 0 ? "−" : ""}
                  <CountUp value={Math.abs(net)} format={formatRupees} />
                </span>
              </dd>
            </div>
            <div>
              <dt className="stat-label">Spent so far</dt>
              <dd className="stat-value-sm mt-1"><CountUp value={o.totalSpend} format={formatRupees} /></dd>
            </div>
            <div>
              <dt className="stat-label flex items-center gap-1">
                Typical month
                {typical && (
                  <EvidenceDrawer
                    title="Typical monthly spend"
                    value={formatRupees(typical.average)}
                    evidence={{
                      method: `Average of what you spent in the last ${typical.months.length === 1 ? "complete month" : `${typical.months.length} complete months`}. A month you only have part of is left out.`,
                      inputs: typical.months.map((m) => ({ label: m.label, value: formatRupees(m.spend) })),
                      source: "rules",
                    }}
                    trigger={<EvidenceTrigger label="typical monthly spend" />}
                  />
                )}
              </dt>
              {typical ? (
                <dd className="stat-value-sm mt-1"><CountUp value={typical.average} format={formatRupees} /></dd>
              ) : (
                <dd className="mt-1.5 text-caption">Needs a full month of history</dd>
              )}
            </div>
          </dl>
        </div>
        <ChartFrame
          title="Spending by month"
          description={typical ? "Dashed line: typical month" : undefined}
          state={trend.isPending ? "loading" : trend.isError ? "error" : "ready"}
          error={trend.error}
          onRetry={trend.refetch}
          height={180}
          summary={`Spending by month: ${bars.map((b) => `${b.label} ${formatRupees(b.spend)}`).join(", ")}.`}
          legend={
            typical ? (
              <ChartLegend
                items={[
                  { label: "Spent", color: "hsl(var(--chart-spend))" },
                  { label: "Typical month", color: "hsl(var(--chart-axis))", variant: "dashed" },
                ]}
              />
            ) : undefined
          }
          as="h3"
        >
          <MonthChart data={bars} typical={typical?.average} />
        </ChartFrame>
      </div>
    );
  }

  return (
    <Section id="month" title="This month" action={<SeeAll href="/dashboard/trends">Trends</SeeAll>}>
      {body}
    </Section>
  );
}

export function RecentActivity() {
  const transactions = useTransactions(RECENT_PAGE);

  let body: React.ReactNode;
  if (transactions.isPending) {
    body = <LoadingState variant="list" rows={5} label="Loading recent transactions" />;
  } else if (transactions.isError) {
    body = (
      <ErrorState compact title="Recent transactions didn't load" error={transactions.error} onRetry={transactions.refetch} />
    );
  } else if (transactions.data.content.length === 0) {
    body = <EmptyState compact as="h3" title="No transactions yet" description="Anything you capture shows up here first." />;
  } else {
    body = (
      <Table className={TABLE_LOOK}>
        <caption className="sr-only">The five most recent transactions</caption>
        <TableHeader>
          <TableRow>
            <TableHead className="px-sp-xl">Merchant</TableHead>
            <TableHead className="hidden px-sp-xl sm:table-cell">Category</TableHead>
            <TableHead align="right" className="px-sp-xl">Amount</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {transactions.data.content.slice(0, 5).map((tx) => (
            <TableRow key={tx.id}>
              <TableCell className="px-sp-xl">
                <p className="truncate text-sm font-medium text-ink">{tx.merchant ?? tx.description}</p>
                <p className="mt-0.5 text-xs text-ink-meta">{formatDay(tx.transactionDate)}</p>
              </TableCell>
              <TableCell className="hidden px-sp-xl sm:table-cell">
                <CategoryPill category={tx.category} />
              </TableCell>
              <TableCell align="right" className={cn("whitespace-nowrap px-sp-xl font-medium", tx.type === "DEBIT" ? "text-ink" : "text-positive")}>
                <span className="sr-only">{tx.type === "DEBIT" ? "Spent " : "Received "}</span>
                <span aria-hidden="true">{tx.type === "DEBIT" ? "−" : "+"}</span>
                {formatRupees(Number(tx.amount))}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }

  return (
    <Section id="recent" title="Recent activity" action={<SeeAll href="/dashboard/transactions">All transactions</SeeAll>}>
      {body}
    </Section>
  );
}

export function SetupChecklist({ userId }: { userId: string | undefined }) {
  const bounds = useActivityBounds();
  const uncategorized = useUncategorizedCount();
  const budgets = useBudgetSummary();
  const goals = useGoals();
  const [dismissed, setDismissed] = useState(true);

  useEffect(() => {
    setDismissed(readStored<boolean>(userId, "checklist-dismissed") === true);
  }, [userId]);

  if (dismissed || !bounds.data || uncategorized.data == null || !budgets.data || !goals.data) return null;

  const items = checklist({
    transactionCount: bounds.data.totalTransactionCount,
    uncategorized: uncategorized.data,
    budgetCount: budgets.data.budgetCount,
    goalCount: goals.data.length,
  });
  const done = items.filter((i) => i.done).length;
  if (done === items.length) return null;

  const dismiss = () => {
    writeStored(userId, "checklist-dismissed", true);
    setDismissed(true);
  };

  return (
    <Section
      id="setup"
      title="Get set up"
      action={
        <Button variant="ghost" size="icon-sm" onClick={dismiss} aria-label="Dismiss the setup checklist">
          <X aria-hidden="true" />
        </Button>
      }
    >
      <div className="rounded-md bg-chrome p-5">
        <p className="text-caption">
          {done} of {items.length} done
        </p>
        <ol className="mt-3 space-y-1">
          {items.map((item) => (
            <li key={item.id}>
              <Link
                href={item.href}
                className="flex items-center gap-2.5 rounded-md px-1.5 py-1.5 text-sm transition-colors duration-fast hover:bg-band coarse:min-h-11"
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    "flex h-5 w-5 shrink-0 items-center justify-center rounded-sm border",
                    item.done ? "border-positive bg-positive text-primary-foreground" : "border-input"
                  )}
                >
                  {item.done && <Check className="h-3 w-3" strokeWidth={3} />}
                </span>
                <span className={cn("flex-1", item.done ? "text-muted-foreground line-through" : "text-foreground")}>
                  {item.label}
                </span>
                <span className="sr-only">{item.done ? "(done)" : "(to do)"}</span>
              </Link>
            </li>
          ))}
        </ol>
      </div>
    </Section>
  );
}
