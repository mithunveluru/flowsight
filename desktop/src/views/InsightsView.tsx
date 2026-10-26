import { Sparkles, TrendingDown, Lightbulb, ShieldAlert } from "lucide-react";
import { CountUp, FadeIn } from "@/components/motion/primitives";
import { Button } from "@/components/ui/button";
import { EmptyState, Skeleton } from "@/components/ui/states";
import {
  SignalCard,
  SignalSectionHeader,
  InsightCallout,
  EvidenceChips,
  type Severity,
} from "@/components/ui/signals";
import type { InsightsResponse } from "@/features/insights/types";
import type { LeakDetectionResponse } from "@/features/leaks/types";
import { insightsApi } from "~/api/insights";
import { leaksApi } from "~/api/leaks";
import { useAsync } from "~/lib/useAsync";
import { useDensity } from "~/lib/density";
import { cn } from "~/lib/utils";
import { formatINR } from "~/lib/format";
import { openWorkspace } from "~/lib/tauri";

interface InsightsData {
  insights: InsightsResponse | null;
  leaks: LeakDetectionResponse | null;
}

async function settle<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch {
    return null;
  }
}

async function load(): Promise<InsightsData> {
  const [insights, leaks] = await Promise.all([settle(insightsApi.get()), settle(leaksApi.detect())]);
  return { insights, leaks };
}

export function InsightsView() {
  const density = useDensity();
  const { data, loading } = useAsync(load, []);
  const listCls = density === "large" ? "grid grid-cols-2 gap-3" : "space-y-3";

  if (loading && !data) {
    return (
      <div role="status" aria-busy="true" className="space-y-4 px-4 py-4">
        <span className="sr-only">Loading insights</span>
        <Skeleton className="h-24 w-full rounded-md" />
        <Skeleton className="h-28 w-full rounded-md" />
        <Skeleton className="h-28 w-full rounded-md" />
      </div>
    );
  }
  if (!data) return null;

  const patterns = data.insights?.profile.patterns ?? [];
  const recommendations = data.insights?.recommendations ?? [];
  const leaks = data.leaks?.leaks ?? [];
  const monthlySaving = data.insights?.totalPotentialMonthlySaving ?? 0;
  const annualSaving = data.insights?.totalPotentialAnnualSaving ?? 0;

  const nothing = patterns.length === 0 && recommendations.length === 0 && leaks.length === 0;
  if (nothing) {
    return (
      <EmptyState
        icon={<Sparkles className="h-7 w-7" />}
        title="Nothing to show yet"
        description="Add a few more transactions and your spending patterns, money drains, and tips will show up here."
        className="m-4"
        action={
          <Button variant="secondary" className="no-drag" onClick={() => void openWorkspace()}>
            Open workspace
          </Button>
        }
      />
    );
  }

  return (
    <div>
      {(monthlySaving > 0 || annualSaving > 0) && (
        <FadeIn>
          <section className="bg-band-blue px-4 py-5 md:px-5">
            <p className="text-xs font-medium text-ink-meta">Potential savings</p>
            <div className="mt-1 flex items-baseline gap-2">
              <CountUp value={monthlySaving} format={formatINR} className="text-ty-lead font-medium tabular-nums text-ink" />
              <span className="text-xs text-ink-meta">/ month</span>
            </div>
            <p className="mt-1 text-xs text-ink-meta">{formatINR(annualSaving)} per year if acted on</p>
          </section>
        </FadeIn>
      )}
      <div className={cn("space-y-6 px-4 py-5", density === "large" && "mx-auto w-full max-w-3xl px-5")}>
        {leaks.length > 0 && (
          <section>
            <SignalSectionHeader
              icon={<ShieldAlert className="h-4 w-4" />}
              title="Money drains"
              subtitle={`${leaks.length} found`}
            />
            <div className={listCls}>
              {leaks.map((leak) => (
                <SignalCard
                  key={leak.type}
                  severity={leak.severity as Severity}
                  title={leak.title}
                  metric={
                    <span className="text-sm font-medium tabular-nums text-ink">
                      {formatINR(leak.monthlyImpact)}/mo
                    </span>
                  }
                >
                  <p className="text-xs text-ink-body">{leak.description}</p>
                  {leak.recommendation && (
                    <InsightCallout tone="signal" className="mt-2">
                      {leak.recommendation}
                    </InsightCallout>
                  )}
                </SignalCard>
              ))}
            </div>
          </section>
        )}

        {patterns.length > 0 && (
          <section>
            <SignalSectionHeader
              icon={<TrendingDown className="h-4 w-4" />}
              title="Patterns we noticed"
              subtitle={data.insights?.profile.summary}
            />
            <div className={listCls}>
              {patterns.map((p) => (
                <SignalCard key={p.code} severity={p.severity} title={p.title}>
                  <p className="text-xs text-ink-body">{p.description}</p>
                  {p.context && <EvidenceChips className="mt-1.5" items={[p.context]} />}
                </SignalCard>
              ))}
            </div>
          </section>
        )}

        {recommendations.length > 0 && (
          <section>
            <SignalSectionHeader icon={<Lightbulb className="h-4 w-4" />} title="Recommendations" />
            <div className={listCls}>
              {recommendations.map((r, i) => (
                <div key={`${r.type}-${i}`} className="rounded-md bg-band p-4">
                  <div className="flex items-start justify-between gap-3">
                    <p className="text-sm font-medium text-ink">{r.title}</p>
                    {r.potentialMonthlySaving != null && r.potentialMonthlySaving > 0 && (
                      <span className="shrink-0 text-sm font-medium tabular-nums text-ink">
                        Save {formatINR(r.potentialMonthlySaving)}/mo
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-ink-body">{r.description}</p>
                  <InsightCallout tone="signal" className="mt-2">
                    {r.suggestedAction}
                  </InsightCallout>
                  {r.evidence.length > 0 && <EvidenceChips className="mt-2" items={r.evidence} />}
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
