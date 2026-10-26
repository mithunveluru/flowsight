"use client";

import { useEffect } from "react";
import Link from "next/link";
import { CheckCircle2, HelpCircle, RefreshCw, Repeat, Sparkles, Trash2, TrendingDown } from "lucide-react";
import { useRecurring, useRecurringAction, useRescanRecurring } from "@/features/recurring/hooks";
import { recurringEvidence } from "@/features/insights/evidence";
import { useAnnounce } from "@/components/ui/announcer";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import type {
  ConfidenceTier,
  RecurringPattern,
  RecurringStatus,
} from "@/features/recurring/types";
import { Button } from "@/components/ui/button";
import { StatusMarker } from "@/components/ui/signals";
import { CountUp, Reveal } from "@/components/motion/primitives";
import { Alert, PageTitle, Stat, Surface } from "@/components/insight/page-kit";
import { cn } from "@/lib/utils";

const fmtShort = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const STATUS_STYLES: Record<RecurringStatus, { label: string; cls: string }> = {
  ACTIVE:   { label: "Active",    cls: "text-positive" },
  DUE_SOON: { label: "Due soon",  cls: "text-primary"  },
  OVERDUE:  { label: "Overdue",   cls: "text-caution"   },
  MISSED:   { label: "Missed",    cls: "text-warning"     },
};

const TIER_STYLES: Record<ConfidenceTier, { label: string; cls: string }> = {
  HIGH:     { label: "High",     cls: "text-positive" },
  MEDIUM:   { label: "Medium",   cls: "text-primary"  },
  POSSIBLE: { label: "Possible", cls: "text-ink-meta"   },
};

