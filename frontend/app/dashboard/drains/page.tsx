"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronDown, CreditCard, Droplets, Layers, RefreshCw, TrendingUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { leakEvidence } from "@/features/insights/evidence";
import type { Severity } from "@/components/ui/signals";
import { Collapse, CountUp, Reveal, Stagger, StaggerItem } from "@/components/motion/primitives";
import { PageTitle, Stat, Surface } from "@/components/insight/page-kit";
import { useLeaks } from "@/features/leaks/hooks";
import type { LeakDetectionResponse, LeakInsight, LeakType } from "@/features/leaks/types";
import { cn } from "@/lib/utils";

const LEAK_TYPE_ICON: Record<LeakType, React.ElementType> = {
  DUPLICATE_SUBSCRIPTIONS: Layers,
  SUBSCRIPTION_CREEP: TrendingUp,
  HIGH_FREQUENCY_SMALL_SPEND: Droplets,
  BANK_FEES: CreditCard,
};

const IMPACT: Record<Severity, { label: string; variant: "red" | "amber" | "blue" }> = {
  HIGH: { label: "High impact", variant: "red" },
  MEDIUM: { label: "Medium impact", variant: "amber" },
  LOW: { label: "Low impact", variant: "blue" },
};

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

export default function LeaksPage() {
  const query = useLeaks();
  const data = query.data;
  const loading = query.isPending;
  const scanning = query.isFetching && !loading;

  return (
    <div className="space-y-sp-hero">
      <PageTitle
        title="Money drains"
        lead="Forgotten subscriptions, creeping prices, small daily habits, and fees quietly adding up, with what you would save by fixing them."
        actions={
          <Button variant="outline" className="coarse:h-11" onClick={() => query.refetch()} disabled={scanning || loading}>
            <RefreshCw className={cn(scanning && "animate-spin")} aria-hidden="true" />
            {scanning ? "Scanning" : "Scan again"}
          </Button>
        }
      />

      {loading ? (
        <LoadingState variant="card" rows={3} label="Looking for money drains" />
      ) : query.isError && !data ? (
        <ErrorState title="Money drains did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : !data || data.totalLeaksFound === 0 ? (
        <EmptyState
          icon={<Droplets />}
          title="Nothing to recover"
          description="No duplicate subscriptions, price hikes, or fee patterns showed up in your last three months."
          action={
            <Button variant="outline" asChild>
              <Link href="/dashboard/transactions">View transactions</Link>
            </Button>
          }
        />
      ) : (
        <>
          <SavingsFigure data={data} />

          <Reveal>
            <section>
              <h2 className="mb-sp-giant text-ty-subhead text-ink">
                What we found
                <span className="ml-sp-sm text-ty-link font-normal text-ink-meta">{data.totalLeaksFound}</span>
              </h2>
              <Stagger className="space-y-sp-wide" stagger={0.05}>
                {data.leaks.map((leak, i) => (
                  <StaggerItem key={`${leak.type}-${i}`}>
                    <LeakCard leak={leak} />
                  </StaggerItem>
                ))}
              </Stagger>
            </section>
          </Reveal>
        </>
      )}
    </div>
  );
}

function SavingsFigure({ data }: { data: LeakDetectionResponse }) {
  const rupee = (v: number) => `₹${v.toLocaleString("en-IN")}`;
  return (
    <Surface tone="blue" className="grid gap-sp-giant p-sp-max sm:grid-cols-[1fr_auto] sm:items-end">
      <Stat label="Potential monthly savings" large>
        <CountUp value={data.totalMonthlyImpact} format={rupee} />
      </Stat>
      <p className="max-w-sm text-sm text-ink-body">
        That is <span className="font-medium tabular-nums text-ink"><CountUp value={data.totalAnnualImpact} format={rupee} /></span> a year if all recovered.
        <EvidenceDrawer
          title="Potential monthly savings"
          value={`${formatINR(data.totalMonthlyImpact)}/mo`}
          evidence={{
            method: "The sum of the monthly impact of each drain found below.",
            inputs: data.leaks.map((l) => ({ label: l.title, value: `${formatINR(l.monthlyImpact)}/mo` })),
          }}
          trigger={<EvidenceTrigger label="the potential monthly savings" className="ml-sp-xs align-middle coarse:h-11 coarse:min-w-11" />}
        />
      </p>
    </Surface>
  );
}

function LeakCard({ leak }: { leak: LeakInsight }) {
  const [open, setOpen] = useState(false);
  const Icon = LEAK_TYPE_ICON[leak.type];
  const impact = IMPACT[leak.severity as Severity];
  const panelId = `leak-${leak.type}-${leak.title}`.replace(/\W+/g, "-");

  return (
    <Surface className="p-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-start gap-sp-wide rounded-md p-sp-giant text-left"
      >
        <Icon className="mt-0.5 h-5 w-5 shrink-0 text-ink-meta" strokeWidth={1.75} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-sp-sm">
            <h3 className="text-ty-link font-medium text-ink">{leak.title}</h3>
            <Badge variant={impact.variant}>{impact.label}</Badge>
          </div>
          <p className="mt-sp-xs text-sm text-ink-body">{leak.description}</p>
        </div>
        <div className="shrink-0 text-right tabular-nums">
          <p className="text-ty-subhead text-ink">
            {formatINR(leak.monthlyImpact)}
            <span className="text-sm font-normal text-ink-meta">/mo</span>
          </p>
          <p className="text-xs text-ink-meta">{formatINR(leak.annualImpact)}/yr</p>
        </div>
        <ChevronDown
          className={cn("mt-1 h-4 w-4 shrink-0 text-ink-meta transition-transform duration-base", open && "rotate-180")}
          aria-hidden="true"
        />
      </button>

      <div className="flex flex-wrap items-center gap-sp-sm px-sp-giant pb-sp-wide pl-sp-giant sm:pl-[3.25rem]">
        <EvidenceDrawer
          title={leak.title}
          value={`${formatINR(leak.monthlyImpact)}/mo`}
          evidence={leakEvidence(leak)}
          trigger={
            <EvidenceTrigger
              label={`${leak.title} costs ${formatINR(leak.monthlyImpact)} a month`}
              className="-ml-sp-sm coarse:h-11 coarse:min-w-11"
            />
          }
        />
        {leak.evidence?.confidence != null && <ConfidenceChip value={leak.evidence.confidence} showValue />}
      </div>

      <Collapse open={open} id={panelId}>
        <div className="px-sp-giant pb-sp-giant sm:pl-[3.25rem]">
          {leak.recommendation && (
            <p className="rounded-md bg-band-blue p-sp-xl text-sm text-ink-body">{leak.recommendation}</p>
          )}
          <div className="mt-sp-wide rounded-md bg-canvas">
            <p className="px-sp-wide py-sp-xl text-sm font-medium text-ink">Affected items ({leak.affectedItemsCount})</p>
            <ul className="divide-y divide-border">
              {leak.items.map((item, i) => (
                <li key={i} className="flex items-center justify-between gap-sp-wide px-sp-wide py-sp-xl">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink">{item.merchant}</p>
                    <p className="truncate text-xs text-ink-meta">{item.detail}</p>
                  </div>
                  <p className="shrink-0 text-sm tabular-nums text-ink">{formatINR(item.amount)}</p>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Collapse>
    </Surface>
  );
}
