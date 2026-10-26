import { ArrowUpRight, Sparkles, Wallet, Plus, ScanLine } from "lucide-react";
import { FadeIn, StaggerContainer, StaggerItem } from "@/components/motion/primitives";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/states";
import {
  SignalCard,
  SignalDot,
  SignalSectionHeader,
  InsightCallout,
  type Severity,
} from "@/components/ui/signals";
import type { FlexibilityScore } from "@/features/simulation/types";
import type { AnalyticsOverview } from "@/features/analytics/types";
import type { Transaction } from "@/features/transactions/types";
import type { InsightsResponse } from "@/features/insights/types";
import type { LeakDetectionResponse } from "@/features/leaks/types";
import { flexibilityApi } from "~/api/flexibility";
import { analyticsApi } from "~/api/analytics";
import { transactionApi } from "~/api/transactions";
import { insightsApi } from "~/api/insights";
import { leaksApi } from "~/api/leaks";
import { useAsync } from "~/lib/useAsync";
import { useDensity } from "~/lib/density";
import { formatINR, formatShortDate, todayISO, weekStartISO } from "~/lib/format";
import { ScoreGauge } from "~/components/ScoreGauge";
import type { ViewKey } from "~/components/TitleBar";
import type { CaptureTab } from "~/views/CaptureView";

interface OverviewData {
  flexibility: FlexibilityScore | null;
  today: AnalyticsOverview | null;
  week: AnalyticsOverview | null;
  recent: Transaction[];
  insights: InsightsResponse | null;
  leaks: LeakDetectionResponse | null;
}

const SEV_RANK: Record<Severity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

async function settle<T>(p: Promise<T>): Promise<T | null> {
  try {
    return await p;
  } catch {
    return null;
  }
}

async function loadOverview(): Promise<OverviewData> {
  const today = todayISO();
  const [flexibility, todayO, weekO, recentPage, insights, leaks] = await Promise.all([
    settle(flexibilityApi.current()),
    settle(analyticsApi.overview(today, today)),
    settle(analyticsApi.overview(weekStartISO(), today)),
    settle(transactionApi.recent(6)),
    settle(insightsApi.get()),
    settle(leaksApi.detect()),
  ]);
  return {
    flexibility,
    today: todayO,
    week: weekO,
    recent: recentPage?.content ?? [],
    insights,
    leaks,
  };
}

interface OverviewSignal {
  id: string;
  severity: Severity;
  title: string;
  detail: string;
  metric?: string;
}

function buildSignals(d: OverviewData): OverviewSignal[] {
  const out: OverviewSignal[] = [];
  for (const leak of d.leaks?.leaks ?? []) {
    out.push({
      id: `leak-${leak.type}`,
      severity: leak.severity,
      title: leak.title,
      detail: leak.recommendation || leak.description,
      metric: `${formatINR(leak.monthlyImpact)}/mo`,
    });
  }
  for (const p of d.insights?.profile.patterns ?? []) {
    out.push({
      id: `pattern-${p.code}`,
      severity: p.severity,
      title: p.title,
      detail: p.description,
    });
  }
  return out.sort((a, b) => SEV_RANK[a.severity] - SEV_RANK[b.severity]);
}

interface OverviewProps {
  onNavigate: (v: ViewKey) => void;
  onCapture: (tab: CaptureTab) => void;
}

export function OverviewView({ onNavigate, onCapture }: OverviewProps) {
  const density = useDensity();
  const { data, loading } = useAsync(loadOverview, []);

  if (loading && !data) return <OverviewSkeleton />;
  if (!data) return null;

  const signals = buildSignals(data);

  if (density === "compact") return <CompactOverview data={data} signals={signals} onNavigate={onNavigate} onCapture={onCapture} />;
  if (density === "large") return <LargeOverview data={data} signals={signals} onNavigate={onNavigate} onCapture={onCapture} />;
  return <MediumOverview data={data} signals={signals} onNavigate={onNavigate} onCapture={onCapture} />;
}

