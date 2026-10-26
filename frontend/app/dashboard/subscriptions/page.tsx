"use client";

import { useEffect } from "react";
import Link from "next/link";
import {
  CalendarClock,
  CheckCircle2,
  HelpCircle,
  RefreshCw,
  Repeat,
  Sparkles,
  Trash2,
  TrendingDown,
  XCircle,
} from "lucide-react";
import { useRecurring, useRecurringAction, useRescanRecurring } from "@/features/recurring/hooks";
import { recurringEvidence } from "@/features/insights/evidence";
import { useAnnounce } from "@/components/ui/announcer";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { ErrorState, LoadingState } from "@/components/ui/states";
import type {
  ConfidenceTier,
  RecurringPattern,
  RecurringStatus,
} from "@/features/recurring/types";
import { Button } from "@/components/ui/button";
import { StatusMarker } from "@/components/ui/signals";
import { cn } from "@/lib/utils";

const fmtShort = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const STATUS_STYLES: Record<RecurringStatus, { label: string; cls: string }> = {
  ACTIVE:   { label: "Active",    cls: "text-positive" },
  DUE_SOON: { label: "Due soon",  cls: "text-brand"    },
  OVERDUE:  { label: "Overdue",   cls: "text-caution"   },
  MISSED:   { label: "Missed",    cls: "text-warning"     },
};

