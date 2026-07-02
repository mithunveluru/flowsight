"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  ArrowUpRight,
  BarChart3,
  Brain,
  Flag,
  Receipt,
  Repeat,
  Target,
  TrendingDown,
} from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { analyticsApi } from "@/features/analytics/api";
import type { AnalyticsOverview } from "@/features/analytics/types";
import {
  AmbientGlow,
  AnimatedNumber,
  FadeIn,
  StaggerContainer,
  StaggerItem,
} from "@/components/motion/primitives";

const formatINRDigits = (v: number) =>
  v.toLocaleString("en-IN", { maximumFractionDigits: 0 });

function formatINR(value: number): string {
  return `₹${Number(value).toLocaleString("en-IN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })}`;
}

function currentMonthRange(): { from: string; to: string } {
  const now = new Date();
  const fmt = (d: Date) => d.toISOString().split("T")[0];
  const from = new Date(now.getFullYear(), now.getMonth(), 1);
  return { from: fmt(from), to: fmt(now) };
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

  useEffect(() => {
    const { from, to } = currentMonthRange();
    analyticsApi.getOverview(from, to).then(setOverview).catch(() => {});
  }, []);

  const hasData = overview && overview.transactionCount > 0;

  return (
    <div className="space-y-12">
      <FadeIn>
        <section>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
            {greeting()}, {firstName}.
          </h1>
          <p className="mt-2 text-base text-muted-foreground">
            Your financial snapshot for {monthLabel()}.
          </p>
        </section>
      </FadeIn>

      <FadeIn delay={0.08}>
        <section>
          <HeroMetric overview={overview} hasData={!!hasData} />
        </section>
      </FadeIn>

      <section>
        <FadeIn delay={0.16}>
          <div className="section-header">
            <p className="section-title">Shortcuts</p>
          </div>
        </FadeIn>
        <StaggerContainer className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {quickActions.map((action) => (
            <StaggerItem key={action.label}>
              <QuickAction {...action} />
            </StaggerItem>
          ))}
        </StaggerContainer>
      </section>

      <section>
        <FadeIn delay={0.24}>
          <div className="section-header">
            <div>
              <p className="section-title">Explore</p>
              <p className="section-subtitle">Everything FlowSight can do for you</p>
            </div>
          </div>
        </FadeIn>
        <StaggerContainer className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" stagger={0.04}>
          {modules.map((mod) => (
            <StaggerItem key={mod.title}>
              <ModuleCard {...mod} />
            </StaggerItem>
          ))}
        </StaggerContainer>
      </section>
    </div>
  );
}

function HeroMetric({
  overview,
  hasData,
}: {
  overview: AnalyticsOverview | null;
  hasData: boolean;
}) {
  if (!overview) {
    return <div className="skeleton h-44 w-full rounded-xl" />;
  }

  if (!hasData) {
    return (
      <div className="card-refined p-10 text-center">
        <p className="text-headline">No activity recorded yet this month</p>
        <p className="mt-2 text-supporting">
          Your snapshot will appear here as transactions and receipts are added.
        </p>
        <div className="mt-6 flex items-center justify-center gap-3">
          <Link
            href="/dashboard/transactions/new"
            className="rounded-lg bg-primary px-4 py-2 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90"
          >
            Add transaction
          </Link>
          <Link
            href="/dashboard/receipts/upload"
            className="rounded-lg border px-4 py-2 text-xs font-medium text-foreground transition-colors hover:bg-muted"
            style={{ borderColor: "hsl(var(--border))" }}
          >
            Scan receipt
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
            <AnimatedNumber
              value={Math.abs(net)}
              format={(v) => `₹${formatINRDigits(v)}`}
            />
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-3 sm:gap-x-6">
            <MetricInline label="Spent"        value={overview.totalSpend} />
            <div className="hidden h-4 w-px bg-border sm:block" />
            <MetricInline label="Earned"       value={overview.totalIncome} />
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
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground/80">
        {label}
      </p>
      <p className="mt-0.5 text-sm font-medium text-foreground tabular-nums">
        <AnimatedNumber
          value={value}
          format={(v) => isCount ? String(v) : `₹${formatINRDigits(v)}`}
        />
      </p>
    </div>
  );
}

function QuickAction({
  label,
  description,
  href,
  icon: Icon,
}: {
  label: string;
  description: string;
  href: string;
  icon: React.ElementType;
}) {
  return (
    <Link
      href={href}
      className="card-tactile group flex items-center gap-3 px-4 py-3.5"
    >
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
        <Icon className="h-4 w-4" strokeWidth={1.75} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium text-foreground">{label}</div>
        <div className="text-xs text-muted-foreground">{description}</div>
      </div>
      <ArrowRight
        className="h-3.5 w-3.5 shrink-0 text-muted-foreground/40 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-foreground"
        strokeWidth={1.75}
      />
    </Link>
  );
}

function ModuleCard({
  icon: Icon,
  title,
  description,
  href,
}: {
  icon: React.ElementType;
  title: string;
  description: string;
  href?: string;
}) {
  const content = (
    <>
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted text-foreground/70">
        <Icon className="h-4 w-4" strokeWidth={1.75} />
      </div>
      <h3 className="mt-4 text-sm font-semibold text-foreground">{title}</h3>
      <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{description}</p>
    </>
  );

  if (href) {
    return (
      <Link href={href} className="card-tactile block p-5">
        {content}
      </Link>
    );
  }
  return (
    <div className="card-refined block p-5">
      {content}
    </div>
  );
}

const quickActions = [
  { label: "Transactions",  description: "Review and record",         href: "/dashboard/transactions",    icon: BarChart3 },
  { label: "Scan receipt",  description: "Analyze a new receipt",      href: "/dashboard/receipts/upload", icon: Receipt   },
  { label: "Set a budget",  description: "Define a monthly limit",     href: "/dashboard/budgets",         icon: Target    },
  { label: "Create a goal", description: "Save toward what matters",   href: "/dashboard/goals",           icon: Flag      },
];

const modules = [
  {
    icon: BarChart3, href: "/dashboard/analytics",
    title: "Spending trends",
    description: "Where your money goes each month, by category, merchant, and over time.",
  },
  {
    icon: Repeat, href: "/dashboard/recurring",
    title: "Subscriptions & bills",
    description: "Every recurring charge in one place, with the ones worth cancelling flagged.",
  },
  {
    icon: TrendingDown, href: "/dashboard/leaks",
    title: "Money drains",
    description: "Duplicate subscriptions, creeping prices, small daily habits, and fees worth recovering.",
  },
  {
    icon: Target, href: "/dashboard/budgets",
    title: "Budgets & goals",
    description: "Set monthly limits, save toward what matters, and watch your progress.",
  },
  {
    icon: Receipt, href: "/dashboard/receipts",
    title: "Receipts",
    description: "Snap a receipt, check what we read off it, and save it in a tap.",
  },
  {
    icon: Brain, href: "/dashboard/insights",
    title: "Your money habits",
    description: "The patterns in how you spend, what you could do, and what it adds up to over time.",
  },
];