export default function RecurringPage() {
  const query = useRecurring();
  const rescan = useRescanRecurring();
  const action = useRecurringAction();
  const announce = useAnnounce();
  const patterns = query.data ?? [];
  const loading = query.isPending;
  const scanning = rescan.isPending;
  const busyId = action.isPending ? action.variables?.id ?? null : null;

  // detection runs on the server each time the page opens, as before
  const { mutate: runRescan } = rescan;
  useEffect(() => { runRescan(); }, [runRescan]);

  const scan = () => rescan.mutate(undefined, { onSuccess: (p) => announce(`Scan finished. ${p.length} recurring ${p.length === 1 ? "charge" : "charges"} found.`) });

  const act = (id: string, kind: "confirm" | "unconfirm" | "dismiss") =>
    action.mutate(
      { id, action: kind },
      {
        onSuccess: () => announce(kind === "confirm" ? "Confirmed as recurring." : kind === "unconfirm" ? "Confirmation removed." : "Dismissed."),
        onError: () => announce("That did not save. The list was restored.", "assertive"),
      }
    );
  const handleDismiss = (id: string) => act(id, "dismiss");
  const handleConfirm = (id: string) => act(id, "confirm");
  const handleUnconfirm = (id: string) => act(id, "unconfirm");
  const error = action.isError ? "That change did not save, so the list was restored." : null;

  // Partition into three tiers for the UI
  const confirmed = patterns.filter((p) => p.isUserConfirmed);
  const detected  = patterns.filter((p) => !p.isUserConfirmed && p.confidenceTier !== "POSSIBLE");
  const possible  = patterns.filter((p) => !p.isUserConfirmed && p.confidenceTier === "POSSIBLE");

  const totalMonthly = patterns.reduce((s, p) => s + Number(p.monthlyEquivalent), 0);
  const totalAnnual  = patterns.reduce((s, p) => s + Number(p.annualCost), 0);
  const cancellable  = patterns.filter((p) => p.isCancellationCandidate).length;

  const rupee = (v: number) => `₹${Math.round(v).toLocaleString("en-IN")}`;

  return (
    <div className="space-y-sp-hero">
      <PageTitle
        title="Subscriptions"
        lead="The recurring charges we spotted in your history. Confirm the real ones and dismiss the rest so we get it right."
        actions={
          <Button variant="outline" className="coarse:h-11" onClick={scan} disabled={scanning || loading}>
            <RefreshCw className={cn(scanning && "animate-spin")} aria-hidden="true" />
            {scanning ? "Scanning" : "Scan again"}
          </Button>
        }
      />

      {error && <Alert>{error}</Alert>}

      {query.isError && !query.data ? (
        <ErrorState title="Subscriptions did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : loading ? (
        <LoadingState variant="table" rows={3} label="Loading subscriptions" />
      ) : patterns.length === 0 ? (
        <EmptyState
          icon={<Repeat />}
          title="No subscriptions found yet"
          description="Once we see the same charge a couple of times, it will show up here automatically."
          action={
            <Button onClick={scan} disabled={scanning}>
              <RefreshCw className={cn(scanning && "animate-spin")} aria-hidden="true" />
              {scanning ? "Scanning" : "Scan now"}
            </Button>
          }
          secondaryAction={
            <Button variant="outline" asChild>
              <Link href="/dashboard/transactions">Add transactions</Link>
            </Button>
          }
        />
      ) : (
        <>
          <Surface tone="blue" className="grid gap-sp-giant p-sp-max sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Per month"><CountUp value={Math.round(totalMonthly)} format={rupee} /></Stat>
            <Stat label="Per year"><CountUp value={Math.round(totalAnnual)} format={rupee} /></Stat>
            <Stat label="Recurring charges"><CountUp value={patterns.length} format={String} /></Stat>
            <Stat label="Worth cancelling"><CountUp value={cancellable} format={String} /></Stat>
          </Surface>

          {/* Confirmed section */}
          {confirmed.length > 0 && (
            <Section
              title="Confirmed by you"
              count={confirmed.length}
              icon={<CheckCircle2 className="h-4 w-4 text-positive" />}
              hint="We'll keep these and refresh them every time you re-scan."
            >
              <PatternsTable
                patterns={confirmed}
                onDismiss={handleDismiss}
                onConfirm={handleConfirm}
                onUnconfirm={handleUnconfirm}
                busyId={busyId}
              />
            </Section>
          )}

          {/* Detected section */}
          {detected.length > 0 && (
            <Section
              title="Looks recurring"
              count={detected.length}
              icon={<Sparkles className="h-4 w-4 text-primary" />}
              hint="We're fairly confident these repeat. Confirm the ones that do."
            >
              <PatternsTable
                patterns={detected}
                onDismiss={handleDismiss}
                onConfirm={handleConfirm}
                onUnconfirm={handleUnconfirm}
                busyId={busyId}
              />
            </Section>
          )}

          {/* Possible section */}
          {possible.length > 0 && (
            <Section
              title="Maybe recurring"
              count={possible.length}
              icon={<HelpCircle className="h-4 w-4 text-ink-meta" />}
              hint="We're not sure about these. Confirm the ones that repeat, dismiss the rest."
            >
              <PatternsTable
                patterns={possible}
                onDismiss={handleDismiss}
                onConfirm={handleConfirm}
                onUnconfirm={handleUnconfirm}
                busyId={busyId}
              />
            </Section>
          )}
        </>
      )}
    </div>
  );
}

function Section({
  title, count, icon, hint, children,
}: {
  title: string;
  count: number;
  icon: React.ReactNode;
  hint: string;
  children: React.ReactNode;
}) {
  return (
    <Reveal>
      <section>
        <h2 className="flex items-center gap-sp-sm text-ty-subhead text-ink">
          {icon}
          {title}
          <span className="text-ty-link font-normal text-ink-meta">{count}</span>
        </h2>
        <p className="mb-sp-wide mt-sp-xs text-sm text-ink-meta">{hint}</p>
        <div className="relative overflow-x-auto">{children}</div>
      </section>
    </Reveal>
  );
}

function PatternsTable({
  patterns, onDismiss, onConfirm, onUnconfirm, busyId,
}: {
  patterns: RecurringPattern[];
  onDismiss:   (id: string) => void;
  onConfirm:   (id: string) => void;
  onUnconfirm: (id: string) => void;
  busyId:      string | null;
}) {
  return (
    <table>
      <thead>
        <tr>
          {["Merchant", "Period", "Confidence", "Amount", "Next expected", "Status"].map((h) => (
            <th key={h} className="whitespace-nowrap">{h}</th>
          ))}
          <th><span className="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        {patterns.map((p) => (
          <PatternRow
            key={p.id}
            pattern={p}
            busy={busyId === p.id}
            onDismiss={onDismiss}
            onConfirm={onConfirm}
            onUnconfirm={onUnconfirm}
          />
        ))}
      </tbody>
    </table>
  );
}

