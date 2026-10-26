"use client";

import { TitleRow } from "@/components/layout/title-row";
import Link from "next/link";
import { ArrowRight, Check, X } from "lucide-react";
import { useInsights, useRecommendationFeedback } from "@/features/insights/hooks";
import { canAnswer } from "@/features/insights/feedback";
import { recommendationEvidence } from "@/features/insights/evidence";
import type {
  BehavioralPattern,
  CategoryDrift,
  ConsequenceProjection,
  Recommendation,
} from "@/features/insights/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAnnounce } from "@/components/ui/announcer";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { CountUp, Reveal, Stagger, StaggerItem } from "@/components/motion/primitives";
import { EvidenceChips, type Severity } from "@/components/ui/signals";
import { ContentBand, SectionHead, Stat, Surface } from "@/components/insight/page-kit";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function formatINRCompact(v: number) {
  if (v >= 10_000_000) return `₹${(v / 10_000_000).toFixed(1)}Cr`;
  if (v >= 100_000) return `₹${(v / 100_000).toFixed(1)}L`;
  if (v >= 1_000) return `₹${(v / 1_000).toFixed(0)}K`;
  return formatINR(v);
}

const SIGNAL: Record<Severity, { label: string; variant: "red" | "amber" | "blue" }> = {
  HIGH: { label: "High signal", variant: "red" },
  MEDIUM: { label: "Moderate signal", variant: "amber" },
  LOW: { label: "Low signal", variant: "blue" },
};

const whyClass = "ml-sp-xs align-middle coarse:h-11 coarse:min-w-11";

