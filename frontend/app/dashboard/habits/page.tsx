"use client";

import Link from "next/link";
import {
  Brain,
  Check,
  Compass,
  Lightbulb,
  Sparkles,
  Telescope,
  X,
} from "lucide-react";
import { useInsights, useRecommendationFeedback } from "@/features/insights/hooks";
import { canAnswer } from "@/features/insights/feedback";
import { recommendationEvidence } from "@/features/insights/evidence";
import type {
  BehavioralPattern,
  CategoryDrift,
  ConsequenceProjection,
  Recommendation,
} from "@/features/insights/types";
import { Button } from "@/components/ui/button";
import { useAnnounce } from "@/components/ui/announcer";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { ErrorState, LoadingState } from "@/components/ui/states";
import {
  AmbientGlow,
  AnimatedNumber,
  FadeIn,
  RevealOnScroll,
  StaggerContainer,
  StaggerItem,
} from "@/components/motion/primitives";
import {
  EvidenceChips,
  ForecastCell,
  ForecastPanel,
  InsightCallout,
  SignalCard,
  SignalSectionHeader,
} from "@/components/ui/signals";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function formatINRCompact(v: number) {
  if (v >= 10_000_000) return `₹${(v / 10_000_000).toFixed(1)}Cr`;
  if (v >= 100_000)    return `₹${(v / 100_000).toFixed(1)}L`;
  if (v >= 1_000)      return `₹${(v / 1_000).toFixed(0)}K`;
  return formatINR(v);
}

