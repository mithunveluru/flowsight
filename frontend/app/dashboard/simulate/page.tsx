"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  CheckCircle2,
  Coins,
  CreditCard,
  Gauge,
  Info,
  PiggyBank,
  Sliders,
  Sparkles,
  Telescope,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import {
  AreaChart,
  Area,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { simulationApi } from "@/features/simulation/api";
import type {
  ConsequenceInsight,
  FinancialBaseline,
  FlexibilityScore,
  FlexibilityTier,
  Projection,
  ScenarioRequest,
  ScenarioType,
  Severity,
  SimulationResult,
  Tradeoff,
} from "@/features/simulation/types";
import {
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import {
  AnimatedNumber,
  AnimatedSwitch,
  FadeIn,
  StaggerContainer,
  StaggerItem,
  motion,
} from "@/components/motion/primitives";
import { cn } from "@/lib/utils";

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

function formatINR(v: number): string {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function formatINRSigned(v: number): string {
  if (v > 0) return `+${formatINR(v)}`;
  if (v < 0) return `−${formatINR(Math.abs(v))}`;
  return formatINR(0);
}

function formatINRCompact(v: number): string {
  const abs = Math.abs(v);
  const sign = v < 0 ? "−" : "";
  if (abs >= 10_000_000) return `${sign}₹${(abs / 10_000_000).toFixed(1)}Cr`;
  if (abs >= 100_000)    return `${sign}₹${(abs / 100_000).toFixed(1)}L`;
  if (abs >= 1_000)      return `${sign}₹${(abs / 1_000).toFixed(0)}K`;
  return `${sign}${formatINR(abs)}`;
}

const TIER_META: Record<FlexibilityTier, { label: string; cls: string; bar: string }> = {
  EXCELLENT:   { label: "Excellent",   cls: "text-emerald-700", bar: "bg-emerald-500" },
  GOOD:        { label: "Good",        cls: "text-blue-700",    bar: "bg-blue-500" },
  FAIR:        { label: "Fair",        cls: "text-slate-700",   bar: "bg-slate-500" },
  TIGHT:       { label: "Tight",       cls: "text-amber-700",   bar: "bg-amber-500" },
  CONSTRAINED: { label: "Constrained", cls: "text-red-700",     bar: "bg-red-500" },
};

const SEVERITY_META: Record<Severity, { dot: string; ring: string; icon: React.ElementType }> = {
  POSITIVE: { dot: "bg-emerald-500", ring: "border-emerald-200", icon: CheckCircle2  },
  NEUTRAL:  { dot: "bg-slate-400",   ring: "border-slate-200",   icon: Info          },
  CAUTION:  { dot: "bg-amber-500",   ring: "border-amber-200",   icon: AlertTriangle },
  WARNING:  { dot: "bg-red-500",     ring: "border-red-200",     icon: AlertTriangle },
};

// -------------------------------------------------------------------------
// Default scenarios for quick exploration
// -------------------------------------------------------------------------

const DEFAULT_SCENARIO: Record<ScenarioType, ScenarioRequest> = {
  ONE_TIME_PURCHASE:  { type: "ONE_TIME_PURCHASE",  name: "New phone",       amount: 60000,  category: "SHOPPING"      },
  RECURRING_EXPENSE:  { type: "RECURRING_EXPENSE",  name: "Streaming bundle", amount: 999,    category: "ENTERTAINMENT", durationMonths: 12 },
  SAVINGS_ADJUSTMENT: { type: "SAVINGS_ADJUSTMENT", name: "Boost savings",   amount: 10000,  durationMonths: 12 },
  LOAN_EMI:           { type: "LOAN_EMI",           name: "Vehicle loan",    amount: 1000000, annualInterestRate: 9.5, tenureMonths: 36 },
};

const SCENARIO_TAB_META: Record<ScenarioType, { label: string; icon: React.ElementType }> = {
  ONE_TIME_PURCHASE:  { label: "Buy something",   icon: Coins },
  RECURRING_EXPENSE:  { label: "Add a subscription", icon: CreditCard },
  SAVINGS_ADJUSTMENT: { label: "Adjust savings",     icon: PiggyBank },
  LOAN_EMI:           { label: "Take a loan",        icon: TrendingDown },
};

// -------------------------------------------------------------------------
// Page
// -------------------------------------------------------------------------

export default function SimulatePage() {
  const [scenario, setScenario] = useState<ScenarioRequest>(DEFAULT_SCENARIO.ONE_TIME_PURCHASE);
  const [result,   setResult]   = useState<SimulationResult | null>(null);
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState<string | null>(null);

  // Debounced auto-simulation — keeps the page feeling alive without spamming the API
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
  }, [scenario]);

  const handleTypeChange = useCallback((type: ScenarioType) => {
    setScenario(DEFAULT_SCENARIO[type]);
  }, []);

  return (
    <div className="space-y-10">
      {/* Page header */}
      <FadeIn>
        <header className="flex items-start justify-between gap-6">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">Decision simulator</h1>
            <p className="mt-1.5 text-sm text-muted-foreground max-w-2xl">
              Test a financial decision against your real baseline. See the cashflow impact, flexibility shift, and long-term consequences before you commit.
            </p>
          </div>
          <Sliders className="h-5 w-5 text-muted-foreground mt-1.5" strokeWidth={1.5} />
        </header>
      </FadeIn>

      {/* Two-column on desktop: scenario builder (sticky) + results */}
      <div className="grid gap-8 lg:grid-cols-[minmax(0,360px)_1fr]">
        {/* Scenario builder */}
        <FadeIn delay={0.05}>
          <aside className="lg:sticky lg:top-6 lg:self-start space-y-6">
            <ScenarioBuilder
              scenario={scenario}
              onChange={setScenario}
              onTypeChange={handleTypeChange}
            />
          </aside>
        </FadeIn>

        {/* Results — stagger the stack into view */}
        <section className="space-y-8">
          {error && (
            <FadeIn>
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            </FadeIn>
          )}

          {result ? (
            <StaggerContainer className="space-y-8" stagger={0.06}>
              <StaggerItem><ImpactSummary result={result} /></StaggerItem>
              <StaggerItem><FlexibilityCard flexibility={result.flexibility} baseline={result.baseline} /></StaggerItem>
              <StaggerItem><ProjectionChart projection={result.projection} /></StaggerItem>
              <StaggerItem><TradeoffAnalysis tradeoffs={result.tradeoffs} /></StaggerItem>
              <StaggerItem><ConsequenceInsights insights={result.insights} /></StaggerItem>
              {!result.baseline.hasEnoughData && (
                <StaggerItem><LowDataNotice /></StaggerItem>
              )}
            </StaggerContainer>
          ) : (
            <FadeIn delay={0.1}><LoadingState loading={loading} /></FadeIn>
          )}
        </section>
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------
// Scenario Builder
// -------------------------------------------------------------------------

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
    <div className="rounded-xl border bg-card p-5" style={{ borderColor: "hsl(var(--border))" }}>
      {/* Scenario type tabs */}
      <p className="stat-label mb-2">Scenario type</p>
      <div className="grid grid-cols-2 gap-1.5">
        {(Object.keys(SCENARIO_TAB_META) as ScenarioType[]).map((t) => {
          const meta = SCENARIO_TAB_META[t];
          const Icon = meta.icon;
          const active = scenario.type === t;
          return (
            <button
              key={t}
              onClick={() => onTypeChange(t)}
              className={cn(
                "flex flex-col items-start gap-1.5 rounded-lg border px-3 py-2.5 text-left transition-colors",
                active
                  ? "border-foreground bg-muted/50"
                  : "border-transparent bg-muted/40 hover:bg-muted"
              )}
            >
              <Icon className="h-3.5 w-3.5 text-foreground/70" strokeWidth={1.75} />
              <span className="text-xs font-medium text-foreground">{meta.label}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-6 space-y-4">
        {/* Name */}
        <Field label="Name">
          <input
            type="text"
            value={scenario.name ?? ""}
            onChange={(e) => update({ name: e.target.value })}
            placeholder="e.g. New phone"
            className="input-base"
          />
        </Field>

        {/* Type-specific fields */}
        {scenario.type === "ONE_TIME_PURCHASE" && (
          <>
            <Field label="Amount">
              <AmountInput
                value={scenario.amount}
                onChange={(v) => update({ amount: v })}
              />
            </Field>
            <Field label="Category (optional)">
              <CategorySelect
                value={scenario.category}
                onChange={(v) => update({ category: v })}
              />
            </Field>
          </>
        )}

        {scenario.type === "RECURRING_EXPENSE" && (
          <>
            <Field label="Monthly amount">
              <AmountInput value={scenario.amount} onChange={(v) => update({ amount: v })} />
            </Field>
            <Field label="Duration (months)">
              <NumberInput
                value={scenario.durationMonths ?? 12}
                onChange={(v) => update({ durationMonths: v })}
                min={1}
                max={120}
              />
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
                <SelectTrigger className="h-10"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INCREASE">Increase savings</SelectItem>
                  <SelectItem value="DECREASE">Decrease savings</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Monthly amount">
              <AmountInput
                value={Math.abs(scenario.amount)}
                onChange={(v) => update({ amount: scenario.amount < 0 ? -v : v })}
              />
            </Field>
            <Field label="Duration (months)">
              <NumberInput
                value={scenario.durationMonths ?? 12}
                onChange={(v) => update({ durationMonths: v })}
                min={1}
                max={120}
              />
            </Field>
          </>
        )}

        {scenario.type === "LOAN_EMI" && (
          <>
            <Field label="Principal">
              <AmountInput value={scenario.amount} onChange={(v) => update({ amount: v })} />
            </Field>
            <Field label="Annual interest rate (%)">
              <NumberInput
                value={scenario.annualInterestRate ?? 9.5}
                onChange={(v) => update({ annualInterestRate: v })}
                min={0}
                max={36}
                step={0.1}
              />
            </Field>
            <Field label="Tenure (months)">
              <NumberInput
                value={scenario.tenureMonths ?? 36}
                onChange={(v) => update({ tenureMonths: v })}
                min={1}
                max={360}
              />
            </Field>
          </>
        )}
      </div>

      <p className="mt-5 text-[11px] text-muted-foreground leading-relaxed">
        The projection updates as you adjust values. It uses your last 3 months of transactions and active recurring patterns as the baseline.
      </p>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-muted-foreground mb-1.5">{label}</label>
      {children}
    </div>
  );
}

function AmountInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-muted-foreground">INR</span>
      <input
        type="number"
        value={value || ""}
        onChange={(e) => onChange(Number(e.target.value) || 0)}
        min={0}
        step={100}
        placeholder="0"
        className="input-base flex-1"
      />
    </div>
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
    <input
      type="number"
      value={value || ""}
      onChange={(e) => onChange(Number(e.target.value) || 0)}
      min={min}
      max={max}
      step={step}
      className="input-base"
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
      <SelectTrigger className="h-10"><SelectValue placeholder="Auto-detect" /></SelectTrigger>
      <SelectContent>
        <SelectItem value="__none__">No category</SelectItem>
        {CATEGORY_OPTIONS.map((opt) => (
          <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

// -------------------------------------------------------------------------
// Impact summary
// -------------------------------------------------------------------------

function ImpactSummary({ result }: { result: SimulationResult }) {
  const positive = result.monthlyImpact >= 0;

  const cards = [
    {
      label: "Monthly cashflow impact",
      value: result.scenario.type === "ONE_TIME_PURCHASE"
        ? formatINR(0)
        : formatINRSigned(result.monthlyImpact),
      hint: result.scenario.type === "ONE_TIME_PURCHASE"
        ? "One-time outlay"
        : positive ? "Net inflow" : "Net outflow",
      icon: positive ? TrendingUp : TrendingDown,
      accent: positive ? "text-emerald-700" : "text-red-700",
    },
    {
      label: "Yearly impact",
      value: formatINRCompact(result.yearlyImpact),
      hint: "Total over 12 months",
      icon: Coins,
      accent: result.yearlyImpact >= 0 ? "text-emerald-700" : "text-red-700",
    },
    {
      label: "5-year cumulative",
      value: formatINRCompact(result.fiveYearImpact),
      hint: "Total over 60 months",
      icon: Coins,
      accent: result.fiveYearImpact >= 0 ? "text-emerald-700" : "text-red-700",
    },
    {
      label: "10-year opportunity cost",
      value: formatINRCompact(result.tenYearOpportunityCost),
      hint: "If invested at 8% return",
      icon: Telescope,
      accent: "text-foreground",
    },
  ];

  return (
    <div
      className="grid grid-cols-2 lg:grid-cols-4 divide-x divide-y lg:divide-y-0 overflow-hidden rounded-xl border bg-card"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      {cards.map((c) => (
        <div key={c.label} className="px-5 py-4">
          <div className="flex items-center gap-1.5 mb-1.5">
            <c.icon className="h-3 w-3 text-muted-foreground" strokeWidth={2} />
            <p className="stat-label">{c.label}</p>
          </div>
          <p className={cn("stat-value text-2xl tabular-nums", c.accent)}>{c.value}</p>
          <p className="mt-0.5 text-[11px] text-muted-foreground">{c.hint}</p>
        </div>
      ))}
    </div>
  );
}

// -------------------------------------------------------------------------
// Flexibility card
// -------------------------------------------------------------------------

function FlexibilityCard({
  flexibility,
  baseline,
}: {
  flexibility: FlexibilityScore;
  baseline: FinancialBaseline;
}) {
  const currentMeta   = TIER_META[flexibility.currentTier];
  const projectedMeta = TIER_META[flexibility.projectedTier];
  const delta = flexibility.deltaPercent;

  return (
    <div className="rounded-xl border bg-card p-6" style={{ borderColor: "hsl(var(--border))" }}>
      <div className="flex items-start gap-3 mb-5">
        <Gauge className="h-4 w-4 text-muted-foreground mt-0.5" strokeWidth={1.75} />
        <div>
          <p className="text-sm font-semibold text-foreground">Financial flexibility</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            Room to absorb surprises and pursue what comes next.
          </p>
        </div>
      </div>

      <div className="grid items-center gap-6 sm:grid-cols-[1fr_auto_1fr]">
        {/* Current */}
        <div className="space-y-2">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Current</p>
          <p className="stat-value text-3xl tabular-nums">
            <AnimatedNumber value={flexibility.currentScore} duration={0.6} />
            <span className="text-base text-muted-foreground">/100</span>
          </p>
          <p className={cn("text-sm font-medium", currentMeta.cls)}>{currentMeta.label}</p>
          <div className="h-1.5 rounded-full bg-muted overflow-hidden">
            <motion.div
              className={cn("h-full", currentMeta.bar)}
              initial={{ width: 0 }}
              animate={{ width: `${flexibility.currentScore}%` }}
              transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
            />
          </div>
        </div>

        {/* Arrow + delta — switches its color/sign smoothly with content */}
        <AnimatedSwitch viewKey={String(Math.sign(delta)) + Math.abs(delta).toFixed(1)} className="text-center hidden sm:block">
          <div className={cn(
            "inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium",
            delta >= 0
              ? "border-emerald-200 bg-emerald-50 text-emerald-700"
              : "border-red-200    bg-red-50    text-red-700"
          )}>
            {delta >= 0 ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />}
            {Math.abs(delta).toFixed(1)}%
          </div>
        </AnimatedSwitch>

        {/* Projected */}
        <div className="space-y-2">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">After this decision</p>
          <p className="stat-value text-3xl tabular-nums">
            <AnimatedNumber value={flexibility.projectedScore} duration={0.6} />
            <span className="text-base text-muted-foreground">/100</span>
          </p>
          <p className={cn("text-sm font-medium", projectedMeta.cls)}>{projectedMeta.label}</p>
          <div className="h-1.5 rounded-full bg-muted overflow-hidden">
            <motion.div
              className={cn("h-full", projectedMeta.bar)}
              initial={{ width: 0 }}
              animate={{ width: `${flexibility.projectedScore}%` }}
              transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
            />
          </div>
        </div>
      </div>

      <p className="mt-5 text-xs text-muted-foreground leading-relaxed">
        {flexibility.explanation}
      </p>

      {/* Baseline footnote */}
      {baseline.hasEnoughData && (
        <div className="mt-4 grid gap-2 text-[11px] text-muted-foreground sm:grid-cols-3">
          <span>Income: <span className="font-medium text-foreground tabular-nums">{formatINR(baseline.monthlyIncome)}/mo</span></span>
          <span>Spend: <span className="font-medium text-foreground tabular-nums">{formatINR(baseline.monthlySpend)}/mo</span></span>
          <span>Recurring: <span className="font-medium text-foreground tabular-nums">{formatINR(baseline.monthlyRecurring)}/mo</span></span>
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------------------------
// Projection chart — 12-month before vs after
// -------------------------------------------------------------------------

function ProjectionChart({ projection }: { projection: Projection }) {
  const data = useMemo(() => {
    return projection.before.map((p, i) => ({
      month: `M${p.month}`,
      before: p.cumulativeSavings,
      after:  projection.after[i]?.cumulativeSavings ?? 0,
    }));
  }, [projection]);

  return (
    <div className="rounded-xl border bg-card p-6" style={{ borderColor: "hsl(var(--border))" }}>
      <div className="flex items-start gap-3 mb-5">
        <Telescope className="h-4 w-4 text-muted-foreground mt-0.5" strokeWidth={1.75} />
        <div>
          <p className="text-sm font-semibold text-foreground">12-month projection</p>
          <p className="text-xs text-muted-foreground mt-0.5">Cumulative savings position — current trajectory vs simulated.</p>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={280}>
        <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="grad-before" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%"  stopColor="hsl(220 9% 46%)" stopOpacity={0.12} />
              <stop offset="95%" stopColor="hsl(220 9% 46%)" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="grad-after" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%"  stopColor="hsl(234 50% 53%)" stopOpacity={0.18} />
              <stop offset="95%" stopColor="hsl(234 50% 53%)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
          <XAxis dataKey="month" tick={{ fontSize: 11, fill: "#94a3b8" }} axisLine={false} tickLine={false} />
          <YAxis
            tick={{ fontSize: 11, fill: "#94a3b8" }}
            axisLine={false} tickLine={false}
            tickFormatter={(v: number) => formatINRCompact(v)}
            width={56}
          />
          <Tooltip
            formatter={(v: unknown, name: unknown) => {
              const lbl = name === "before" ? "Current trajectory" : "With this decision";
              return `${lbl}: ${formatINR(Number(v))}`;
            }}
            contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #e2e8f0" }}
            labelStyle={{ color: "#475569", fontWeight: 600 }}
          />
          <Legend
            iconType="circle" iconSize={8}
            formatter={(v) => v === "before" ? "Current trajectory" : "With this decision"}
            wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
          />
          <Area type="monotone" dataKey="before" stroke="hsl(220 9% 46%)" strokeWidth={2} fill="url(#grad-before)" dot={false} />
          <Area type="monotone" dataKey="after"  stroke="hsl(234 50% 53%)" strokeWidth={2} fill="url(#grad-after)"  dot={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// -------------------------------------------------------------------------
// Tradeoff analysis
// -------------------------------------------------------------------------

function TradeoffAnalysis({ tradeoffs }: { tradeoffs: Tradeoff[] }) {
  if (tradeoffs.length === 0) return null;

  return (
    <div className="rounded-xl border bg-card p-6" style={{ borderColor: "hsl(var(--border))" }}>
      <div className="flex items-start gap-3 mb-5">
        <Sparkles className="h-4 w-4 text-muted-foreground mt-0.5" strokeWidth={1.75} />
        <div>
          <p className="text-sm font-semibold text-foreground">Key tradeoffs</p>
          <p className="text-xs text-muted-foreground mt-0.5">The concrete numbers behind this decision.</p>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        {tradeoffs.map((t, i) => (
          <div key={i} className="rounded-lg bg-muted/40 px-4 py-3.5">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{t.label}</p>
            <p className="mt-1.5 text-base font-semibold tabular-nums text-foreground">{t.value}</p>
            <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground">{t.description}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------
// Consequence insights
// -------------------------------------------------------------------------

function ConsequenceInsights({ insights }: { insights: ConsequenceInsight[] }) {
  if (insights.length === 0) return null;

  return (
    <div className="rounded-xl border bg-card p-6" style={{ borderColor: "hsl(var(--border))" }}>
      <div className="flex items-start gap-3 mb-5">
        <ArrowUpRight className="h-4 w-4 text-muted-foreground mt-0.5" strokeWidth={1.75} />
        <div>
          <p className="text-sm font-semibold text-foreground">Consequences</p>
          <p className="text-xs text-muted-foreground mt-0.5">Plain-language read-outs grounded in your data.</p>
        </div>
      </div>

      <div className="space-y-3">
        {insights.map((ins, i) => (
          <InsightRow key={i} insight={ins} />
        ))}
      </div>
    </div>
  );
}

function InsightRow({ insight }: { insight: ConsequenceInsight }) {
  const meta = SEVERITY_META[insight.severity];
  return (
    <div className="flex items-start gap-3">
      <span className={cn("mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full", meta.dot)} />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground">{insight.title}</p>
        <p className="mt-0.5 text-xs text-muted-foreground leading-relaxed">{insight.description}</p>
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------
// Low-data + loading states
// -------------------------------------------------------------------------

function LowDataNotice() {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
      <Info className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
      <div>
        <p className="text-sm font-medium text-amber-900">Limited baseline data</p>
        <p className="mt-0.5 text-xs text-amber-700">
          A few months of history make the projection more reliable.{" "}
          <Link href="/dashboard/transactions" className="underline">Add transactions →</Link>
        </p>
      </div>
    </div>
  );
}

function LoadingState({ loading }: { loading: boolean }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-px overflow-hidden rounded-xl border bg-card"
           style={{ borderColor: "hsl(var(--border))" }}>
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="skeleton h-24" />
        ))}
      </div>
      <div className="skeleton h-48 rounded-xl" />
      <div className="skeleton h-72 rounded-xl" />
      {!loading && (
        <p className="text-center text-xs text-muted-foreground">
          Adjust the scenario on the left to see how this decision plays out.
        </p>
      )}
    </div>
  );
}
