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
import { cn } from "@/lib/utils";

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
      {/* Hero — calm, generous space, ambient breathing */}
      <FadeIn>
        <section>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">
            {greeting()}, {firstName}.
          </h1>
          <p className="mt-2 text-base text-muted-foreground">
            Your financial snapshot for {monthLabel()}.
          </p>
        </section>
      </FadeIn>

      {/* Primary metric — animates from 0 to the real number */}
      <FadeIn delay={0.08}>
        <section>
          <HeroMetric overview={overview} hasData={!!hasData} />
        </section>
      </FadeIn>

      {/* Quick actions — staggered entrance */}
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

      {/* Modules — staggered entrance */}
      <section>
        <FadeIn delay={0.24}>
          <div className="section-header">
            <div>
              <p className="section-title">Explore</p>
              <p className="section-subtitle">Tools available across the platform</p>
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

// -------------------------------------------------------------------------
// Hero metric
// -------------------------------------------------------------------------

function HeroMetric({
  overview,
  hasData,
}: {
  overview: AnalyticsOverview | null;
  hasData: boolean;
}) {
  if (!overview) {
    return <div className="h-44 w-full animate-pulse rounded-xl bg-muted" />;
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
    <div className="card-refined relative overflow-hidden p-8 lg:p-10">
      {/* Ambient breathing glow — very subtle, restrained */}
      <AmbientGlow />

      <div className="relative flex items-start justify-between gap-6">
        <div>
          <p className="stat-label">Net cashflow</p>
          <p className="mt-3 stat-value text-4xl lg:text-5xl">
            {positive ? "" : "−"}
            <AnimatedNumber
              value={Math.abs(net)}
              format={(v) => `₹${formatINRDigits(v)}`}
            />
          </p>
          <div className="mt-4 flex items-center gap-6">
            <MetricInline label="Spent"        value={overview.totalSpend} />
            <div className="h-4 w-px bg-border" />
            <MetricInline label="Earned"       value={overview.totalIncome} />
            <div className="h-4 w-px bg-border" />
            <MetricInline label="Transactions" value={overview.transactionCount} isCount />
          </div>
        </div>

        <Link
          href="/dashboard/analytics"
          className="inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
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

// -------------------------------------------------------------------------
// Quick action tile
// -------------------------------------------------------------------------

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
      className={cn(
        "group flex items-center gap-3 rounded-xl border bg-card px-4 py-3.5 transition-colors",
        "hover:bg-muted/40"
      )}
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
        <Icon className="h-4 w-4" strokeWidth={1.75} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium text-foreground">{label}</div>
        <div className="text-xs text-muted-foreground">{description}</div>
      </div>
      <ArrowRight
        className="h-3.5 w-3.5 shrink-0 text-muted-foreground/40 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-muted-foreground"
        strokeWidth={1.75}
      />
    </Link>
  );
}

// -------------------------------------------------------------------------
// Module card
// -------------------------------------------------------------------------

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
  const className = cn(
    "block rounded-xl border bg-card p-5 transition-colors",
    href ? "hover:bg-muted/40 cursor-pointer" : ""
  );
  const style = { borderColor: "hsl(var(--border))" };

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
      <Link href={href} className={className} style={style}>
        {content}
      </Link>
    );
  }
  return (
    <div className={className} style={style}>
      {content}
    </div>
  );
}

// -------------------------------------------------------------------------
// Static config
// -------------------------------------------------------------------------

const quickActions = [
  { label: "Transactions",  description: "Review and record",         href: "/dashboard/transactions",    icon: BarChart3 },
  { label: "Scan receipt",  description: "Analyze a new receipt",      href: "/dashboard/receipts/upload", icon: Receipt   },
  { label: "Set a budget",  description: "Define a monthly limit",     href: "/dashboard/budgets",         icon: Target    },
  { label: "Create a goal", description: "Save toward what matters",   href: "/dashboard/goals",           icon: Flag      },
];

const modules = [
  {
    icon: BarChart3, href: "/dashboard/analytics",
    title: "Financial overview",
    description: "Monthly trends, category breakdown, top merchants, and behavioral observations.",
  },
  {
    icon: Repeat, href: "/dashboard/recurring",
    title: "Recurring payments",
    description: "Subscriptions and recurring bills, with cancellation candidates surfaced.",
  },
  {
    icon: TrendingDown, href: "/dashboard/leaks",
    title: "Recoverable spending",
    description: "Duplicate subscriptions, price creep, silent drains, and bank fees in one view.",
  },
  {
    icon: Target, href: "/dashboard/budgets",
    title: "Budgets and goals",
    description: "Set monthly limits, save toward what matters, and track progress over time.",
  },
  {
    icon: Receipt, href: "/dashboard/receipts",
    title: "Receipt review",
    description: "Receipt analysis with editable extraction and review-first confirmation.",
  },
  {
    icon: Brain, href: "/dashboard/insights",
    title: "Observations",
    description: "Behavioral patterns, recommendations, and the long-term cost of recurring choices.",
  },
];
