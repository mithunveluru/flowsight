import type { Evidence } from "@/components/ui/evidence-drawer";
import { humanizeKey, rupees, rupeesCompact } from "../insights/format.ts";
import type { FanPoint, GoalOdds, MonteCarloEvidence, MonteCarloResult, Percentiles, SimulationAssumptions } from "./types";

export interface FanRow {
  month: number;
  label: string;
  p10: number;
  p50: number;
  p90: number;
  // stacked area pair: invisible floor, then the visible p10-p90 band
  floor: number;
  band: number;
  baseP50: number | null;
}

/** One row per month for the recharts fan; `baseline` supplies the comparison median line. */
export function fanRows(fan: FanPoint[], baseline: FanPoint[] = []): FanRow[] {
  const base = new Map(baseline.map((b) => [b.month, b.p50]));
  return [...fan]
    .sort((a, b) => a.month - b.month)
    .map((f) => ({
      month: f.month,
      label: `M${f.month}`,
      p10: f.p10,
      p50: f.p50,
      p90: f.p90,
      floor: f.p10,
      band: Math.max(0, f.p90 - f.p10),
      baseP50: base.get(f.month) ?? null,
    }));
}

/** Words for screen readers: the last point of the fan. */
export function fanSummary(fan: FanPoint[]): string {
  const last = [...fan].sort((a, b) => a.month - b.month).at(-1);
  if (!last) return "No simulated paths.";
  return `After ${last.month} months, cumulative savings are most likely between ${rupees(last.p10)} and ${rupees(last.p90)}, with a median of ${rupees(last.p50)}.`;
}

export function clampReturn(raw: string | number): number | null {
  if (raw === "" || raw == null) return null;
  const n = Number(raw);
  if (!Number.isFinite(n)) return null;
  return Math.min(30, Math.max(0, n));
}

export const percent = (p: number): string => `${Math.round(p * 100)}%`;

export function reachLabel(months: number | null): string {
  return months == null ? "not within the horizon" : `month ${months}`;
}

export function goalOddsText(g: GoalOdds): { headline: string; baseline: string; reach: string } {
  return {
    headline: `${percent(g.probabilityByTargetDate)} chance of reaching ${g.goalName} by ${g.targetDate}`,
    baseline: `${percent(g.baselineProbabilityByTargetDate)} without this decision`,
    reach: `Typical month to reach it: ${reachLabel(g.reachMonth.p50)} (fast case ${reachLabel(g.reachMonth.p10)}, slow case ${reachLabel(g.reachMonth.p90)})`,
  };
}

export function assumptionText(a: SimulationAssumptions): string {
  return `${a.annualReturnPercent}% a year (${a.annualReturnSource === "USER" ? "your setting" : "default"})`;
}

const VOLATILITY: Record<MonteCarloEvidence["volatilitySource"], string> = {
  OWN_HISTORY: "your own history",
  SHRUNK_TO_PRIOR: "your history blended with a typical spread",
  PRIOR: "a typical spread (too little history of your own)",
};

export function monteCarloEvidence(mc: MonteCarloResult, assumptions?: SimulationAssumptions): Evidence {
  const e = mc.evidence;
  const inputs: NonNullable<Evidence["inputs"]> = [
    { label: "Simulated paths", value: e.paths.toLocaleString("en-IN") },
    { label: "Months simulated", value: String(e.horizonMonths) },
    { label: "Baseline monthly net", value: rupees(e.baseMonthlyNet) },
    { label: "Monthly spread used", value: rupees(e.monthlySd) },
    { label: "Spread taken from", value: VOLATILITY[e.volatilitySource] },
    { label: "Weight on your own history", value: `${Math.round(e.ownWeight * 100)}%` },
    { label: "Block length", value: `${e.blockLength} months` },
    { label: "Random seed", value: String(e.seed) },
  ];
  if (e.ownMonthlySd != null) inputs.push({ label: "Your own monthly spread", value: rupees(e.ownMonthlySd) });
  if (assumptions) inputs.push({ label: "Return assumption", value: assumptionText(assumptions) });
  return {
    method: `${humanizeKey(e.method)}. ${e.note}`,
    inputs,
    window: e.window ? { from: e.window[0], to: e.window[1] } : undefined,
    caveats: [
      "The band shows where 80% of simulated paths end up; the line is the median.",
      "The return assumption only feeds opportunity-cost figures, not these paths.",
    ],
  };
}

export function flexibilityEvidence(now: Percentiles, after: Percentiles, mc: MonteCarloResult): Evidence {
  const base = monteCarloEvidence(mc);
  return {
    ...base,
    inputs: [
      { label: "Now: low / median / high", value: `${now.p10} / ${now.p50} / ${now.p90}` },
      { label: "After: low / median / high", value: `${after.p10} / ${after.p50} / ${after.p90}` },
      ...(base.inputs ?? []),
    ],
  };
}

export { rupeesCompact };
