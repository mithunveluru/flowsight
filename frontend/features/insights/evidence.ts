import type { Evidence } from "@/components/ui/evidence-drawer";
import type { ForecastEvidence } from "@/features/analytics/types";
import type { LeakEvidence, LeakInsight } from "@/features/leaks/types";
import type { RecurringPattern } from "@/features/recurring/types";
import { formatDay, humanizeKey, monthEnd, monthStart, rupees } from "./format.ts";
import type { Recommendation } from "./types";

type Input = NonNullable<Evidence["inputs"]>[number];

const LEAK_METHODS: Record<string, string> = {
  "rule:last-two-vs-earlier":
    "Rule: the last two payments to a recurring merchant average at least 10% more than the earlier ones.",
  "rule:fixed-thresholds":
    "Rule: eight or more small purchases at one merchant in the last three months, above a fixed monthly total.",
  "rule:fee-keywords": "Rule: transactions whose description mentions a fee, penalty or charge.",
  "change-point:median-mad":
    "Change-point test: later payments sit well above the typical earlier price, measured against how much that price normally varies.",
  "duplicate:parallel-charges": "One service charged more than once in the same period.",
  "duplicate:same-merchant-key": "Two recurring patterns found for the same merchant.",
  "duplicate:category-overlap": "Several subscriptions in the same category.",
  "drain:personal-baseline":
    "Frequent small purchases at one merchant, judged against your own usual ticket size and monthly spend.",
  "drain:global-thresholds":
    "Frequent small purchases at one merchant, judged against fixed thresholds because your history is short.",
};

const LEVEL_FROM_LABEL = { HIGH: "high", MEDIUM: "medium", LOW: "low" } as const;

function formatValue(v: string | number | boolean | null | undefined): string {
  if (v == null) return "none";
  if (typeof v === "number") return v.toLocaleString("en-IN", { maximumFractionDigits: 2 });
  if (typeof v === "boolean") return v ? "yes" : "no";
  return /^\d{4}-\d{2}-\d{2}$/.test(v) ? formatDay(v) : v;
}

export function inputsFromMap(map: Record<string, string | number | boolean | null> | null | undefined): Input[] {
  return Object.entries(map ?? {}).map(([k, v]) => ({ label: humanizeKey(k), value: formatValue(v) }));
}

function leakDetector(ev: LeakEvidence | null | undefined) {
  if (!ev) return null;
  return {
    method: LEAK_METHODS[ev.method] ?? `Method: ${ev.method}`,
    source: ev.source === "MODEL" ? ("model" as const) : ("rules" as const),
    window: ev.windowStart && ev.windowEnd ? { from: ev.windowStart, to: ev.windowEnd } : undefined,
    confidence: ev.confidence,
    inputs: inputsFromMap(ev.inputs),
  };
}

/** What the API returned about a leak. Nothing is added when the API sent no evidence. */
export function leakEvidence(leak: LeakInsight, computedAt?: Date): Evidence {
  const d = leakDetector(leak.evidence);
  const items: Input[] = leak.items.map((i) => ({
    label: i.merchant,
    value: `${rupees(i.amount)}/mo · ${i.detail}`,
    href: "/dashboard/transactions",
  }));
  const detector = d?.inputs ?? [];
  return {
    method: d?.method ?? leak.description,
    inputs: [...detector, ...items],
    inputCount: detector.length + leak.affectedItemsCount,
    source: d?.source,
    window: d?.window,
    confidence: d?.confidence ?? undefined,
    computedAt,
    caveats: leak.recommendation ? [leak.recommendation] : undefined,
  };
}

const RECURRING_LEVEL = { HIGH: "high", MEDIUM: "medium", POSSIBLE: "low" } as const;

export function recurringEvidence(p: RecurringPattern): Evidence {
  const model = p.scoredBy === "MODEL";
  const range =
    p.nextExpectedLow && p.nextExpectedHigh ? `${formatDay(p.nextExpectedLow)} to ${formatDay(p.nextExpectedHigh)}` : null;
  const inputs: Input[] = [
    { label: "Charges seen", value: String(p.occurrenceCount) },
    { label: "Last seen", value: formatDay(p.lastSeenDate) },
    { label: "Repeats", value: p.periodLabel },
    { label: "Typical amount", value: rupees(p.estimatedAmount) },
  ];
  if (p.nextExpectedDate) inputs.push({ label: "Next expected", value: formatDay(p.nextExpectedDate) });
  if (range) inputs.push({ label: "Likely window", value: range });
  if (p.chargesPerPeriod && p.chargesPerPeriod > 1) {
    inputs.push({ label: "Charges per period", value: String(p.chargesPerPeriod) });
  }
  return {
    method: model
      ? "A model scored how regularly this merchant's past charges repeat, from their spacing and amounts. The next date is a range taken from the gaps between past charges."
      : "Rule: charges to this merchant repeat at a regular interval.",
    inputs,
    source: model ? "model" : "rules",
    modelVersion: p.modelVersion ?? undefined,
    confidence: model ? p.probability ?? undefined : undefined,
    confidenceLevel: model ? undefined : RECURRING_LEVEL[p.confidenceTier],
    caveats: p.isUserConfirmed ? ["You confirmed this one."] : undefined,
  };
}

const SOURCE_LABEL = { LEAK: "Money drains detector", BEHAVIOR: "Spending habits analysis", GOAL: "Your goals" } as const;

export function recommendationEvidence(r: Recommendation): Evidence {
  const inputs: Input[] = r.evidence.map((e) => ({ label: e }));
  if (r.source) inputs.unshift({ label: "Comes from", value: SOURCE_LABEL[r.source] });
  if (r.acceptanceRate != null) {
    inputs.push({ label: "People who accepted this kind", value: `${Math.round(r.acceptanceRate * 100)}%` });
  }
  return {
    method: r.description,
    inputs,
    confidenceLevel: LEVEL_FROM_LABEL[r.confidence],
    caveats: r.rankingReason ? [r.rankingReason] : undefined,
  };
}

export function forecastEvidence(ev: ForecastEvidence, asOf?: string): Evidence {
  const inputs: Input[] = [
    { label: "Complete months used", value: String(ev.dataMonths) },
    { label: "Backtest points", value: String(ev.backtestPoints) },
  ];
  if (ev.backtestMae != null) inputs.push({ label: "Backtest mean error", value: rupees(ev.backtestMae) });
  if (ev.backtestWape != null) inputs.push({ label: "Backtest error share", value: `${(ev.backtestWape * 100).toFixed(1)}%` });
  inputs.push({ label: "Weight on your own errors", value: `${Math.round(ev.ownWeight * 100)}%` });
  inputs.push({ label: "Range basis", value: humanizeKey(ev.intervalSource.toLowerCase()) });
  return {
    method: `${ev.method}. ${ev.note}`,
    inputs,
    window: ev.window ? { from: monthStart(ev.window[0]), to: monthEnd(ev.window[1]) } : undefined,
    source: ev.source === "ml-service" ? "model" : "rules",
    modelVersion: ev.modelVersion ?? undefined,
    computedAt: asOf ? new Date(`${asOf}T00:00:00`) : undefined,
    caveats: ev.lowHistory ? ["History is short, so these ranges are wide."] : undefined,
  };
}
