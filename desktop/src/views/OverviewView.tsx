import {
  TrendingDown,
  CalendarDays,
  Receipt as ReceiptIcon,
  ArrowUpRight,
  Sparkles,
  Gauge,
  Wallet,
  Plus,
  ScanLine,
} from "lucide-react";
import { FadeIn, StaggerContainer, StaggerItem, AmbientGlow } from "@/components/motion/primitives";
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
import { Skeleton, Button } from "~/components/ui";
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

// -----------------------------------------------------------------------------
// COMPACT — widget. Score, today's spend, quick actions, top 2 signals. Nothing
// else: understand state in 3s, act in <5s.
// -----------------------------------------------------------------------------
function CompactOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  const top = signals.slice(0, 2);
  return (
    <div className="space-y-4 px-3.5 py-3.5">
      <FadeIn>
        <div className="card-refined surface-gradient-emerald relative overflow-hidden p-3.5">
          <AmbientGlow />
          <div className="relative z-10 flex items-center gap-3.5">
            {data.flexibility ? (
              <ScoreGauge score={data.flexibility.currentScore} tier={data.flexibility.currentTier} size={72} />
            ) : (
              <Skeleton className="h-[72px] w-[72px] rounded-full" />
            )}
            <div className="min-w-0 flex-1">
              <p className="text-overline">Breathing room</p>
              <p className="mt-1 line-clamp-2 text-[11px] leading-relaxed text-muted-foreground">
                {data.flexibility?.explanation ?? "Add some income and spending to see this."}
              </p>
              <p className="mt-1.5 text-xs text-muted-foreground">
                Today <span className="font-semibold text-foreground">{formatINR(data.today?.totalSpend ?? 0, { compact: true })}</span>
              </p>
            </div>
          </div>
        </div>
      </FadeIn>

      <QuickActions onCapture={onCapture} />

      <section>
        <SignalSectionHeader
          icon={<Sparkles className="h-4 w-4" />}
          title="Worth a look"
          action={
            <Button variant="ghost" className="text-[11px]" onClick={() => onNavigate("insights")}>
              All <ArrowUpRight className="h-3 w-3" />
            </Button>
          }
        />
        {top.length === 0 ? (
          <div className="card-refined p-3.5">
            <InsightCallout tone="signal">All steady. Nothing needs your attention right now.</InsightCallout>
          </div>
        ) : (
          <div className="card-refined divide-y divide-border px-3.5">
            {top.map((s) => (
              <div key={s.id} className="flex items-center gap-2.5 py-2.5">
                <SignalDot severity={s.severity} />
                <p className="min-w-0 flex-1 truncate text-xs font-medium text-foreground">{s.title}</p>
                {s.metric && (
                  <span className="shrink-0 text-xs font-semibold tabular-nums text-foreground">{s.metric}</span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

// -----------------------------------------------------------------------------
// MEDIUM — companion. Score, snapshot, quick actions, 3 signals, 3 recent.
// -----------------------------------------------------------------------------
function MediumOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  return (
    <div className="space-y-5 px-4 py-4">
      <ScoreHero flexibility={data.flexibility} />
      <SnapshotTiles data={data} />
      <QuickActions onCapture={onCapture} />
      <SignalsSection signals={signals.slice(0, 3)} onNavigate={onNavigate} />
      <RecentSection recent={data.recent.slice(0, 3)} onCapture={onCapture} />
    </div>
  );
}

// -----------------------------------------------------------------------------
// LARGE — desktop window. Two columns; more signals + recent.
// -----------------------------------------------------------------------------
function LargeOverview({
  data,
  signals,
  onNavigate,
  onCapture,
}: { data: OverviewData; signals: OverviewSignal[] } & OverviewProps) {
  return (
    <div className="grid grid-cols-2 gap-5 px-5 py-5">
      <div className="space-y-5">
        <ScoreHero flexibility={data.flexibility} />
        <SnapshotTiles data={data} />
        <QuickActions onCapture={onCapture} />
      </div>
      <div className="space-y-5">
        <SignalsSection signals={signals.slice(0, 5)} onNavigate={onNavigate} />
        <RecentSection recent={data.recent.slice(0, 6)} onCapture={onCapture} />
      </div>
    </div>
  );
}

// -----------------------------------------------------------------------------
// Shared building blocks
// -----------------------------------------------------------------------------
function ScoreHero({ flexibility }: { flexibility: FlexibilityScore | null }) {
  return (
    <FadeIn>
      <div className="card-refined surface-gradient-emerald relative overflow-hidden p-4">
        <AmbientGlow />
        <div className="relative z-10 flex items-center gap-4">
          {flexibility ? (
            <ScoreGauge score={flexibility.currentScore} tier={flexibility.currentTier} />
          ) : (
            <Skeleton className="h-24 w-24 rounded-full" />
          )}
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <Gauge className="h-3.5 w-3.5 text-muted-foreground" strokeWidth={2} />
              <p className="text-overline">Breathing room</p>
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
              {flexibility?.explanation ?? "Add income and spending to see this."}
            </p>
          </div>
        </div>
      </div>
    </FadeIn>
  );
}

function SnapshotTiles({ data }: { data: OverviewData }) {
  const topCategory = data.week?.categoryBreakdown?.[0] ?? null;
  return (
    <section>
      <StaggerContainer className="grid grid-cols-3 gap-2.5">
        <StaggerItem>
          <SnapshotTile icon={<TrendingDown className="h-3.5 w-3.5" />} label="Today" value={formatINR(data.today?.totalSpend ?? 0, { compact: true })} />
        </StaggerItem>
        <StaggerItem>
          <SnapshotTile icon={<CalendarDays className="h-3.5 w-3.5" />} label="This week" value={formatINR(data.week?.totalSpend ?? 0, { compact: true })} />
        </StaggerItem>
        <StaggerItem>
          <SnapshotTile icon={<ReceiptIcon className="h-3.5 w-3.5" />} label="Entries (wk)" value={String(data.week?.transactionCount ?? 0)} />
        </StaggerItem>
      </StaggerContainer>
      {topCategory && (
        <p className="mt-2 px-1 text-[11px] text-muted-foreground">
          Top category this week:{" "}
          <span className="font-medium text-foreground">{topCategory.displayName}</span> ·{" "}
          {formatINR(topCategory.amount, { compact: true })}
        </p>
      )}
    </section>
  );
}

function QuickActions({ onCapture }: { onCapture: (tab: CaptureTab) => void }) {
  return (
    <div className="grid grid-cols-2 gap-2.5">
      <QuickActionButton icon={<Plus className="h-4 w-4" />} label="Add transaction" onClick={() => onCapture("transaction")} />
      <QuickActionButton icon={<ScanLine className="h-4 w-4" />} label="Scan receipt" onClick={() => onCapture("receipt")} />
    </div>
  );
}

function QuickActionButton({
  icon,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="card-tactile no-drag flex items-center gap-2.5 p-3 text-left"
    >
      <span
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: "hsl(var(--signal-soft))", color: "hsl(var(--signal))" }}
      >
        {icon}
      </span>
      <span className="text-xs font-medium text-foreground">{label}</span>
    </button>
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
        action={
          <Button variant="ghost" className="text-[11px]" onClick={() => onNavigate("insights")}>
            View all <ArrowUpRight className="h-3 w-3" />
          </Button>
        }
      />
      {signals.length === 0 ? (
        <div className="card-refined p-4">
          <InsightCallout tone="signal">
            Nothing needs your attention right now. Your spending looks steady.
          </InsightCallout>
        </div>
      ) : (
        <div className="space-y-2.5">
          {signals.map((s) => (
            <SignalCard
              key={s.id}
              severity={s.severity}
              title={s.title}
              metric={
                s.metric ? (
                  <span className="text-sm font-semibold tabular-nums text-foreground">{s.metric}</span>
                ) : undefined
              }
            >
              <p className="text-xs leading-relaxed text-muted-foreground">{s.detail}</p>
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
        action={
          <Button variant="ghost" className="text-[11px]" onClick={() => onCapture("transaction")}>
            Add
          </Button>
        }
      />
      <div className="card-refined divide-y divide-border overflow-hidden">
        {recent.length === 0 ? (
          <p className="px-4 py-5 text-center text-xs text-muted-foreground">No transactions yet.</p>
        ) : (
          recent.map((t) => <TransactionRow key={t.id} t={t} />)
        )}
      </div>
    </section>
  );
}

function SnapshotTile({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="card-refined px-3 py-2.5">
      <span className="text-muted-foreground">{icon}</span>
      <p className="mt-1 text-base font-semibold tabular-nums text-foreground">{value}</p>
      <p className="stat-label">{label}</p>
    </div>
  );
}

function TransactionRow({ t }: { t: Transaction }) {
  const debit = t.type === "DEBIT";
  const name = t.merchant || t.description || "Transaction";
  return (
    <div className="flex items-center justify-between gap-3 px-4 py-2.5">
      <div className="min-w-0">
        <p className="truncate text-xs font-medium text-foreground">{name}</p>
        <p className="text-[10px] text-muted-foreground">
          {formatShortDate(t.transactionDate)}
          {t.categoryDisplayName ? ` · ${t.categoryDisplayName}` : ""}
        </p>
      </div>
      <span
        className="shrink-0 text-xs font-semibold tabular-nums"
        style={{ color: debit ? "hsl(var(--foreground))" : "hsl(var(--positive))" }}
      >
        {debit ? "-" : "+"}
        {formatINR(t.amount)}
      </span>
    </div>
  );
}

function OverviewSkeleton() {
  return (
    <div className="space-y-5 px-4 py-4">
      <Skeleton className="h-28 w-full rounded-xl" />
      <div className="grid grid-cols-3 gap-2.5">
        <Skeleton className="h-16 rounded-xl" />
        <Skeleton className="h-16 rounded-xl" />
        <Skeleton className="h-16 rounded-xl" />
      </div>
      <Skeleton className="h-24 w-full rounded-xl" />
    </div>
  );
}
