import { Sparkles, TrendingDown, Lightbulb, ShieldAlert } from "lucide-react";
import { FadeIn } from "@/components/motion/primitives";
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
import { Skeleton, EmptyState, Button } from "~/components/ui";
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
  const listCls = density === "large" ? "grid grid-cols-2 gap-2.5" : "space-y-2.5";

  if (loading && !data) {
    return (
      <div className="space-y-4 px-4 py-4">
        <Skeleton className="h-24 w-full rounded-xl" />
        <Skeleton className="h-28 w-full rounded-xl" />
        <Skeleton className="h-28 w-full rounded-xl" />
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
        hint="Add a few more transactions and your spending patterns, money drains, and tips will show up here."
        action={
          <Button variant="secondary" onClick={() => void openWorkspace()}>
            Open workspace
          </Button>
        }
      />
    );
  }

  return (
    <div className={cn("space-y-5 px-4 py-4", density === "large" && "mx-auto w-full max-w-3xl px-5")}>
      {/* Savings potential hero */}
      {(monthlySaving > 0 || annualSaving > 0) && (
        <FadeIn>
          <div className="card-refined surface-gradient-emerald p-4">
            <p className="text-overline">Potential savings</p>
            <div className="mt-1 flex items-baseline gap-2">
              <span
                className="text-2xl font-semibold tabular-nums"
                style={{ color: "hsl(var(--positive))" }}
              >
                {formatINR(monthlySaving)}
              </span>
              <span className="text-xs text-muted-foreground">/ month</span>
            </div>
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              {formatINR(annualSaving)} per year if acted on
            </p>
          </div>
        </FadeIn>
      )}

      {/* Leak alerts */}
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
                  <span className="text-sm font-semibold tabular-nums text-foreground">
                    {formatINR(leak.monthlyImpact)}/mo
                  </span>
                }
              >
                <p className="text-xs leading-relaxed text-muted-foreground">{leak.description}</p>
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

      {/* Behavioral patterns */}
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
                <p className="text-xs leading-relaxed text-muted-foreground">{p.description}</p>
                {p.context && <EvidenceChips className="mt-1.5" items={[p.context]} />}
              </SignalCard>
            ))}
          </div>
        </section>
      )}

      {/* Recommendations */}
      {recommendations.length > 0 && (
        <section>
          <SignalSectionHeader icon={<Lightbulb className="h-4 w-4" />} title="Recommendations" />
          <div className={listCls}>
            {recommendations.map((r, i) => (
              <div key={`${r.type}-${i}`} className="card-refined p-4">
                <div className="flex items-start justify-between gap-3">
                  <p className="text-sm font-semibold text-foreground">{r.title}</p>
                  {r.potentialMonthlySaving != null && r.potentialMonthlySaving > 0 && (
                    <span
                      className="shrink-0 text-sm font-semibold tabular-nums"
                      style={{ color: "hsl(var(--positive))" }}
                    >
                      {formatINR(r.potentialMonthlySaving)}/mo
                    </span>
                  )}
                </div>
                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{r.description}</p>
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
  );
}
