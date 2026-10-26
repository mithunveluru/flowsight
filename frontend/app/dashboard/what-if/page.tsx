"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  Coins,
  CreditCard,
  Info,
  PiggyBank,
  TrendingDown,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ChartLegend } from "@/components/ui/chart-frame";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { ErrorState } from "@/components/ui/states";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { HeroBand } from "@/components/ui/band";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { simulationApi } from "@/features/simulation/api";
import {
  assumptionText,
  clampReturn,
  fanRows,
  fanSummary,
  flexibilityEvidence,
  goalOddsText,
  monteCarloEvidence,
  percent,
} from "@/features/simulation/fan";
import type {
  ConsequenceInsight,
  FinancialBaseline,
  FlexibilityScore,
  FlexibilityTier,
  MonteCarloResult,
  Projection,
  ScenarioRequest,
  ScenarioType,
  Severity,
  SimulationAssumptions,
  SimulationResult,
  Tradeoff,
} from "@/features/simulation/types";
import {
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import { CountUp, Reveal, Stagger, StaggerItem } from "@/components/motion/primitives";
import { ChartPanel } from "@/components/insight/chart-parts";
import { LineChart, type LineSeries } from "@/components/insight/line-chart";
import { ContentBand, FullBleed, SectionHead, Surface } from "@/components/insight/page-kit";
import { ProgressBar } from "@/components/insight/progress";
import { rupees, rupeesCompact } from "@/features/insights/format";
import { cn } from "@/lib/utils";

function formatINR(v: number): string {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function formatINRSigned(v: number): string {
  if (v > 0) return `+${formatINR(v)}`;
  if (v < 0) return `−${formatINR(Math.abs(v))}`;
  return formatINR(0);
}

const TIER_META: Record<FlexibilityTier, { label: string; variant: "emerald" | "blue" | "slate" | "amber" | "red" }> = {
  EXCELLENT: { label: "Excellent", variant: "emerald" },
  GOOD: { label: "Good", variant: "blue" },
  FAIR: { label: "Fair", variant: "slate" },
  TIGHT: { label: "Tight", variant: "amber" },
  CONSTRAINED: { label: "Constrained", variant: "red" },
};

const SEVERITY_ICON: Record<Severity, { icon: React.ElementType; cls: string; word: string }> = {
  POSITIVE: { icon: CheckCircle2, cls: "text-positive", word: "Good news" },
  NEUTRAL: { icon: Info, cls: "text-ink-meta", word: "Note" },
  CAUTION: { icon: AlertTriangle, cls: "text-caution", word: "Caution" },
  WARNING: { icon: AlertTriangle, cls: "text-warning", word: "Warning" },
};

const DEFAULT_SCENARIO: Record<ScenarioType, ScenarioRequest> = {
  ONE_TIME_PURCHASE: { type: "ONE_TIME_PURCHASE", name: "New phone", amount: 60000, category: "SHOPPING" },
  RECURRING_EXPENSE: { type: "RECURRING_EXPENSE", name: "Streaming bundle", amount: 999, category: "ENTERTAINMENT", durationMonths: 12 },
  SAVINGS_ADJUSTMENT: { type: "SAVINGS_ADJUSTMENT", name: "Boost savings", amount: 10000, durationMonths: 12 },
  LOAN_EMI: { type: "LOAN_EMI", name: "Vehicle loan", amount: 1000000, annualInterestRate: 9.5, tenureMonths: 36 },
};

const SCENARIO_TAB_META: Record<ScenarioType, { label: string; icon: React.ElementType }> = {
  ONE_TIME_PURCHASE: { label: "Buy something", icon: Coins },
  RECURRING_EXPENSE: { label: "Add a subscription", icon: CreditCard },
  SAVINGS_ADJUSTMENT: { label: "Adjust savings", icon: PiggyBank },
  LOAN_EMI: { label: "Take a loan", icon: TrendingDown },
};

// slider span for the main amount of each scenario
const AMOUNT_RANGE: Record<ScenarioType, { max: number; step: number }> = {
  ONE_TIME_PURCHASE: { max: 500000, step: 1000 },
  RECURRING_EXPENSE: { max: 20000, step: 100 },
  SAVINGS_ADJUSTMENT: { max: 100000, step: 500 },
  LOAN_EMI: { max: 5000000, step: 10000 },
};

export default function SimulatePage() {
  const [scenario, setScenario] = useState<ScenarioRequest>(DEFAULT_SCENARIO.ONE_TIME_PURCHASE);
  const [result,   setResult]   = useState<SimulationResult | null>(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState<string | null>(null);
  const [attempt,  setAttempt]  = useState(0);

  // Debounced auto-simulation keeps the page live without spamming the API
  useEffect(() => {
    if (!scenario.amount || scenario.amount <= 0) return;
    const t = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const r = await simulationApi.simulate(scenario);
        setResult(r);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Simulation failed");
      } finally {
        setLoading(false);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [scenario, attempt]);

  const handleTypeChange = useCallback((type: ScenarioType) => {
    setScenario((prev) => ({ ...DEFAULT_SCENARIO[type], annualReturnPercent: prev.annualReturnPercent }));
  }, []);

  return (
    <div>
      <FullBleed flushTop>
        <HeroBand
          title="What-if"
          lead="Thinking about a big purchase or a new commitment? See how it would affect your money, your breathing room, and your savings before you decide."
        />
      </FullBleed>

      <div className="h-sp-hero" aria-hidden="true" />

      <ContentBand tone="blue" space="md">
        <div className="grid gap-sp-max lg:grid-cols-[minmax(0,360px)_1fr]">
          <aside className="lg:sticky lg:top-sp-giant lg:self-start">
            <ScenarioBuilder scenario={scenario} onChange={setScenario} onTypeChange={handleTypeChange} />
          </aside>

          <section aria-label="Results" className="min-w-0 space-y-sp-max">
            {error && (
              <ErrorState
                title="The simulation did not run"
                description={error}
                onRetry={() => setAttempt((n) => n + 1)}
              />
            )}

            {result ? (
              <Stagger className="space-y-sp-max">
                <StaggerItem><ImpactSummary result={result} /></StaggerItem>
                {result.monteCarlo && (
                  <StaggerItem><FanCard mc={result.monteCarlo} assumptions={result.assumptions} /></StaggerItem>
                )}
                {result.monteCarlo?.goal && (
                  <StaggerItem><GoalOddsCard odds={result.monteCarlo.goal} /></StaggerItem>
                )}
                <StaggerItem><FlexibilityCard flexibility={result.flexibility} baseline={result.baseline} mc={result.monteCarlo} /></StaggerItem>
                <StaggerItem><ProjectionChart projection={result.projection} /></StaggerItem>
                <StaggerItem><TradeoffAnalysis tradeoffs={result.tradeoffs} /></StaggerItem>
                <StaggerItem><ConsequenceInsights insights={result.insights} /></StaggerItem>
                {!result.baseline.hasEnoughData && (
                  <StaggerItem><LowDataNotice /></StaggerItem>
                )}
              </Stagger>
            ) : (
              <Reveal><PendingState loading={loading} /></Reveal>
            )}
          </section>
        </div>
      </ContentBand>
    </div>
  );
}

function ScenarioBuilder({
  scenario,
  onChange,
  onTypeChange,
}: {
  scenario: ScenarioRequest;
  onChange: (s: ScenarioRequest) => void;
  onTypeChange: (t: ScenarioType) => void;
}) {
  const update = (partial: Partial<ScenarioRequest>) => onChange({ ...scenario, ...partial });

  return (
    <Surface tone="canvas">
      <p className="text-sm font-medium text-ink" id="scenario-type">Scenario type</p>
      <div role="group" aria-labelledby="scenario-type" className="mt-sp-sm grid grid-cols-2 gap-sp-sm">
        {(Object.keys(SCENARIO_TAB_META) as ScenarioType[]).map((t) => {
          const meta = SCENARIO_TAB_META[t];
          const Icon = meta.icon;
          const active = scenario.type === t;
          return (
            <button
              key={t}
              type="button"
              aria-pressed={active}
              onClick={() => onTypeChange(t)}
              className={cn(
                "press flex min-h-11 flex-col items-start gap-sp-xs rounded-md px-sp-xl py-sp-xl text-left text-sm font-medium transition-colors duration-fast",
                active ? "bg-primary text-primary-foreground" : "bg-chrome text-ink hover:bg-band"
              )}
            >
              <Icon className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
              {meta.label}
            </button>
          );
        })}
      </div>

      <div className="mt-sp-giant space-y-sp-wide">
        <Field label="Name">
          <Input
            type="text"
            value={scenario.name ?? ""}
            onChange={(e) => update({ name: e.target.value })}
            placeholder="e.g. New phone"
          />
        </Field>

        {scenario.type === "ONE_TIME_PURCHASE" && (
          <>
            <AmountField label="Amount" type={scenario.type} value={scenario.amount} onChange={(v) => update({ amount: v })} />
            <Field label="Category (optional)">
              <CategorySelect value={scenario.category} onChange={(v) => update({ category: v })} />
            </Field>
          </>
        )}

        {scenario.type === "RECURRING_EXPENSE" && (
          <>
            <AmountField label="Monthly amount" type={scenario.type} value={scenario.amount} onChange={(v) => update({ amount: v })} />
            <Field label="Duration (months)">
              <NumberInput value={scenario.durationMonths ?? 12} onChange={(v) => update({ durationMonths: v })} min={1} max={120} />
            </Field>
            <Field label="Category (optional)">
              <CategorySelect value={scenario.category} onChange={(v) => update({ category: v })} />
            </Field>
          </>
        )}

        {scenario.type === "SAVINGS_ADJUSTMENT" && (
          <>
            <Field label="Direction">
              <Select
                value={scenario.amount >= 0 ? "INCREASE" : "DECREASE"}
                onValueChange={(v) => update({ amount: v === "INCREASE" ? Math.abs(scenario.amount) : -Math.abs(scenario.amount) })}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INCREASE">Increase savings</SelectItem>
                  <SelectItem value="DECREASE">Decrease savings</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <AmountField
              label="Monthly amount"
              type={scenario.type}
              value={Math.abs(scenario.amount)}
              onChange={(v) => update({ amount: scenario.amount < 0 ? -v : v })}
            />
            <Field label="Duration (months)">
              <NumberInput value={scenario.durationMonths ?? 12} onChange={(v) => update({ durationMonths: v })} min={1} max={120} />
            </Field>
          </>
        )}

        {scenario.type === "LOAN_EMI" && (
          <>
            <AmountField label="Principal" type={scenario.type} value={scenario.amount} onChange={(v) => update({ amount: v })} />
            <Field label="Annual interest rate (%)">
              <NumberInput value={scenario.annualInterestRate ?? 9.5} onChange={(v) => update({ annualInterestRate: v })} min={0} max={36} step={0.1} />
            </Field>
            <Field label="Tenure (months)">
              <NumberInput value={scenario.tenureMonths ?? 36} onChange={(v) => update({ tenureMonths: v })} min={1} max={360} />
            </Field>
          </>
        )}
      </div>

      <div className="mt-sp-giant border-t border-border pt-sp-giant">
        <Field label="Assumed yearly return (%)" hint="Used for opportunity-cost figures. Leave blank for the default.">
          <Input
            type="number"
            inputMode="decimal"
            min={0}
            max={30}
            step={0.5}
            placeholder="Default"
            value={scenario.annualReturnPercent ?? ""}
            onChange={(e) => {
              const v = clampReturn(e.target.value);
              update({ annualReturnPercent: v ?? undefined });
            }}
          />
        </Field>
      </div>

      <p className="mt-sp-giant text-xs text-ink-meta">
        The projection updates as you adjust values. It uses your last 3 months of transactions and active recurring patterns as the baseline.
      </p>
    </Surface>
  );
}

function AmountField({
  label,
  type,
  value,
  onChange,
}: {
  label: string;
  type: ScenarioType;
  value: number;
  onChange: (v: number) => void;
}) {
  const { max, step } = AMOUNT_RANGE[type];
  return (
    <Field label={label}>
      <div className="space-y-sp-xl">
        <div className="flex items-center gap-sp-sm">
          <span className="text-sm text-ink-meta">INR</span>
          <Input
            type="number"
            value={value || ""}
            onChange={(e) => onChange(Number(e.target.value) || 0)}
            min={0}
            step={100}
            placeholder="0"
            className="flex-1"
          />
        </div>
        <input
          type="range"
          aria-label={`${label} slider`}
          min={0}
          max={Math.max(max, value)}
          step={step}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          className="block h-11 w-full cursor-pointer accent-primary"
        />
      </div>
    </Field>
  );
}

function NumberInput({
  value, onChange, min, max, step = 1,
}: {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
}) {
  return (
    <Input
      type="number"
      value={value || ""}
      onChange={(e) => onChange(Number(e.target.value) || 0)}
      min={min}
      max={max}
      step={step}
    />
  );
}

function CategorySelect({
  value, onChange,
}: {
  value?: TransactionCategory;
  onChange: (v: TransactionCategory | undefined) => void;
}) {
  return (
    <Select
      value={value ?? "__none__"}
      onValueChange={(v) => onChange(v === "__none__" ? undefined : (v as TransactionCategory))}
    >
      <SelectTrigger aria-label="Category"><SelectValue placeholder="Auto-detect" /></SelectTrigger>
      <SelectContent>
        <SelectItem value="__none__">No category</SelectItem>
        {CATEGORY_OPTIONS.map((opt) => (
          <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function Delta({ value, children }: { value: number; children: React.ReactNode }) {
  const Icon = value > 0 ? ArrowUp : value < 0 ? ArrowDown : null;
  return (
    <span className="inline-flex items-center gap-sp-xs">
      {Icon && <Icon className="h-5 w-5 shrink-0" strokeWidth={2} aria-hidden="true" />}
      {children}
      <span className="sr-only">{value > 0 ? ", gain" : value < 0 ? ", loss" : ""}</span>
    </span>
  );
}

function ImpactSummary({ result }: { result: SimulationResult }) {
  const positive = result.monthlyImpact >= 0;
  const oneTime = result.scenario.type === "ONE_TIME_PURCHASE";

  const cards = [
    {
      label: "Monthly cashflow impact",
      value: oneTime ? 0 : result.monthlyImpact,
      format: formatINRSigned,
      hint: oneTime ? "One-time outlay" : positive ? "Net inflow" : "Net outflow",
    },
    { label: "Yearly impact", value: result.yearlyImpact, format: rupeesCompact, hint: "Total over 12 months" },
    { label: "5-year cumulative", value: result.fiveYearImpact, format: rupeesCompact, hint: "Total over 60 months" },
    {
      label: "10-year opportunity cost",
      value: result.tenYearOpportunityCost,
      format: rupeesCompact,
      hint: result.assumptions ? `If invested at ${assumptionText(result.assumptions)}` : "If invested",
    },
  ];

  return (
    <Surface tone="canvas" className="grid grid-cols-2 gap-sp-giant lg:grid-cols-4">
      {cards.map((c) => (
        <div key={c.label} className="min-w-0">
          <p className="text-sm text-ink-meta">{c.label}</p>
          <p className="mt-sp-xs text-ty-subhead tabular-nums text-ink">
            <Delta value={c.value}>
              <CountUp value={Math.round(c.value)} format={c.format} />
            </Delta>
          </p>
          <p className="mt-sp-xs text-xs text-ink-meta">{c.hint}</p>
        </div>
      ))}
    </Surface>
  );
}

function FlexibilityCard({
  flexibility,
  baseline,
  mc,
}: {
  flexibility: FlexibilityScore;
  baseline: FinancialBaseline;
  mc?: MonteCarloResult | null;
}) {
  const delta = flexibility.deltaPercent;

  const Column = ({ title, score, tier }: { title: string; score: number; tier: FlexibilityTier }) => (
    <div className="space-y-sp-sm">
      <p className="text-sm text-ink-meta">{title}</p>
      <p className="text-ty-section-title tabular-nums text-ink">
        <CountUp value={score} duration={0.6} />
        <span className="text-ty-link text-ink-meta">/100</span>
      </p>
      <Badge variant={TIER_META[tier].variant}>{TIER_META[tier].label}</Badge>
      <ProgressBar value={score} label={`${title} breathing room score`} valueText={`${score} out of 100, ${TIER_META[tier].label}`} />
    </div>
  );

  return (
    <Surface tone="canvas">
      <SectionHead
        as="h3"
        title="Breathing room"
        lead="How much room you would have to absorb surprises and pursue what comes next."
        className="mb-sp-giant"
      />

      <div className="grid items-center gap-sp-giant sm:grid-cols-[1fr_auto_1fr]">
        <Column title="Current" score={flexibility.currentScore} tier={flexibility.currentTier} />
        <Badge variant={delta >= 0 ? "emerald" : "red"} className="hidden gap-sp-xs sm:inline-flex">
          {delta >= 0 ? <ArrowUp className="h-3 w-3" aria-hidden="true" /> : <ArrowDown className="h-3 w-3" aria-hidden="true" />}
          {delta >= 0 ? "+" : "−"}{Math.abs(delta).toFixed(1)}%
        </Badge>
        <Column title="After this decision" score={flexibility.projectedScore} tier={flexibility.projectedTier} />
      </div>

      <p className="mt-sp-giant text-sm text-ink-body">{flexibility.explanation}</p>
      {mc && (
        <p className="mt-sp-xl flex flex-wrap items-center gap-x-sp-sm text-sm text-ink-body">
          <span>
            Across simulated futures the score after this decision is typically{" "}
            <span className="font-medium tabular-nums text-ink">{mc.flexibility.p10}</span> to{" "}
            <span className="font-medium tabular-nums text-ink">{mc.flexibility.p90}</span> (median{" "}
            <span className="font-medium tabular-nums text-ink">{mc.flexibility.p50}</span>).
          </span>
          <EvidenceDrawer
            title="Breathing room range"
            value={`${mc.flexibility.p10} to ${mc.flexibility.p90}`}
            evidence={flexibilityEvidence(mc.baselineFlexibility, mc.flexibility, mc)}
            trigger={<EvidenceTrigger label="the breathing room range" className="coarse:h-11 coarse:min-w-11" />}
          />
        </p>
      )}

      {baseline.hasEnoughData && (
        <dl className="mt-sp-giant grid gap-sp-sm text-xs text-ink-meta sm:grid-cols-3">
          {[
            ["Income", baseline.monthlyIncome],
            ["Spend", baseline.monthlySpend],
            ["Recurring", baseline.monthlyRecurring],
          ].map(([k, v]) => (
            <div key={k as string} className="flex gap-sp-xs">
              <dt>{k}:</dt>
              <dd className="font-medium tabular-nums text-ink">{formatINR(v as number)}/mo</dd>
            </div>
          ))}
        </dl>
      )}
    </Surface>
  );
}

const PRIMARY = "hsl(var(--chart-spend))";
const SLATE = "hsl(var(--chart-axis))";

function ProjectionChart({ projection }: { projection: Projection }) {
  const data = useMemo(
    () =>
      projection.before.map((p, i) => ({
        month: `M${p.month}`,
        before: p.cumulativeSavings,
        after: projection.after[i]?.cumulativeSavings ?? 0,
      })),
    [projection]
  );
  const series = useMemo<LineSeries[]>(
    () => [
      { key: "after", label: "With it", color: PRIMARY, marker: "circle", values: data.map((d) => d.after) },
      { key: "before", label: "Current", color: SLATE, marker: "square", dash: "5 4", values: data.map((d) => d.before) },
    ],
    [data]
  );
  const last = data.at(-1);

  return (
    <Surface tone="canvas">
      <ChartPanel
        title="12-month projection"
        description="Cumulative savings position, current trajectory against simulated."
        summary={last ? `After ${data.length} months: ${formatINR(last.before)} on the current trajectory, ${formatINR(last.after)} with this decision.` : undefined}
        state={data.length ? "ready" : "empty"}
        height={280}
        legend={
          <ChartLegend
            className="justify-start"
            items={[
              { label: "With this decision", color: PRIMARY, marker: "circle" },
              { label: "Current trajectory", color: SLATE, variant: "dashed", marker: "square" },
            ]}
          />
        }
      >
        <LineChart
          categories={data.map((d) => d.month)}
          series={series}
          height={280}
          format={rupees}
          formatAxis={rupeesCompact}
          ariaLabel="Cumulative savings, current trajectory against simulated"
        />
      </ChartPanel>
    </Surface>
  );
}

function FanCard({ mc, assumptions }: { mc: MonteCarloResult; assumptions?: SimulationAssumptions }) {
  const rows = useMemo(() => fanRows(mc.fan, mc.baselineFan), [mc.fan, mc.baselineFan]);
  const series = useMemo<LineSeries[]>(
    () => [
      {
        key: "p50",
        label: "Median",
        color: PRIMARY,
        marker: "circle",
        values: rows.map((r) => r.p50),
        lower: rows.map((r) => r.p10),
        upper: rows.map((r) => r.p90),
      },
      { key: "baseP50", label: "Without it", color: SLATE, marker: "square", dash: "5 4", values: rows.map((r) => r.baseP50) },
    ],
    [rows]
  );
  const last = rows.at(-1);

  return (
    <Surface tone="canvas">
      <ChartPanel
        title="Range of outcomes"
        description="Cumulative savings from today across thousands of simulated futures that replay your own month-to-month swings."
        summary={fanSummary(mc.fan)}
        state={rows.length ? "ready" : "empty"}
        emptyTitle="No simulated paths"
        height={300}
        actions={
          <EvidenceDrawer
            title="Range of outcomes"
            value={last ? `${rupeesCompact(last.p10)} to ${rupeesCompact(last.p90)} at month ${last.month}` : undefined}
            evidence={monteCarloEvidence(mc, assumptions)}
            trigger={<EvidenceTrigger label="the range of outcomes" className="coarse:h-11 coarse:min-w-11" />}
          />
        }
        legend={
          <div className="space-y-sp-sm">
            <ChartLegend
              className="justify-start"
              items={[
                { label: "Median with this decision", color: PRIMARY, marker: "circle" },
                { label: "Median without it", color: SLATE, variant: "dashed", marker: "square" },
              ]}
            />
            <p className="flex items-center gap-sp-sm text-xs text-ink-meta">
              <span aria-hidden="true" className="h-3 w-6 rounded-sm" style={{ backgroundColor: PRIMARY, opacity: 0.16 }} />
              Shaded band: the middle 80% of futures.
            </p>
            {assumptions && (
              <p className="text-xs text-ink-meta">
                Return assumption: {assumptionText(assumptions)}. {assumptions.label}
              </p>
            )}
          </div>
        }
      >
        <LineChart
          categories={rows.map((r) => r.label)}
          series={series}
          height={300}
          format={rupees}
          formatAxis={rupeesCompact}
          ariaLabel="Range of simulated savings outcomes by month"
          tooltipRows={(i) => {
            const row = rows[i];
            if (!row) return null;
            return (
              <dl className="space-y-sp-xs tabular-nums">
                {[
                  ["High", row.p90],
                  ["Median", row.p50],
                  ["Low", row.p10],
                  ...(row.baseP50 != null ? [["Without", row.baseP50]] : []),
                ].map(([k, v]) => (
                  <div key={k as string} className="flex justify-between gap-sp-xl">
                    <dt>{k}</dt>
                    <dd className="text-ink">{rupees(v as number)}</dd>
                  </div>
                ))}
              </dl>
            );
          }}
        />
      </ChartPanel>
    </Surface>
  );
}

function GoalOddsCard({ odds }: { odds: NonNullable<MonteCarloResult["goal"]> }) {
  const t = goalOddsText(odds);
  const pct = Math.round(odds.probabilityByTargetDate * 100);
  return (
    <Surface tone="canvas">
      <h3 className="text-ty-subhead text-ink">Chance of reaching {odds.goalName}</h3>
      <p className="mt-sp-sm text-ty-section-title tabular-nums text-ink">{percent(odds.probabilityByTargetDate)}</p>
      <ProgressBar
        className="mt-sp-xl"
        value={pct}
        label={`Chance of reaching ${odds.goalName} by ${odds.targetDate}`}
        valueText={`${pct} percent`}
      />
      <p className="mt-sp-xl text-sm text-ink-body">
        By {odds.targetDate}; {t.baseline}. About {formatINR(odds.remaining)} still to save.
      </p>
      <p className="mt-sp-xs text-sm text-ink-meta">{t.reach}.</p>
    </Surface>
  );
}

function TradeoffAnalysis({ tradeoffs }: { tradeoffs: Tradeoff[] }) {
  if (tradeoffs.length === 0) return null;
  return (
    <Surface tone="canvas">
      <SectionHead as="h3" title="Key tradeoffs" lead="The concrete numbers behind this decision." className="mb-sp-giant" />
      <div className="grid gap-sp-wide sm:grid-cols-3">
        {tradeoffs.map((t, i) => (
          <div key={i} className="rounded-md bg-chrome p-sp-wide">
            <p className="text-sm text-ink-meta">{t.label}</p>
            <p className="mt-sp-xs text-ty-subhead tabular-nums text-ink">{t.value}</p>
            <p className="mt-sp-xs text-xs text-ink-meta">{t.description}</p>
          </div>
        ))}
      </div>
    </Surface>
  );
}

function ConsequenceInsights({ insights }: { insights: ConsequenceInsight[] }) {
  if (insights.length === 0) return null;
  return (
    <Surface tone="canvas">
      <SectionHead as="h3" title="Consequences" lead="Plain-language read-outs grounded in your data." className="mb-sp-giant" />
      <ul className="space-y-sp-xl">
        {insights.map((ins, i) => {
          const meta = SEVERITY_ICON[ins.severity];
          return (
            <li key={i} className="flex items-start gap-sp-xl">
              <meta.icon className={cn("mt-0.5 h-4 w-4 shrink-0", meta.cls)} aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink">
                  <span className="sr-only">{meta.word}: </span>
                  {ins.title}
                </p>
                <p className="mt-sp-xs text-sm text-ink-body">{ins.description}</p>
              </div>
            </li>
          );
        })}
      </ul>
    </Surface>
  );
}

function LowDataNotice() {
  return (
    <div className="flex items-start gap-sp-xl rounded-md bg-caution-soft p-sp-wide">
      <Info className="mt-0.5 h-4 w-4 shrink-0 text-caution" aria-hidden="true" />
      <div>
        <p className="text-sm font-medium text-caution">Limited baseline data</p>
        <p className="mt-sp-xs text-sm text-caution">
          A few months of history make the projection more reliable.{" "}
          <Link href="/dashboard/transactions" className="underline">Add transactions</Link>
        </p>
      </div>
    </div>
  );
}

function PendingState({ loading }: { loading: boolean }) {
  return (
    <div className="space-y-sp-wide">
      <div className="grid grid-cols-2 gap-sp-wide lg:grid-cols-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="skeleton h-24 rounded-md" />
        ))}
      </div>
      <div className="skeleton h-48 rounded-md" />
      <div className="skeleton h-72 rounded-md" />
      {!loading && (
        <p className="text-center text-sm text-ink-meta">
          Adjust the scenario on the left to see how this decision plays out.
        </p>
      )}
    </div>
  );
}
