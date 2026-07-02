"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertOctagon,
  ChevronDown,
  ChevronRight,
  CreditCard,
  Droplets,
  Layers,
  Sparkles,
  RefreshCw,
  TrendingUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  InsightCallout,
  StatusMarker,
  type Severity,
} from "@/components/ui/signals";
import {
  AmbientGlow,
  AnimatedNumber,
  FadeIn,
  StaggerContainer,
  StaggerItem,
} from "@/components/motion/primitives";
import { leaksApi } from "@/features/leaks/api";
import type {
  LeakDetectionResponse,
  LeakInsight,
  LeakType,
} from "@/features/leaks/types";
import { cn } from "@/lib/utils";

const LEAK_TYPE_ICON: Record<LeakType, React.ElementType> = {
  DUPLICATE_SUBSCRIPTIONS:    Layers,
  SUBSCRIPTION_CREEP:         TrendingUp,
  HIGH_FREQUENCY_SMALL_SPEND: Droplets,
  BANK_FEES:                  CreditCard,
};

const SEVERITY_TOKEN: Record<Severity, string> = {
  HIGH:   "var(--severity-high)",
  MEDIUM: "var(--severity-medium)",
  LOW:    "var(--severity-low)",
};

const SEVERITY_LABEL: Record<Severity, string> = {
  HIGH:   "High impact",
  MEDIUM: "Medium impact",
  LOW:    "Low impact",
};

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

export default function LeaksPage() {
  const [data, setData]         = useState<LeakDetectionResponse | null>(null);
  const [loading, setLoading]   = useState(true);
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
    <div className="space-y-8">
      <FadeIn>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Money drains
            </h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              Forgotten subscriptions, creeping prices, small daily habits, and fees quietly adding up, with what you&apos;d save by fixing them.
            </p>
          </div>
          <Button size="sm" variant="outline" onClick={rescan} disabled={scanning || loading}>
            <RefreshCw className={cn("h-4 w-4", scanning && "animate-spin")} />
            {scanning ? "Scanning" : "Scan again"}
          </Button>
        </div>
      </FadeIn>

      {loading ? (
        <LoadingSkeleton />
      ) : !data || data.totalLeaksFound === 0 ? (
        <EmptyState />
      ) : (
        <>
          <FadeIn delay={0.08}>
            <SavingsHero data={data} />
          </FadeIn>

          <section>
            <div className="section-header items-center">
              <p className="text-sm font-semibold text-foreground">
                What we found
                <span className="ml-1.5 font-normal text-muted-foreground">({data.totalLeaksFound})</span>
              </p>
            </div>
            <StaggerContainer className="space-y-3" stagger={0.05}>
              {data.leaks.map((leak) => (
                <StaggerItem key={leak.type}>
                  <LeakCard leak={leak} />
                </StaggerItem>
              ))}
            </StaggerContainer>
          </section>
        </>
      )}
    </div>
  );
}

function SavingsHero({ data }: { data: LeakDetectionResponse }) {
  return (
    <section className="surface-gradient-emerald relative overflow-hidden rounded-xl card-refined p-6 sm:p-8">
      <AmbientGlow />
      <div className="relative flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-emerald-200 bg-card">
          <Sparkles className="h-5 w-5 text-emerald-600" strokeWidth={1.75} />
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-emerald-700">
            Potential monthly savings
          </p>
          <p className="mt-1.5 stat-value text-4xl">
            <AnimatedNumber value={data.totalMonthlyImpact} format={(v) => `₹${v.toLocaleString("en-IN")}`} />
          </p>
          <p className="mt-2 text-sm text-muted-foreground">
            That&apos;s{" "}
            <span className="font-medium text-foreground tabular-nums">
              <AnimatedNumber value={data.totalAnnualImpact} format={(v) => `₹${v.toLocaleString("en-IN")}`} />
            </span>{" "}
            a year if all recovered.
          </p>
        </div>
      </div>
    </section>
  );
}

function LeakCard({ leak }: { leak: LeakInsight }) {
  const [open, setOpen] = useState(false);
  const severity = leak.severity as Severity;
  const Icon = LEAK_TYPE_ICON[leak.type];
  const sevColor = SEVERITY_TOKEN[severity];

  return (
    <div className="relative overflow-hidden card-refined">
      {/* Severity rail — the intelligence-layer identity marker */}
      <span
        aria-hidden="true"
        className="absolute inset-y-0 left-0 w-[3px]"
        style={{ background: `hsl(${sevColor})`, opacity: 0.9 }}
      />
      <button
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-start gap-3.5 p-5 text-left transition-colors hover:bg-muted/40"
      >
        <span
          className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
          style={{ backgroundColor: `hsl(${sevColor} / 0.12)`, color: `hsl(${sevColor})` }}
        >
          <Icon className="h-4 w-4" strokeWidth={1.75} />
        </span>

        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-foreground">{leak.title}</p>
            <StatusMarker label={SEVERITY_LABEL[severity]} color={`hsl(${sevColor})`} />
          </div>
          <p className="mt-1 text-xs text-muted-foreground leading-relaxed">{leak.description}</p>
        </div>

        <div className="flex shrink-0 flex-col items-end gap-0.5">
          <p className="text-base font-semibold tabular-nums text-foreground">
            {formatINR(leak.monthlyImpact)}
            <span className="text-xs font-normal text-muted-foreground">/mo</span>
          </p>
          <p className="text-xs text-muted-foreground">{formatINR(leak.annualImpact)}/yr</p>
        </div>

        {open
          ? <ChevronDown  className="ml-1 mt-1 h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} />
          : <ChevronRight className="ml-1 mt-1 h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} />}
      </button>

      {open && (
        <div className="border-t px-5 pb-5 pt-4 animate-fade-in" style={{ borderColor: "hsl(var(--border))" }}>
          {leak.recommendation && (
            <InsightCallout tone="signal">{leak.recommendation}</InsightCallout>
          )}

          <div className="mt-4 rounded-lg border bg-card" style={{ borderColor: "hsl(var(--border))" }}>
            <div className="border-b px-4 py-2.5" style={{ borderColor: "hsl(var(--border))" }}>
              <p className="text-xs font-medium text-muted-foreground">
                Affected items ({leak.affectedItemsCount})
              </p>
            </div>
            <div className="divide-y" style={{ borderColor: "hsl(var(--border))" }}>
              {leak.items.map((item, i) => (
                <div key={i} className="flex items-center justify-between px-4 py-2.5">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">{item.merchant}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground truncate">{item.detail}</p>
                  </div>
                  <p className="ml-3 shrink-0 text-sm font-medium tabular-nums text-foreground">
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

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      <div className="h-28 rounded-xl skeleton" />
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-20 rounded-xl skeleton" />
      ))}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="card-refined px-6 py-16 text-center">
      <AlertOctagon className="mx-auto h-10 w-10 text-muted-foreground/40 mb-3" strokeWidth={1.5} />
      <p className="text-sm font-medium text-foreground">Nothing to recover</p>
      <p className="mt-1 text-xs text-muted-foreground max-w-xs mx-auto">
        No duplicate subscriptions, price hikes, or fee patterns showed up in your last three months.
      </p>
      <div className="mt-5">
        <Button size="sm" variant="outline" asChild>
          <Link href="/dashboard/transactions">View transactions</Link>
        </Button>
      </div>
    </div>
  );
}