const TIER_STYLES: Record<ConfidenceTier, { label: string; cls: string }> = {
  HIGH:     { label: "High",     cls: "text-positive" },
  MEDIUM:   { label: "Medium",   cls: "text-brand"    },
  POSSIBLE: { label: "Possible", cls: "text-muted-foreground"   },
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

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-foreground">Subscriptions</h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            The recurring charges we spotted in your history. Confirm the real ones and dismiss the rest so we get it right.
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={scan} disabled={scanning || loading}>
          <RefreshCw className={cn("h-4 w-4", scanning && "animate-spin")} />
          {scanning ? "Scanning" : "Scan again"}
        </Button>
      </div>

      {error && (
        <p role="alert" className="text-sm text-warning flex items-center gap-1.5">
          <XCircle className="h-4 w-4 shrink-0" /> {error}
        </p>
      )}

      {query.isError && !query.data ? (
        <ErrorState title="Subscriptions did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : loading ? (
        <LoadingState variant="table" rows={3} label="Loading subscriptions" />
      ) : patterns.length === 0 ? (
        <EmptyState onScan={scan} scanning={scanning} />
      ) : (
        <>
          {/* Summary cards */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Per month"                value={formatINR(totalMonthly)} icon={Repeat}        cls="text-caution"  bg="bg-caution-soft border-caution/20" />
            <StatCard label="Per year"                 value={formatINR(totalAnnual)}  icon={CalendarClock} cls="text-brand"    bg="bg-brand-soft border-brand/20"     />
            <StatCard label="Active subscriptions"     value={String(patterns.length)} icon={CheckCircle2}  cls="text-positive" bg="bg-positive-soft border-positive/20" />
            <StatCard label="Worth cancelling"         value={String(cancellable)}     icon={TrendingDown}  cls="text-brand"  bg="bg-brand-soft border-brand/20"   />
          </div>

          {/* Confirmed section */}
          {confirmed.length > 0 && (
            <Section
              title="Confirmed by you"
              count={confirmed.length}
              icon={<CheckCircle2 className="h-3.5 w-3.5 text-positive" />}
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
              icon={<Sparkles className="h-3.5 w-3.5 text-brand" />}
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
              icon={<HelpCircle className="h-3.5 w-3.5 text-muted-foreground" />}
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
    <section className="space-y-3">
      <div className="flex items-center gap-2">
        {icon}
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <span className="text-xs text-muted-foreground/70">({count})</span>
      </div>
      <p className="text-xs text-muted-foreground">{hint}</p>
      <div className="rounded-lg border border-border bg-card overflow-x-auto">
        {children}
      </div>
    </section>
  );
}

function StatCard({
  label, value, icon: Icon, cls, bg,
}: {
  label: string; value: string;
  icon: React.ElementType; cls: string; bg: string;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-5">
      <div className="flex items-center gap-2 mb-2">
        <div className={cn("flex h-7 w-7 items-center justify-center rounded-md border", bg)}>
          <Icon className={cn("h-3.5 w-3.5", cls)} />
        </div>
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
      </div>
      <p className="text-2xl font-semibold tabular-nums text-foreground">{value}</p>
    </div>
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
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border bg-muted/50">
          {["Merchant","Period","Confidence","Amount","Next expected","Status",""].map((h) => (
            <th key={h} className="px-4 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wide whitespace-nowrap">
              {h}
            </th>
          ))}
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
    <tr className="border-b border-border/60 last:border-0 hover:bg-muted/50 transition-colors">
      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5">
          <span className="font-medium text-foreground">{pattern.merchant}</span>
          {pattern.isCancellationCandidate && (
            <span className="inline-flex items-center gap-1 text-xs font-medium text-brand">
              <TrendingDown className="h-3 w-3" strokeWidth={2} /> Optional
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground/70">
          Seen {pattern.occurrenceCount} times so far
        </p>
        <EvidenceDrawer
          title={`${pattern.merchant} repeats ${pattern.periodLabel.toLowerCase()}`}
          value={pattern.probability != null ? `${Math.round(Number(pattern.probability) * 100)}% likely recurring` : undefined}
          evidence={recurringEvidence(pattern)}
          trigger={<EvidenceTrigger label={`${pattern.merchant} is recurring`} className="-ml-1.5 mt-0.5" />}
        />
      </td>
      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
        {pattern.periodLabel}
      </td>
      <td className="px-4 py-3">
        {pattern.scoredBy === "MODEL" && pattern.probability != null ? (
          <ConfidenceChip value={pattern.probability} showValue />
        ) : (
          <>
            <StatusMarker label={tier.label} className={tier.cls} />
            <p className="mt-0.5 text-xs text-muted-foreground/70">
              {(Number(pattern.confidence) * 100).toFixed(0)}%
            </p>
          </>
        )}
      </td>
      <td className="px-4 py-3 tabular-nums">
        <span className="font-medium text-foreground">{formatINR(pattern.estimatedAmount)}</span>
        <p className="mt-0.5 text-xs text-muted-foreground/70">
          {formatINR(pattern.monthlyEquivalent)}/mo equivalent
        </p>
      </td>
      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
        {pattern.nextExpectedDate
          ? new Date(pattern.nextExpectedDate).toLocaleDateString("en-IN", {
              day: "numeric", month: "short", year: "numeric",
            })
          : "—"}
        {pattern.nextExpectedLow && pattern.nextExpectedHigh && (
          <span className="block text-muted-foreground/70">
            likely {fmtShort(pattern.nextExpectedLow)} to {fmtShort(pattern.nextExpectedHigh)}
          </span>
        )}
        {pattern.daysUntilNext !== 0 && (
          <span className={cn("ml-1 block", pattern.daysUntilNext < 0 ? "text-caution" : "text-muted-foreground/70")}>
            ({pattern.daysUntilNext < 0
              ? `${Math.abs(pattern.daysUntilNext)}d ago`
              : `in ${pattern.daysUntilNext}d`})
          </span>
        )}
      </td>
      <td className="px-4 py-3">
        <StatusMarker label={status.label} className={status.cls} />
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-1">
          {pattern.isUserConfirmed ? (
            <button
              onClick={() => onUnconfirm(pattern.id)}
              disabled={busy}
              className="rounded p-1.5 text-positive hover:text-positive hover:bg-positive-soft transition-colors disabled:opacity-50"
              title="Remove confirmation" aria-label={`Remove confirmation for ${pattern.merchant}`}
            >
              <CheckCircle2 className="h-4 w-4" />
            </button>
          ) : (
            <button
              onClick={() => onConfirm(pattern.id)}
              disabled={busy}
              className="rounded p-1.5 text-muted-foreground/70 hover:text-positive hover:bg-positive-soft transition-colors disabled:opacity-50"
              title="Confirm as recurring" aria-label={`Confirm ${pattern.merchant} as recurring`}
            >
              <CheckCircle2 className="h-4 w-4" />
            </button>
          )}
          <button
            onClick={() => onDismiss(pattern.id)}
            disabled={busy}
            className="rounded p-1.5 text-muted-foreground/50 hover:text-warning hover:bg-warning-soft transition-colors disabled:opacity-50"
            title="Not recurring — dismiss" aria-label={`Dismiss ${pattern.merchant}, not recurring`}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </td>
    </tr>
  );
}

function EmptyState({ onScan, scanning }: { onScan: () => void; scanning: boolean }) {
  return (
    <div className="rounded-lg border border-border bg-card px-6 py-16 text-center">
      <Repeat className="mx-auto h-10 w-10 text-muted-foreground/30 mb-3" />
      <p className="text-sm font-medium text-foreground">No subscriptions found yet</p>
      <p className="mt-1 text-xs text-muted-foreground/70 max-w-xs mx-auto">
        Once we see the same charge a couple of times, it&apos;ll show up here automatically.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Button size="sm" onClick={onScan} disabled={scanning}>
          <RefreshCw className={cn("h-4 w-4", scanning && "animate-spin")} />
          {scanning ? "Scanning" : "Scan now"}
        </Button>
        <Button size="sm" variant="outline" asChild>
          <Link href="/dashboard/transactions">Add transactions</Link>
        </Button>
      </div>
    </div>
  );
}
