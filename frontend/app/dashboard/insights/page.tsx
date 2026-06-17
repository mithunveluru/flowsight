"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Brain,
  ChevronRight,
  Compass,
  Lightbulb,
  PiggyBank,
  Sparkles,
  Telescope,
  TrendingUp,
} from "lucide-react";
import { insightsApi } from "@/features/insights/api";
import type {
  BehavioralPattern,
  ConsequenceProjection,
  InsightsResponse,
  Recommendation,
  Severity,
} from "@/features/insights/types";
import {
  AmbientGlow,
  AnimatedNumber,
  FadeIn,
  RevealOnScroll,
  StaggerContainer,
  StaggerItem,
} from "@/components/motion/primitives";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function formatINRCompact(v: number) {
  if (v >= 10_000_000) return `₹${(v / 10_000_000).toFixed(1)}Cr`;
  if (v >= 100_000)    return `₹${(v / 100_000).toFixed(1)}L`;
  if (v >= 1_000)      return `₹${(v / 1_000).toFixed(0)}K`;
  return formatINR(v);
}

const SEVERITY_DOT: Record<Severity, string> = {
  HIGH:   "bg-red-500",
  MEDIUM: "bg-amber-500",
  LOW:    "bg-blue-500",
};

export default function InsightsPage() {
  const [data,    setData]    = useState<InsightsResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    insightsApi.get()
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="space-y-8 animate-fade-in">
        <Header />
        <LoadingSkeleton />
      </div>
    );
  }

  if (!data) {
    return (
      <div className="space-y-8 animate-fade-in">
        <Header />
        <EmptyState />
      </div>
    );
  }

  const hasPatterns         = data.profile.patterns.length > 0;
  const hasRecommendations  = data.recommendations.length > 0;
  const hasConsequences     = data.topConsequences.length > 0;

  return (
    <div className="space-y-12">
      <FadeIn><Header summary={data.profile.summary} /></FadeIn>

      {/* Hero — total potential savings, with ambient glow + animated counters */}
      {data.totalPotentialMonthlySaving > 0 && (
        <FadeIn delay={0.08}>
          <SavingsHero
            monthly={data.totalPotentialMonthlySaving}
            annual={data.totalPotentialAnnualSaving}
          />
        </FadeIn>
      )}

      {/* Behavioral patterns — scroll-reveal */}
      {hasPatterns && (
        <RevealOnScroll delay={0.04}>
          <PatternsSection patterns={data.profile.patterns} />
        </RevealOnScroll>
      )}

      {/* Recommendations */}
      {hasRecommendations && (
        <RevealOnScroll delay={0.06}>
          <RecommendationsSection recommendations={data.recommendations} />
        </RevealOnScroll>
      )}

      {/* Decision consequences — FlowSight's signature view */}
      {hasConsequences && (
        <RevealOnScroll delay={0.08}>
          <ConsequencesSection consequences={data.topConsequences} />
        </RevealOnScroll>
      )}

      {!hasPatterns && !hasRecommendations && !hasConsequences && (
        <FadeIn><EmptyState /></FadeIn>
      )}
    </div>
  );
}

function Header({ summary }: { summary?: string }) {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-foreground">
        Observations
      </h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        {summary
          ? `Your spending profile: ${summary}.`
          : "Patterns in your spending, gentle recommendations, and the long-term cost of recurring choices."}
      </p>
    </div>
  );
}

function SavingsHero({ monthly, annual }: { monthly: number; annual: number }) {
  return (
    <section
      className="relative overflow-hidden rounded-xl border bg-gradient-to-br from-emerald-50 via-white to-white p-8 lg:p-10"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <AmbientGlow />
      <div className="relative flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-emerald-200 bg-white">
          <Sparkles className="h-5 w-5 text-emerald-600" strokeWidth={1.75} />
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-emerald-700">
            Potential monthly savings
          </p>
          <p className="mt-1.5 stat-value text-4xl lg:text-5xl">
            <AnimatedNumber value={monthly} format={(v) => `₹${v.toLocaleString("en-IN")}`} />
          </p>
          <p className="mt-2 text-sm text-muted-foreground">
            That&apos;s{" "}
            <span className="font-medium text-foreground tabular-nums">
              <AnimatedNumber value={annual} format={(v) => `₹${v.toLocaleString("en-IN")}`} />
            </span>{" "}
            a year if you act on every suggestion below.
          </p>
        </div>
      </div>
    </section>
  );
}

function PatternsSection({ patterns }: { patterns: BehavioralPattern[] }) {
  return (
    <section>
      <SectionHeader
        icon={<Compass className="h-3.5 w-3.5" />}
        title="Behavioral patterns"
        subtitle="How your money moves, week by week"
      />
      <StaggerContainer className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {patterns.map((p) => (
          <StaggerItem key={p.code}>
            <PatternCard pattern={p} />
          </StaggerItem>
        ))}
      </StaggerContainer>
    </section>
  );
}

function PatternCard({ pattern }: { pattern: BehavioralPattern }) {
  return (
    <div
      className="rounded-xl border bg-card p-5"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex items-start justify-between mb-3">
        <p className="text-sm font-semibold text-foreground leading-snug">
          {pattern.title}
        </p>
        <span className={cn("mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full", SEVERITY_DOT[pattern.severity])} />
      </div>
      <p className="text-xs text-muted-foreground leading-relaxed">
        {pattern.description}
      </p>
      <p className="mt-3 text-[11px] text-muted-foreground/80">{pattern.context}</p>
    </div>
  );
}