function PatternRow({
  pattern, busy, onDismiss, onConfirm, onUnconfirm,
}: {
  pattern: RecurringPattern;
  busy: boolean;
  onDismiss:   (id: string) => void;
  onConfirm:   (id: string) => void;
  onUnconfirm: (id: string) => void;
}) {
  const status = STATUS_STYLES[pattern.status];
  const tier   = TIER_STYLES[pattern.confidenceTier];

  return (
    <tr>
      <td>
        <div className="flex items-center gap-1.5">
          <span className="font-medium text-ink">{pattern.merchant}</span>
          {pattern.isCancellationCandidate && (
            <span className="inline-flex items-center gap-1 text-xs font-medium text-primary">
              <TrendingDown className="h-3 w-3" strokeWidth={2} /> Optional
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs text-ink-meta">
          Seen {pattern.occurrenceCount} times so far
        </p>
        <EvidenceDrawer
          title={`${pattern.merchant} repeats ${pattern.periodLabel.toLowerCase()}`}
          value={pattern.probability != null ? `${Math.round(Number(pattern.probability) * 100)}% likely recurring` : undefined}
          evidence={recurringEvidence(pattern)}
          trigger={<EvidenceTrigger label={`${pattern.merchant} is recurring`} className="-ml-1.5 mt-0.5 coarse:h-11 coarse:min-w-11" />}
        />
      </td>
      <td className="whitespace-nowrap text-xs text-ink-meta">
        {pattern.periodLabel}
      </td>
      <td>
        {pattern.scoredBy === "MODEL" && pattern.probability != null ? (
          <ConfidenceChip value={pattern.probability} showValue />
        ) : (
          <>
            <StatusMarker label={tier.label} className={tier.cls} />
            <p className="mt-0.5 text-xs text-ink-meta">
              {(Number(pattern.confidence) * 100).toFixed(0)}%
            </p>
          </>
        )}
      </td>
      <td className="tabular-nums">
        <span className="font-medium text-ink">{formatINR(pattern.estimatedAmount)}</span>
        <p className="mt-0.5 text-xs text-ink-meta">
          {formatINR(pattern.monthlyEquivalent)}/mo equivalent
        </p>
      </td>
      <td className="whitespace-nowrap text-xs text-ink-meta">
        {pattern.nextExpectedDate
          ? new Date(pattern.nextExpectedDate).toLocaleDateString("en-IN", {
              day: "numeric", month: "short", year: "numeric",
            })
          : "—"}
        {pattern.nextExpectedLow && pattern.nextExpectedHigh && (
          <span className="block text-ink-meta">
            likely {fmtShort(pattern.nextExpectedLow)} to {fmtShort(pattern.nextExpectedHigh)}
          </span>
        )}
        {pattern.daysUntilNext !== 0 && (
          <span className={cn("ml-1 block", pattern.daysUntilNext < 0 ? "text-caution" : "text-ink-meta")}>
            ({pattern.daysUntilNext < 0
              ? `${Math.abs(pattern.daysUntilNext)}d ago`
              : `in ${pattern.daysUntilNext}d`})
          </span>
        )}
      </td>
      <td>
        <StatusMarker label={status.label} className={status.cls} />
      </td>
      <td>
        <div className="flex items-center gap-sp-xs">
          {pattern.isUserConfirmed ? (
            <Button
              variant="ghost"
              size="icon-sm"
              className="text-positive coarse:h-11 coarse:w-11"
              onClick={() => onUnconfirm(pattern.id)}
              disabled={busy}
              title="Remove confirmation"
              aria-label={`Remove confirmation for ${pattern.merchant}`}
            >
              <CheckCircle2 aria-hidden="true" />
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="icon-sm"
              className="coarse:h-11 coarse:w-11"
              onClick={() => onConfirm(pattern.id)}
              disabled={busy}
              title="Confirm as recurring"
              aria-label={`Confirm ${pattern.merchant} as recurring`}
            >
              <CheckCircle2 aria-hidden="true" />
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon-sm"
            className="coarse:h-11 coarse:w-11"
            onClick={() => onDismiss(pattern.id)}
            disabled={busy}
            title="Not recurring, dismiss"
            aria-label={`Dismiss ${pattern.merchant}, not recurring`}
          >
            <Trash2 aria-hidden="true" />
          </Button>
        </div>
      </td>
    </tr>
  );
}