export default function InsightsPage() {
  const query = useInsights();

  if (query.isPending) {
    return (
      <div className="space-y-8">
        <Header />
        <LoadingState variant="card" rows={3} label="Reading your spending habits" />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="space-y-8">
        <Header />
        <ErrorState title="Habits did not load" error={query.error} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const data = query.data;
  const hasPatterns         = data.profile.patterns.length > 0;
  const hasRecommendations  = data.recommendations.length > 0;
  const hasConsequences     = data.topConsequences.length > 0;
  const drifts              = data.profile.drifts ?? [];
  const waiting             = data.profile.needsMoreHistory ?? [];

  return (
    <div className="space-y-12">
      <FadeIn><Header summary={data.narrative?.text ?? data.profile.summary} narrative={!!data.narrative} /></FadeIn>

      {/* Hero — total potential savings, with ambient glow + animated counters */}
      {data.totalPotentialMonthlySaving > 0 && (
        <FadeIn delay={0.08}>
          <SavingsHero
            monthly={data.totalPotentialMonthlySaving}
            annual={data.totalPotentialAnnualSaving}
            recommendations={data.recommendations}
          />
        </FadeIn>
      )}

      {/* Behavioral patterns — scroll-reveal */}
      {hasPatterns && (
        <RevealOnScroll delay={0.04}>
          <PatternsSection patterns={data.profile.patterns} drifts={drifts} />
        </RevealOnScroll>
      )}

      {waiting.length > 0 && (
        <p role="note" className="text-caption">
          Not checked yet because your history is too short: {waiting.map(humanize).join(", ")}.
        </p>
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

function humanize(code: string) {
  const t = code.replace(/_/g, " ").toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

function Header({ summary, narrative }: { summary?: string; narrative?: boolean }) {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-foreground">
        Habits
      </h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        {summary
          ? narrative ? summary : `In short: ${summary}.`
          : "The patterns in how you spend, what you could do about them, and what your regular choices add up to over time."}
      </p>
    </div>
  );
}

function SavingsHero({
  monthly,
  annual,
  recommendations,
}: {
  monthly: number;
  annual: number;
  recommendations: Recommendation[];
}) {
  const counted = recommendations.filter((r) => r.source === "LEAK" && r.potentialMonthlySaving);
  return (
    <section
      className="relative overflow-hidden rounded-xl border bg-gradient-to-br from-positive-soft via-card to-card p-8 lg:p-10"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <AmbientGlow />
      <div className="relative flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-positive/25 bg-card">
          <Sparkles className="h-5 w-5 text-positive" strokeWidth={1.75} />
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-positive">
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
            <EvidenceDrawer
              title="Potential monthly savings"
              value={`${formatINR(monthly)}/mo`}
              evidence={{
                method:
                  "The sum of the savings from the money-drain recommendations below. Habit estimates overlap with them, so they are not added in.",
                inputs: counted.map((r) => ({
                  label: r.title,
                  value: `${formatINR(r.potentialMonthlySaving as number)}/mo`,
                })),
              }}
              trigger={<EvidenceTrigger label="the potential monthly savings" className="ml-1 align-middle" />}
            />
          </p>
        </div>
      </div>
    </section>
  );
}

function PatternsSection({ patterns, drifts }: { patterns: BehavioralPattern[]; drifts: CategoryDrift[] }) {
  return (
    <section>
      <SignalSectionHeader
        icon={<Compass className="h-3.5 w-3.5" />}
        title="Patterns we noticed"
        subtitle="How your money tends to move, week by week"
      />
      <StaggerContainer className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {patterns.map((p) => (
          <StaggerItem key={p.code}>
            <SignalCard severity={p.severity} title={p.title}>
              <p className="text-xs text-muted-foreground leading-relaxed">
                {p.description}
              </p>
              <p className="mt-3 text-[11px] text-muted-foreground/80">{p.context}</p>
              {p.threshold != null && (
                <p className="mt-1 text-[11px] text-muted-foreground/80">
                  Flagged above {p.threshold.toLocaleString("en-IN", { maximumFractionDigits: 2 })} {p.unit} for you.
                </p>
              )}
            </SignalCard>
          </StaggerItem>
        ))}
      </StaggerContainer>
      {drifts.length > 0 && (
        <ul className="mt-4 space-y-1.5 text-sm text-muted-foreground">
          {drifts.map((d) => (
            <li key={d.category}>
              <span className="font-medium text-foreground">{d.displayName}</span>{" "}
              {d.direction === "UP" ? "up" : "down"} about{" "}
              <span className="tabular-nums">{formatINR(Math.abs(d.monthlyChange))}</span> a month since{" "}
              {new Date(d.since).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}.
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function RecommendationsSection({ recommendations }: { recommendations: Recommendation[] }) {
  return (
    <section>
      <SignalSectionHeader
        icon={<Lightbulb className="h-3.5 w-3.5" />}
        title="Recommendations"
        subtitle="Actionable steps ranked by impact"
      />
      <StaggerContainer className="space-y-3" stagger={0.05}>
        {recommendations.map((r, i) => (
          <StaggerItem key={r.id ?? i}>
            <RecommendationCard recommendation={r} />
          </StaggerItem>
        ))}
      </StaggerContainer>
    </section>
  );
}

function RecommendationCard({ recommendation: r }: { recommendation: Recommendation }) {
  const feedback = useRecommendationFeedback();
  const announce = useAnnounce();
  const hasSaving = r.potentialMonthlySaving != null && r.potentialMonthlySaving > 0;
  const accepted = r.feedback === "ACCEPTED";
  const id = canAnswer(r) ? r.id : null;

  const answer = (action: "ACCEPTED" | "DISMISSED") => {
    if (!id) return;
    feedback.mutate(
      { id, action },
      {
        onSuccess: () => announce(action === "ACCEPTED" ? `Accepted: ${r.title}` : `Dismissed: ${r.title}`),
        onError: () => announce("That did not save. The recommendation was restored.", "assertive"),
      }
    );
  };

  return (
    <SignalCard
      severity={r.confidence}
      title={r.title}
      metric={
        hasSaving ? (
          <>
            <p className="text-base font-semibold tabular-nums text-foreground">
              {formatINR(r.potentialMonthlySaving as number)}
              <span className="text-xs font-normal text-muted-foreground">/mo</span>
              <EvidenceDrawer
                title={r.title}
                value={`${formatINR(r.potentialMonthlySaving as number)}/mo`}
                evidence={recommendationEvidence(r)}
                trigger={<EvidenceTrigger label={`${r.title} could save ${formatINR(r.potentialMonthlySaving as number)} a month`} className="ml-1" />}
              />
            </p>
            {r.potentialAnnualSaving != null && (
              <p className="text-xs text-muted-foreground">
                {formatINR(r.potentialAnnualSaving)}/yr
              </p>
            )}
          </>
        ) : undefined
      }
    >
      <p className="text-sm text-muted-foreground leading-relaxed">{r.description}</p>
      {r.suggestedAction && (
        <InsightCallout className="mt-3" tone="signal">
          {r.suggestedAction}
        </InsightCallout>
      )}
      {r.evidence.length > 0 && <EvidenceChips className="mt-3" items={r.evidence} />}
      {r.rankingReason && <p className="mt-3 text-[11px] text-muted-foreground/80">{r.rankingReason}</p>}
      {id && (
        <div className="mt-4 flex flex-wrap items-center gap-2">
          {accepted ? (
            <span className="inline-flex items-center gap-1 text-xs font-medium text-positive">
              <Check className="h-3.5 w-3.5" aria-hidden="true" /> Accepted
            </span>
          ) : (
            <Button size="sm" variant="outline" onClick={() => answer("ACCEPTED")} disabled={feedback.isPending}>
              <Check className="h-4 w-4" aria-hidden="true" /> I&apos;ll do this
              <span className="sr-only">: {r.title}</span>
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => answer("DISMISSED")} disabled={feedback.isPending}>
            <X className="h-4 w-4" aria-hidden="true" /> Not for me
            <span className="sr-only">: {r.title}</span>
          </Button>
          {feedback.isError && (
            <span role="alert" className="text-xs text-warning">
              Could not save that. Try again.
            </span>
          )}
        </div>
      )}
    </SignalCard>
  );
}

function ConsequencesSection({ consequences }: { consequences: ConsequenceProjection[] }) {
  return (
    <section>
      <SignalSectionHeader
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
      className="card-refined overflow-hidden"
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

      <ForecastPanel columns={4}>
        <ForecastCell label="1 year"   value={formatINR(p.yearCost)} />
        <ForecastCell label="5 years"  value={formatINR(p.fiveYearCost)} />
        <ForecastCell label="10 years" value={formatINR(p.tenYearCost)} />
        <ForecastCell
          label="10 yr opportunity cost"
          value={formatINRCompact(p.tenYearOpportunityCost)}
          accent
          hint="if invested at 8%/yr"
        />
      </ForecastPanel>
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
      <p className="text-sm font-medium text-foreground">Nothing to show just yet</p>
      <p className="mt-1 text-xs text-muted-foreground max-w-sm mx-auto">
        We need a few months of activity before patterns become reliable. Add some transactions and we&apos;ll start building the picture.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Link
          href="/dashboard/transactions"
          className="rounded-md bg-primary px-4 py-2 text-xs font-medium text-primary-foreground hover:opacity-90 transition-opacity"
        >
          Add transactions
        </Link>
        <Link
          href="/dashboard/trends"
          className="rounded-md border px-4 py-2 text-xs font-medium text-foreground hover:bg-muted transition-colors"
          style={{ borderColor: "hsl(var(--border))" }}
        >
          See your trends
        </Link>
      </div>
    </div>
  );
}