function RecommendationsSection({ recommendations }: { recommendations: Recommendation[] }) {
  return (
    <section>
      <SectionHeader
        icon={<Lightbulb className="h-3.5 w-3.5" />}
        title="Recommendations"
        subtitle="Actionable steps ranked by impact"
      />
      <StaggerContainer className="space-y-3" stagger={0.05}>
        {recommendations.map((r, i) => (
          <StaggerItem key={i}>
            <RecommendationCard recommendation={r} />
          </StaggerItem>
        ))}
      </StaggerContainer>
    </section>
  );
}

function RecommendationCard({ recommendation: r }: { recommendation: Recommendation }) {
  return (
    <div
      className="rounded-xl border bg-card p-5"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex items-start gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-foreground">{r.title}</p>
            <span className={cn("h-1.5 w-1.5 rounded-full shrink-0", SEVERITY_DOT[r.confidence])} />
          </div>
          <p className="mt-1.5 text-sm text-muted-foreground leading-relaxed">
            {r.description}
          </p>
          {r.suggestedAction && (
            <div className="mt-3 flex items-start gap-2 rounded-md bg-muted/50 px-3 py-2.5 text-xs text-foreground">
              <ArrowRight className="mt-0.5 h-3 w-3 shrink-0 text-muted-foreground" strokeWidth={2} />
              <span>{r.suggestedAction}</span>
            </div>
          )}
          {r.evidence.length > 0 && (
            <div className="mt-3 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
              {r.evidence.map((e, i) => (
                <span key={i}>
                  {i > 0 && <span className="mx-1.5 text-muted-foreground/40">·</span>}
                  {e}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Savings impact */}
        {r.potentialMonthlySaving != null && r.potentialMonthlySaving > 0 && (
          <div className="shrink-0 text-right">
            <p className="text-base font-semibold tabular-nums text-foreground">
              {formatINR(r.potentialMonthlySaving)}
              <span className="text-xs font-normal text-muted-foreground">/mo</span>
            </p>
            {r.potentialAnnualSaving != null && (
              <p className="text-xs text-muted-foreground">
                {formatINR(r.potentialAnnualSaving)}/yr
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function ConsequencesSection({ consequences }: { consequences: ConsequenceProjection[] }) {
  return (
    <section>
      <SectionHeader
        icon={<Telescope className="h-3.5 w-3.5" />}
        title="The long view"
        subtitle="What your recurring choices actually cost over time"
      />
      <StaggerContainer className="space-y-3" stagger={0.05}>
        {consequences.map((c, i) => (
          <StaggerItem key={i}>
            <ConsequenceCard projection={c} />
          </StaggerItem>
        ))}
      </StaggerContainer>
      <p className="mt-4 text-[11px] text-muted-foreground">
        Opportunity cost assumes 8% annual return compounded monthly — a conservative long-run equity index assumption.
      </p>
    </section>
  );
}

function ConsequenceCard({ projection: p }: { projection: ConsequenceProjection }) {
  return (
    <div
      className="rounded-xl border bg-card overflow-hidden"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex items-start justify-between gap-4 px-5 pt-5 pb-4">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-foreground">{p.label}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{p.reflection}</p>
        </div>
        <p className="shrink-0 text-sm font-medium text-foreground tabular-nums">
          {formatINR(p.monthlyAmount)}<span className="text-xs text-muted-foreground">/mo</span>
        </p>
      </div>

      {/* Projection timeline */}
      <div className="grid grid-cols-2 lg:grid-cols-4 divide-x divide-y lg:divide-y-0 border-t" style={{ borderColor: "hsl(var(--border))" }}>
        <Projection label="1 year"    value={formatINR(p.yearCost)}     />
        <Projection label="5 years"   value={formatINR(p.fiveYearCost)} />
        <Projection label="10 years"  value={formatINR(p.tenYearCost)}  />
        <Projection
          label="10 yr opportunity cost"
          value={formatINRCompact(p.tenYearOpportunityCost)}
          accent
          hint="if invested at 8%/yr"
        />
      </div>
    </div>
  );
}

function Projection({
  label, value, accent, hint,
}: {
  label: string;
  value: string;
  accent?: boolean;
  hint?: string;
}) {
  return (
    <div className="px-5 py-3.5">
      <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p className={cn(
        "mt-1 text-base font-semibold tabular-nums",
        accent ? "text-emerald-700" : "text-foreground"
      )}>
        {value}
      </p>
      {hint && <p className="mt-0.5 text-[10px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

function SectionHeader({
  icon, title, subtitle,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="section-header items-center">
      <div className="flex items-center gap-2">
        <span className="text-muted-foreground">{icon}</span>
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <span className="text-xs text-muted-foreground">· {subtitle}</span>
      </div>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-6">
      <div className="skeleton h-32 rounded-xl" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="skeleton h-28 rounded-xl" />
        ))}
      </div>
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="skeleton h-24 rounded-xl" />
        ))}
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div
      className="rounded-xl border bg-card px-6 py-16 text-center"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <Brain className="mx-auto h-10 w-10 text-muted-foreground/40 mb-3" strokeWidth={1.5} />
      <p className="text-sm font-medium text-foreground">Not enough to observe yet</p>
      <p className="mt-1 text-xs text-muted-foreground max-w-sm mx-auto">
        Patterns become reliable after a few months of activity. Add transactions to start building the picture.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Link
          href="/dashboard/transactions"
          className="rounded-md bg-primary px-4 py-2 text-xs font-medium text-primary-foreground hover:opacity-90 transition-opacity"
        >
          Add transactions
        </Link>
        <Link
          href="/dashboard/analytics"
          className="rounded-md border px-4 py-2 text-xs font-medium text-foreground hover:bg-muted transition-colors"
          style={{ borderColor: "hsl(var(--border))" }}
        >
          Open overview
        </Link>
      </div>
    </div>
  );
}