export default function InsightsPage() {
  const query = useInsights();

  if (query.isPending) {
    return (
      <div className="space-y-sp-hero">
        <TitleRow rule={false} title="Habits" />
        <LoadingState variant="card" rows={3} label="Reading your spending habits" />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="space-y-sp-hero">
        <TitleRow rule={false} title="Habits" />
        <ErrorState title="Habits did not load" error={query.error} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const data = query.data;
  const hasPatterns = data.profile.patterns.length > 0;
  const hasRecommendations = data.recommendations.length > 0;
  const hasConsequences = data.topConsequences.length > 0;
  const drifts = data.profile.drifts ?? [];
  const waiting = data.profile.needsMoreHistory ?? [];
  const summary = data.narrative?.text ?? data.profile.summary;

  return (
    <div className="space-y-sp-hero">
      <TitleRow
        rule={false}
        title="Habits"
        description={
          summary
            ? data.narrative ? summary : `In short: ${summary}.`
            : "The patterns in how you spend, what you could do about them, and what your regular choices add up to over time."
        }
      />

      {data.totalPotentialMonthlySaving > 0 && (
        <SavingsFigure
          monthly={data.totalPotentialMonthlySaving}
          annual={data.totalPotentialAnnualSaving}
          recommendations={data.recommendations}
        />
      )}

      {hasPatterns && (
        <Reveal>
          <PatternsSection patterns={data.profile.patterns} drifts={drifts} />
        </Reveal>
      )}

      {waiting.length > 0 && (
        <p role="note" className="text-sm text-ink-meta">
          Not checked yet because your history is too short: {waiting.map(humanize).join(", ")}.
        </p>
      )}

      {hasRecommendations && (
        <Reveal>
          <RecommendationsSection recommendations={data.recommendations} />
        </Reveal>
      )}

      {hasConsequences && <ConsequencesSection consequences={data.topConsequences} />}

      {!hasPatterns && !hasRecommendations && !hasConsequences && (
        <EmptyState
          title="Nothing to show just yet"
          description="We need a few months of activity before patterns become reliable. Add some transactions and we will start building the picture."
          action={
            <Button asChild>
              <Link href="/dashboard/transactions">Add transactions</Link>
            </Button>
          }
          secondaryAction={
            <Button variant="outline" asChild>
              <Link href="/dashboard/trends">See your trends</Link>
            </Button>
          }
        />
      )}
    </div>
  );
}

function humanize(code: string) {
  const t = code.replace(/_/g, " ").toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

function SavingsFigure({
  monthly,
  annual,
  recommendations,
}: {
  monthly: number;
  annual: number;
  recommendations: Recommendation[];
}) {
  const counted = recommendations.filter((r) => r.source === "LEAK" && r.potentialMonthlySaving);
  const rupee = (v: number) => `₹${v.toLocaleString("en-IN")}`;
  return (
    <Surface tone="blue" className="grid gap-sp-giant p-sp-max sm:grid-cols-[1fr_auto] sm:items-end">
      <Stat label="Potential monthly savings" large>
        <CountUp value={monthly} format={rupee} />
      </Stat>
      <p className="max-w-sm text-sm text-ink-body">
        That is <span className="font-medium tabular-nums text-ink"><CountUp value={annual} format={rupee} /></span> a year if you act on every suggestion below.
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
          trigger={<EvidenceTrigger label="the potential monthly savings" className={whyClass} />}
        />
      </p>
    </Surface>
  );
}

function PatternsSection({ patterns, drifts }: { patterns: BehavioralPattern[]; drifts: CategoryDrift[] }) {
  return (
    <section>
      <SectionHead title="Patterns we noticed" lead="How your money tends to move, week by week" />
      <Stagger className="grid gap-sp-wide sm:grid-cols-2 lg:grid-cols-3">
        {patterns.map((p) => {
          const sig = SIGNAL[p.severity];
          return (
            <StaggerItem key={p.code} className="h-full">
              <Surface className="h-full">
                <Badge variant={sig.variant}>{sig.label}</Badge>
                <h3 className="mt-sp-xl text-ty-link font-medium text-ink">{p.title}</h3>
                <p className="mt-sp-sm text-sm text-ink-body">{p.description}</p>
                <p className="mt-sp-xl text-xs text-ink-meta">{p.context}</p>
                {p.threshold != null && (
                  <p className="mt-sp-xs text-xs text-ink-meta">
                    Flagged above {p.threshold.toLocaleString("en-IN", { maximumFractionDigits: 2 })} {p.unit} for you.
                  </p>
                )}
              </Surface>
            </StaggerItem>
          );
        })}
      </Stagger>
      {drifts.length > 0 && (
        <ul className="mt-sp-giant space-y-sp-sm text-sm text-ink-body">
          {drifts.map((d) => (
            <li key={d.category}>
              <span className="font-medium text-ink">{d.displayName}</span> {d.direction === "UP" ? "up" : "down"} about{" "}
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
      <SectionHead title="Recommendations" lead="Actionable steps ranked by impact" />
      <Stagger className="space-y-sp-wide">
        {recommendations.map((r, i) => (
          <StaggerItem key={r.id ?? i}>
            <RecommendationCard recommendation={r} />
          </StaggerItem>
        ))}
      </Stagger>
    </section>
  );
}

function RecommendationCard({ recommendation: r }: { recommendation: Recommendation }) {
  const feedback = useRecommendationFeedback();
  const announce = useAnnounce();
  const hasSaving = r.potentialMonthlySaving != null && r.potentialMonthlySaving > 0;
  const accepted = r.feedback === "ACCEPTED";
  const id = canAnswer(r) ? r.id : null;
  const sig = SIGNAL[r.confidence];

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
    <Surface>
      <div className="flex flex-wrap items-start justify-between gap-sp-xl">
        <div className="min-w-0">
          <Badge variant={sig.variant}>{sig.label}</Badge>
          <h3 className="mt-sp-xl text-ty-link font-medium text-ink">{r.title}</h3>
        </div>
        {hasSaving && (
          <div className="text-right tabular-nums">
            <p className="text-ty-subhead text-ink">
              {formatINR(r.potentialMonthlySaving as number)}
              <span className="text-sm font-normal text-ink-meta">/mo</span>
              <EvidenceDrawer
                title={r.title}
                value={`${formatINR(r.potentialMonthlySaving as number)}/mo`}
                evidence={recommendationEvidence(r)}
                trigger={
                  <EvidenceTrigger
                    label={`${r.title} could save ${formatINR(r.potentialMonthlySaving as number)} a month`}
                    className={whyClass}
                  />
                }
              />
            </p>
            {r.potentialAnnualSaving != null && (
              <p className="text-xs text-ink-meta">{formatINR(r.potentialAnnualSaving)}/yr</p>
            )}
          </div>
        )}
      </div>
      <p className="mt-sp-sm text-sm text-ink-body">{r.description}</p>
      {r.suggestedAction && (
        <p className="mt-sp-xl flex items-start gap-sp-sm rounded-md bg-band-blue p-sp-xl text-sm text-ink-body">
          <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
          {r.suggestedAction}
        </p>
      )}
      {r.evidence.length > 0 && <EvidenceChips className="mt-sp-xl" items={r.evidence} />}
      {r.rankingReason && <p className="mt-sp-xl text-xs text-ink-meta">{r.rankingReason}</p>}
      {id && (
        <div className="mt-sp-giant flex flex-wrap items-center gap-sp-sm">
          {accepted ? (
            <Badge variant="emerald" className="gap-sp-xs">
              <Check className="h-3.5 w-3.5" aria-hidden="true" /> Accepted
            </Badge>
          ) : (
            <Button size="sm" variant="outline" className="coarse:h-11" onClick={() => answer("ACCEPTED")} disabled={feedback.isPending}>
              <Check aria-hidden="true" /> I&apos;ll do this
              <span className="sr-only">: {r.title}</span>
            </Button>
          )}
          <Button size="sm" variant="ghost" className="coarse:h-11" onClick={() => answer("DISMISSED")} disabled={feedback.isPending}>
            <X aria-hidden="true" /> Not for me
            <span className="sr-only">: {r.title}</span>
          </Button>
          {feedback.isError && (
            <span role="alert" className="text-xs text-warning">
              Could not save that. Try again.
            </span>
          )}
        </div>
      )}
    </Surface>
  );
}

function ConsequencesSection({ consequences }: { consequences: ConsequenceProjection[] }) {
  return (
    <ContentBand tone="blue">
      <SectionHead title="The long view" lead="What your recurring choices actually cost over time" />
      <Stagger className="space-y-sp-wide">
        {consequences.map((c, i) => (
          <StaggerItem key={i}>
            <ConsequenceCard projection={c} />
          </StaggerItem>
        ))}
      </Stagger>
      <p className="mt-sp-giant text-xs text-ink-meta">
        Opportunity cost assumes 8% annual return compounded monthly, a conservative long-run equity index assumption.
      </p>
    </ContentBand>
  );
}

function ConsequenceCard({ projection: p }: { projection: ConsequenceProjection }) {
  const cells: { label: string; value: string; hint?: string; lead?: boolean }[] = [
    { label: "1 year", value: formatINR(p.yearCost) },
    { label: "5 years", value: formatINR(p.fiveYearCost) },
    { label: "10 years", value: formatINR(p.tenYearCost) },
    { label: "10 year opportunity cost", value: formatINRCompact(p.tenYearOpportunityCost), hint: "if invested at 8% a year", lead: true },
  ];
  return (
    <Surface tone="canvas">
      <div className="flex items-start justify-between gap-sp-wide">
        <div className="min-w-0">
          <h3 className="text-ty-link font-medium text-ink">{p.label}</h3>
          <p className="mt-sp-xs text-sm text-ink-meta">{p.reflection}</p>
        </div>
        <p className="shrink-0 tabular-nums text-ink">
          {formatINR(p.monthlyAmount)}
          <span className="text-sm text-ink-meta">/mo</span>
        </p>
      </div>
      <dl className="mt-sp-giant grid grid-cols-2 gap-sp-giant lg:grid-cols-4">
        {cells.map((c) => (
          <div key={c.label}>
            <dt className="text-xs text-ink-meta">{c.label}</dt>
            <dd className={cn("mt-sp-xs text-ty-subhead tabular-nums", c.lead ? "text-primary" : "text-ink")}>{c.value}</dd>
            {c.hint && <dd className="mt-sp-xs text-xs text-ink-meta">{c.hint}</dd>}
          </div>
        ))}
      </dl>
    </Surface>
  );
}