// COMPACT: widget. Score band, today's spend, quick actions, top 2 signals. Nothing
// else: understand state in 3s, act in <5s.
function CompactOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  const top = signals.slice(0, 2);
  return (
    <div>
      <FadeIn>
        <section className="bg-band-blue px-4 py-4">
          <div className="flex items-center gap-4">
            {data.flexibility ? (
              <ScoreGauge score={data.flexibility.currentScore} tier={data.flexibility.currentTier} size={72} />
            ) : (
              <Skeleton className="h-[72px] w-[72px] rounded-full" />
            )}
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium text-ink">Breathing room</p>
              <p className="mt-1 line-clamp-2 text-xs text-ink-body">
                {data.flexibility?.explanation ?? "Add some income and spending to see this."}
              </p>
              <p className="mt-1.5 text-xs text-ink-meta">
                Today <span className="font-medium text-ink">{formatINR(data.today?.totalSpend ?? 0, { compact: true })}</span>
              </p>
            </div>
          </div>
        </section>
      </FadeIn>

      <div className="space-y-5 px-4 py-4">
        <QuickActions onCapture={onCapture} />

        <section>
          <SignalSectionHeader
            icon={<Sparkles className="h-4 w-4" />}
            title="Worth a look"
            className="mb-2"
            action={
              <Button variant="ghost" size="sm" className="text-primary" onClick={() => onNavigate("insights")}>
                All <ArrowUpRight />
              </Button>
            }
          />
          {top.length === 0 ? (
            <InsightCallout tone="signal">All steady. Nothing needs your attention right now.</InsightCallout>
          ) : (
            <div className="divide-y divide-border">
              {top.map((s) => (
                <div key={s.id} className="flex items-center gap-2.5 py-2.5">
                  <SignalDot severity={s.severity} />
                  <p className="min-w-0 flex-1 truncate text-xs font-medium text-ink">{s.title}</p>
                  {s.metric && <span className="shrink-0 text-xs font-medium tabular-nums text-ink">{s.metric}</span>}
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

// MEDIUM: companion. Score band with snapshot, quick actions, 3 signals, 3 recent.
function MediumOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  return (
    <div>
      <ScoreBand data={data} />
      <div className="space-y-6 px-4 py-5">
        <QuickActions onCapture={onCapture} />
        <SignalsSection signals={signals.slice(0, 3)} onNavigate={onNavigate} />
        <RecentSection recent={data.recent.slice(0, 3)} onCapture={onCapture} />
      </div>
    </div>
  );
}

// LARGE: desktop window. Score band across the top, then two columns.
function LargeOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  return (
    <div>
      <ScoreBand data={data} />
      <div className="grid grid-cols-2 gap-6 px-5 py-5">
        <div className="space-y-6">
          <QuickActions onCapture={onCapture} />
          <RecentSection recent={data.recent.slice(0, 6)} onCapture={onCapture} />
        </div>
        <SignalsSection signals={signals.slice(0, 5)} onNavigate={onNavigate} />
      </div>
    </div>
  );
}

function ScoreBand({ data }: { data: OverviewData }) {
  const { flexibility } = data;
  const topCategory = data.week?.categoryBreakdown?.[0] ?? null;
  return (
    <FadeIn>
      <section className="bg-band-blue px-4 py-5 md:px-5">
        <div className="flex items-center gap-4">
          {flexibility ? (
            <ScoreGauge score={flexibility.currentScore} tier={flexibility.currentTier} />
          ) : (
            <Skeleton className="h-24 w-24 rounded-full" />
          )}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ink">Breathing room</p>
            <p className="mt-1 text-xs text-ink-body">
              {flexibility?.explanation ?? "Add income and spending to see this."}
            </p>
          </div>
        </div>
        <StaggerContainer className="mt-5 grid grid-cols-3 gap-4">
          <StaggerItem>
            <Stat label="Today" value={formatINR(data.today?.totalSpend ?? 0, { compact: true })} />
          </StaggerItem>
          <StaggerItem>
            <Stat label="This week" value={formatINR(data.week?.totalSpend ?? 0, { compact: true })} />
          </StaggerItem>
          <StaggerItem>
            <Stat label="Entries this week" value={String(data.week?.transactionCount ?? 0)} />
          </StaggerItem>
        </StaggerContainer>
        {topCategory && (
          <p className="mt-4 text-xs text-ink-meta">
            Top category this week: <span className="font-medium text-ink">{topCategory.displayName}</span>
            {" · "}
            {formatINR(topCategory.amount, { compact: true })}
          </p>
        )}
      </section>
    </FadeIn>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-base font-medium tabular-nums text-ink">{value}</p>
      <p className="text-xs text-ink-meta">{label}</p>
    </div>
  );
}

function QuickActions({ onCapture }: { onCapture: (tab: CaptureTab) => void }) {
  return (
    <div className="grid grid-cols-2 gap-3">
      <Button variant="outline" className="no-drag" onClick={() => onCapture("transaction")}>
        <Plus /> Add transaction
      </Button>
      <Button variant="outline" className="no-drag" onClick={() => onCapture("receipt")}>
        <ScanLine /> Scan receipt
      </Button>
    </div>
  );
}

function SignalsSection({
  signals,
  onNavigate,
}: {
  signals: OverviewSignal[];
  onNavigate: (v: ViewKey) => void;
}) {
  return (
    <section>
      <SignalSectionHeader
        icon={<Sparkles className="h-4 w-4" />}
        title="Worth a look"
        subtitle={signals.length ? `${signals.length} to check` : undefined}
        className="mb-2"
        action={
          <Button variant="ghost" size="sm" className="text-primary" onClick={() => onNavigate("insights")}>
            View all <ArrowUpRight />
          </Button>
        }
      />
      {signals.length === 0 ? (
        <InsightCallout tone="signal">
          Nothing needs your attention right now. Your spending looks steady.
        </InsightCallout>
      ) : (
        <div className="space-y-3">
          {signals.map((s) => (
            <SignalCard
              key={s.id}
              severity={s.severity}
              title={s.title}
              metric={
                s.metric ? <span className="text-sm font-medium tabular-nums text-ink">{s.metric}</span> : undefined
              }
            >
              <p className="text-xs text-ink-body">{s.detail}</p>
            </SignalCard>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentSection({
  recent,
  onCapture,
}: {
  recent: Transaction[];
  onCapture: (tab: CaptureTab) => void;
}) {
  return (
    <section>
      <SignalSectionHeader
        icon={<Wallet className="h-4 w-4" />}
        title="Recent activity"
        className="mb-2"
        action={
          <Button variant="ghost" size="sm" className="text-primary" onClick={() => onCapture("transaction")}>
            Add
          </Button>
        }
      />
      <div className="divide-y divide-border">
        {recent.length === 0 ? (
          <p className="py-5 text-center text-xs text-ink-meta">No transactions yet.</p>
        ) : (
          recent.map((t) => <TransactionRow key={t.id} t={t} />)
        )}
      </div>
    </section>
  );
}

function TransactionRow({ t }: { t: Transaction }) {
  const debit = t.type === "DEBIT";
  const name = t.merchant || t.description || "Transaction";
  return (
    <div className="flex items-center justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <p className="truncate text-xs font-medium text-ink">{name}</p>
        <p className="text-2xs text-ink-meta">
          {formatShortDate(t.transactionDate)}
          {t.categoryDisplayName ? ` · ${t.categoryDisplayName}` : ""}
        </p>
      </div>
      <span className="shrink-0 text-xs font-medium tabular-nums text-ink">
        {debit ? "-" : "+"}
        {formatINR(t.amount)}
      </span>
    </div>
  );
}

function OverviewSkeleton() {
  return (
    <div role="status" aria-busy="true">
      <span className="sr-only">Loading overview</span>
      <Skeleton className="h-36 w-full rounded-none" />
      <div className="space-y-4 px-4 py-4">
        <Skeleton className="h-11 w-full rounded-md" />
        <Skeleton className="h-24 w-full rounded-md" />
      </div>
    </div>
  );
}
