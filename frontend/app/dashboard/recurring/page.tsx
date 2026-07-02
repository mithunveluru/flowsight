"use client";

import { useCallback, useEffect, useState } from "react";
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
import { recurringApi } from "@/features/recurring/api";
import type {
  ConfidenceTier,
  RecurringPattern,
  RecurringStatus,
} from "@/features/recurring/types";
import { Button } from "@/components/ui/button";
import { StatusMarker } from "@/components/ui/signals";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const STATUS_STYLES: Record<RecurringStatus, { label: string; cls: string }> = {
  ACTIVE:   { label: "Active",    cls: "text-emerald-600" },
  DUE_SOON: { label: "Due soon",  cls: "text-blue-600"    },
  OVERDUE:  { label: "Overdue",   cls: "text-amber-600"   },
  MISSED:   { label: "Missed",    cls: "text-red-600"     },
};

const TIER_STYLES: Record<ConfidenceTier, { label: string; cls: string }> = {
  HIGH:     { label: "High",     cls: "text-emerald-600" },
  MEDIUM:   { label: "Medium",   cls: "text-blue-600"    },
  POSSIBLE: { label: "Possible", cls: "text-slate-500"   },
};

export default function RecurringPage() {
  const [patterns, setPatterns] = useState<RecurringPattern[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [scanning, setScanning] = useState(false);
  const [error,    setError]    = useState<string | null>(null);
  const [busyId,   setBusyId]   = useState<string | null>(null);

  const load = useCallback(async (refresh = false) => {
    try {
      setPatterns(await recurringApi.list(refresh));
    } catch {
      setError("Failed to load recurring patterns.");
    } finally {
      setLoading(false);
      setScanning(false);
    }
  }, []);

  useEffect(() => { load(true); }, [load]);

  const scan = async () => {
    setScanning(true);
    setError(null);
    await load(true);
  };

  // Pattern actions — optimistic UI
  const handleDismiss = async (id: string) => {
    setBusyId(id);
    try {
      await recurringApi.dismiss(id);
      setPatterns((prev) => prev.filter((p) => p.id !== id));
    } finally { setBusyId(null); }
  };

  const handleConfirm = async (id: string) => {
    setBusyId(id);
    try {
      const updated = await recurringApi.confirm(id);
      setPatterns((prev) => prev.map((p) => (p.id === id ? updated : p)));
    } finally { setBusyId(null); }
  };

  const handleUnconfirm = async (id: string) => {
    setBusyId(id);
    try {
      const updated = await recurringApi.unconfirm(id);
      setPatterns((prev) => prev.map((p) => (p.id === id ? updated : p)));
    } finally { setBusyId(null); }
  };

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
          <h1 className="text-xl font-semibold text-slate-900">Subscriptions &amp; bills</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            The recurring charges we spotted in your history. Confirm the real ones and dismiss the rest so we get it right.
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={scan} disabled={scanning || loading}>
          <RefreshCw className={cn("h-4 w-4", scanning && "animate-spin")} />
          {scanning ? "Scanning" : "Scan again"}
        </Button>
      </div>

      {error && (
        <p className="text-sm text-red-600 flex items-center gap-1.5">
          <XCircle className="h-4 w-4 shrink-0" /> {error}
        </p>
      )}

      {loading ? (
        <LoadingSkeleton />
      ) : patterns.length === 0 ? (
        <EmptyState onScan={scan} scanning={scanning} />
      ) : (
        <>
          {/* Summary cards */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Per month"                value={formatINR(totalMonthly)} icon={Repeat}        cls="text-orange-500"  bg="bg-orange-50 border-orange-100" />
            <StatCard label="Per year"                 value={formatINR(totalAnnual)}  icon={CalendarClock} cls="text-blue-500"    bg="bg-blue-50 border-blue-100"     />
            <StatCard label="Active subscriptions"     value={String(patterns.length)} icon={CheckCircle2}  cls="text-emerald-500" bg="bg-emerald-50 border-emerald-100" />
            <StatCard label="Worth cancelling"         value={String(cancellable)}     icon={TrendingDown}  cls="text-violet-500"  bg="bg-violet-50 border-violet-100"   />
          </div>

          {/* Confirmed section */}
          {confirmed.length > 0 && (
            <Section
              title="Confirmed by you"
              count={confirmed.length}
              icon={<CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />}
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
              icon={<Sparkles className="h-3.5 w-3.5 text-blue-600" />}
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
              icon={<HelpCircle className="h-3.5 w-3.5 text-slate-500" />}
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
        <p className="text-sm font-semibold text-slate-900">{title}</p>
        <span className="text-xs text-slate-400">({count})</span>
      </div>
      <p className="text-xs text-slate-500">{hint}</p>
      <div className="rounded-lg border border-slate-200 bg-white overflow-x-auto">
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
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex items-center gap-2 mb-2">
        <div className={cn("flex h-7 w-7 items-center justify-center rounded-md border", bg)}>
          <Icon className={cn("h-3.5 w-3.5", cls)} />
        </div>
        <span className="text-xs font-medium text-slate-500">{label}</span>
      </div>
      <p className="text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
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
        <tr className="border-b border-slate-200 bg-slate-50">
          {["Merchant","Period","Confidence","Amount","Next expected","Status",""].map((h) => (
            <th key={h} className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide whitespace-nowrap">
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
    <tr className="border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors">
      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5">
          <span className="font-medium text-slate-900">{pattern.merchant}</span>
          {pattern.isCancellationCandidate && (
            <span className="inline-flex items-center gap-1 text-xs font-medium text-violet-600">
              <TrendingDown className="h-3 w-3" strokeWidth={2} /> Optional
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs text-slate-400">
          Seen {pattern.occurrenceCount} times so far
        </p>
      </td>
      <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
        {pattern.periodLabel}
      </td>
      <td className="px-4 py-3">
        <StatusMarker label={tier.label} className={tier.cls} />
        <p className="mt-0.5 text-xs text-slate-400">
          {(Number(pattern.confidence) * 100).toFixed(0)}%
        </p>
      </td>
      <td className="px-4 py-3 tabular-nums">
        <span className="font-medium text-slate-900">{formatINR(pattern.estimatedAmount)}</span>
        <p className="mt-0.5 text-xs text-slate-400">
          {formatINR(pattern.monthlyEquivalent)}/mo equivalent
        </p>
      </td>
      <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
        {pattern.nextExpectedDate
          ? new Date(pattern.nextExpectedDate).toLocaleDateString("en-IN", {
              day: "numeric", month: "short", year: "numeric",
            })
          : "—"}
        {pattern.daysUntilNext !== 0 && (
          <span className={cn("ml-1 block", pattern.daysUntilNext < 0 ? "text-amber-600" : "text-slate-400")}>
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
              className="rounded p-1.5 text-emerald-500 hover:text-emerald-600 hover:bg-emerald-50 transition-colors disabled:opacity-50"
              title="Remove confirmation"
            >
              <CheckCircle2 className="h-4 w-4" />
            </button>
          ) : (
            <button
              onClick={() => onConfirm(pattern.id)}
              disabled={busy}
              className="rounded p-1.5 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 transition-colors disabled:opacity-50"
              title="Confirm as recurring"
            >
              <CheckCircle2 className="h-4 w-4" />
            </button>
          )}
          <button
            onClick={() => onDismiss(pattern.id)}
            disabled={busy}
            className="rounded p-1.5 text-slate-300 hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
            title="Not recurring — dismiss"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </td>
    </tr>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div key={i} className="skeleton h-16 rounded-lg" />
      ))}
    </div>
  );
}

function EmptyState({ onScan, scanning }: { onScan: () => void; scanning: boolean }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-6 py-16 text-center">
      <Repeat className="mx-auto h-10 w-10 text-slate-200 mb-3" />
      <p className="text-sm font-medium text-slate-900">No subscriptions found yet</p>
      <p className="mt-1 text-xs text-slate-400 max-w-xs mx-auto">
        Once we see the same charge a couple of times, it'll show up here automatically.
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
