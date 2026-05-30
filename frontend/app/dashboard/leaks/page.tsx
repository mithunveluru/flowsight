"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertOctagon,
  ArrowDown,
  ChevronDown,
  ChevronRight,
  CreditCard,
  Droplets,
  Layers,
  PiggyBank,
  RefreshCw,
  TrendingUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { leaksApi } from "@/features/leaks/api";
import type {
  LeakDetectionResponse,
  LeakInsight,
  LeakSeverity,
  LeakType,
} from "@/features/leaks/types";
import { cn } from "@/lib/utils";

// -------------------------------------------------------------------------
// Style maps
// -------------------------------------------------------------------------

const SEVERITY_STYLES: Record<LeakSeverity, { ringCls: string; badgeCls: string; label: string }> = {
  HIGH:   { ringCls: "border-red-200    bg-red-50",    badgeCls: "border-red-200    bg-red-100    text-red-700",    label: "High impact"   },
  MEDIUM: { ringCls: "border-amber-200  bg-amber-50",  badgeCls: "border-amber-200  bg-amber-100  text-amber-700",  label: "Medium impact" },
  LOW:    { ringCls: "border-slate-200  bg-slate-50",  badgeCls: "border-slate-200  bg-slate-100  text-slate-600",  label: "Low impact"    },
};

const LEAK_TYPE_META: Record<LeakType, { icon: React.ElementType; iconCls: string }> = {
  DUPLICATE_SUBSCRIPTIONS:    { icon: Layers,       iconCls: "text-violet-500" },
  SUBSCRIPTION_CREEP:         { icon: TrendingUp,   iconCls: "text-orange-500" },
  HIGH_FREQUENCY_SMALL_SPEND: { icon: Droplets,     iconCls: "text-blue-500"   },
  BANK_FEES:                  { icon: CreditCard,   iconCls: "text-red-500"    },
};

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

// -------------------------------------------------------------------------
// Page
// -------------------------------------------------------------------------

export default function LeaksPage() {
  const [data, setData]       = useState<LeakDetectionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await leaksApi.detect());
    } catch {
      setData(null);
    } finally {
      setLoading(false);
      setScanning(false);
    }
  }, []);

  const rescan = async () => {
    setScanning(true);
    await load();
  };

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Leak detection</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            Recoverable spending found in your transaction history
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={rescan} disabled={scanning || loading}>
          <RefreshCw className={cn("h-4 w-4", scanning && "animate-spin")} />
          {scanning ? "Scanning…" : "Re-scan"}
        </Button>
      </div>

      {loading ? (
        <LoadingSkeleton />
      ) : !data || data.totalLeaksFound === 0 ? (
        <EmptyState />
      ) : (
        <>
          {/* Hero card — total savings opportunity */}
          <SavingsHero data={data} />

          {/* Leak insights */}
          <div className="space-y-4">
            <p className="text-sm font-semibold text-slate-900">
              Leaks found ({data.totalLeaksFound})
            </p>
            {data.leaks.map((leak) => (
              <LeakCard key={leak.type} leak={leak} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// -------------------------------------------------------------------------
// Hero: total potential savings
// -------------------------------------------------------------------------

function SavingsHero({ data }: { data: LeakDetectionResponse }) {
  return (
    <div className="rounded-xl border border-emerald-200 bg-gradient-to-br from-emerald-50 via-white to-white p-6">
      <div className="flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-emerald-200 bg-white">
          <PiggyBank className="h-5 w-5 text-emerald-600" />
        </div>
        <div className="flex-1">
          <p className="text-xs font-medium uppercase tracking-wide text-emerald-700">
            Potential monthly savings
          </p>
          <p className="mt-1 text-3xl font-semibold tabular-nums text-slate-900">
            {formatINR(data.totalMonthlyImpact)}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            That&apos;s {formatINR(data.totalAnnualImpact)} a year if all
            recovered.
          </p>
        </div>
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------
// Leak card — collapsible
// -------------------------------------------------------------------------

function LeakCard({ leak }: { leak: LeakInsight }) {
  const [open, setOpen] = useState(false);
  const sevStyles = SEVERITY_STYLES[leak.severity];
  const typeMeta  = LEAK_TYPE_META[leak.type];

  return (
    <div className={cn("rounded-lg border", sevStyles.ringCls)}>
      {/* Header — clickable */}
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-start gap-4 p-5 text-left"
      >
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-white/60 bg-white">
          <typeMeta.icon className={cn("h-4 w-4", typeMeta.iconCls)} />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-slate-900">{leak.title}</p>
            <span className={cn("inline-flex items-center rounded-md border px-1.5 py-0.5 text-xs font-medium", sevStyles.badgeCls)}>
              {sevStyles.label}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-600">{leak.description}</p>
        </div>

        <div className="flex shrink-0 flex-col items-end gap-1">
          <p className="text-base font-semibold tabular-nums text-slate-900">
            {formatINR(leak.monthlyImpact)}
            <span className="text-xs font-normal text-slate-400">/mo</span>
          </p>
          <p className="text-xs text-slate-400">{formatINR(leak.annualImpact)}/yr</p>
        </div>

        {open
          ? <ChevronDown  className="ml-2 h-4 w-4 shrink-0 text-slate-400" />
          : <ChevronRight className="ml-2 h-4 w-4 shrink-0 text-slate-400" />}
      </button>

      {/* Expanded detail */}
      {open && (
        <div className="border-t border-white/60 px-5 pb-5">
          {leak.recommendation && (
            <div className="mt-4 flex items-start gap-2 rounded-md bg-white/60 p-3 text-xs text-slate-600">
              <ArrowDown className="mt-0.5 h-3 w-3 shrink-0 rotate-[-45deg] text-emerald-500" />
              <p>{leak.recommendation}</p>
            </div>
          )}

          <div className="mt-4 rounded-lg border border-white/60 bg-white">
            <div className="border-b border-slate-100 px-4 py-2.5">
              <p className="text-xs font-medium text-slate-500">
                Affected items ({leak.affectedItemsCount})
              </p>
            </div>
            <div className="divide-y divide-slate-100">
              {leak.items.map((item, i) => (
                <div key={i} className="flex items-center justify-between px-4 py-2.5">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900 truncate">{item.merchant}</p>
                    <p className="mt-0.5 text-xs text-slate-500 truncate">{item.detail}</p>
                  </div>
                  <p className="ml-3 shrink-0 text-sm font-medium tabular-nums text-slate-900">
                    {formatINR(item.amount)}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------------------------
// Skeleton & empty states
// -------------------------------------------------------------------------

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      <div className="h-24 rounded-xl border border-slate-200 bg-white animate-pulse" />
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-20 rounded-lg border border-slate-200 bg-white animate-pulse" />
      ))}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-6 py-16 text-center">
      <AlertOctagon className="mx-auto h-10 w-10 text-slate-200 mb-3" />
      <p className="text-sm font-medium text-slate-900">No leaks detected</p>
      <p className="mt-1 text-xs text-slate-400 max-w-xs mx-auto">
        Your spending looks clean — no duplicate subscriptions, price hikes, or fee
        patterns found in the last 3 months.
      </p>
      <div className="mt-5">
        <Button size="sm" variant="outline" asChild>
          <Link href="/dashboard/transactions">View transactions</Link>
        </Button>
      </div>
    </div>
  );
}
