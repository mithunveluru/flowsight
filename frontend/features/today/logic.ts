// Pure decisions behind the Today page and the first-run flow. Type-only
// imports keep this file loadable by the node test runner.
import type { MonthlyTrendPoint } from "@/features/analytics/types";
import type { Budget } from "@/features/budgets/types";
import type { Goal } from "@/features/goals/types";
import type { LeakInsight } from "@/features/leaks/types";
import type { Receipt } from "@/features/receipts/types";
import type { RecurringPattern } from "@/features/recurring/types";
import type { Transaction } from "@/features/transactions/types";

export const PARTIAL_HISTORY_DAYS = 30;
// A gap this long between two loads of Today starts a new "visit".
export const VISIT_GAP_MS = 30 * 60 * 1000;
export const ATTENTION_LIMIT = 5;
export const DRAIN_LIMIT = 3;

const DAY_MS = 24 * 60 * 60 * 1000;

export const formatRupees = (v: number) => `₹${Math.round(v).toLocaleString("en-IN")}`;

const plural = (n: number, one: string, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;

export function isDraftReceipt(r: Pick<Receipt, "status" | "transaction">): boolean {
  return r.status === "COMPLETED" && !r.transaction;
}

export function countDrafts(receipts: Pick<Receipt, "status" | "transaction">[]): number {
  return receipts.filter(isDraftReceipt).length;
}

/** First run: send the user to the welcome screen only when we know the account is empty. */
export function shouldShowWelcome(transactionCount: number | undefined, dismissed: boolean): boolean {
  return transactionCount === 0 && !dismissed;
}

/** Days of history from the earliest transaction to today, counting both ends. */
export function historyDays(earliest: string | null | undefined, today: Date): number | null {
  if (!earliest) return null;
  const [y, m, d] = earliest.split("-").map(Number);
  const start = Date.UTC(y, m - 1, d);
  const end = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.max(1, Math.floor((end - start) / DAY_MS) + 1);
}

export function monthKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * Average spend over the last (up to) three complete months before
 * `currentMonth`. The month history starts in counts only if it starts on the 1st.
 */
export function typicalMonthlySpend(
  points: Pick<MonthlyTrendPoint, "month" | "label" | "spend" | "projected">[],
  currentMonth: string,
  earliest?: string | null
): { average: number; months: { label: string; spend: number }[] } | null {
  const partialFirst = earliest && !earliest.endsWith("-01") ? earliest.slice(0, 7) : null;
  const complete = points
    .filter((p) => !p.projected && p.month < currentMonth && p.month !== partialFirst && p.spend > 0)
    .slice(-3);
  if (complete.length === 0) return null;
  const average = complete.reduce((sum, p) => sum + p.spend, 0) / complete.length;
  return { average, months: complete.map((p) => ({ label: p.label, spend: p.spend })) };
}


export interface Snapshot {
  at: number;
  recurringIds: string[];
  creepMerchants: string[];
  // budget id -> highest threshold reached (0, 80 or 100)
  budgetLevels: Record<string, number>;
}

export interface VisitState {
  baseline: Snapshot | null;
  latest: Snapshot;
}

export function budgetLevel(percentUsed: number): number {
  if (percentUsed >= 100) return 100;
  if (percentUsed >= 80) return 80;
  return 0;
}

const creepItems = (leaks: Pick<LeakInsight, "type" | "items">[]) =>
  leaks.filter((l) => l.type === "SUBSCRIPTION_CREEP").flatMap((l) => l.items);

export function takeSnapshot(input: {
  now: number;
  recurring: Pick<RecurringPattern, "id">[];
  leaks: Pick<LeakInsight, "type" | "items">[];
  budgets: Pick<Budget, "id" | "percentUsed">[];
}): Snapshot {
  return {
    at: input.now,
    recurringIds: input.recurring.map((r) => r.id),
    creepMerchants: creepItems(input.leaks).map((i) => i.merchant),
    budgetLevels: Object.fromEntries(input.budgets.map((b) => [b.id, budgetLevel(b.percentUsed)])),
  };
}

/**
 * Moves the comparison point forward only when a new visit starts, so
 * reloading Today within one visit keeps showing the same changes.
 */
export function advanceVisit(stored: VisitState | null, current: Snapshot, gapMs = VISIT_GAP_MS): VisitState {
  if (!stored) return { baseline: null, latest: current };
  const newVisit = current.at - stored.latest.at > gapMs;
  return { baseline: newVisit ? stored.latest : stored.baseline, latest: current };
}

export interface Change {
  id: string;
  label: string;
  detail?: string;
  href: string;
}

export function changesSince(
  baseline: Snapshot,
  data: {
    transactions: Pick<Transaction, "createdAt" | "amount" | "type">[];
    // true when `transactions` is a capped page and every row was new
    transactionsCapped?: boolean;
    receipts: Pick<Receipt, "transaction">[];
    recurring: Pick<RecurringPattern, "id" | "merchant">[];
    leaks: Pick<LeakInsight, "type" | "items">[];
    budgets: Pick<Budget, "id" | "percentUsed" | "categoryDisplayName">[];
  }
): Change[] {
  const after = (iso: string | null | undefined) => !!iso && Date.parse(iso) > baseline.at;
  const changes: Change[] = [];

  const fresh = data.transactions.filter((t) => after(t.createdAt));
  if (fresh.length > 0) {
    const spent = fresh.filter((t) => t.type === "DEBIT").reduce((s, t) => s + Number(t.amount), 0);
    const capped = data.transactionsCapped && fresh.length === data.transactions.length;
    changes.push({
      id: "transactions",
      label: capped ? `${fresh.length}+ new transactions` : `${plural(fresh.length, "new transaction")}`,
      detail: spent > 0 ? `${formatRupees(spent)} spent` : undefined,
      href: "/dashboard/transactions",
    });
  }

  const confirmed = data.receipts.filter((r) => after(r.transaction?.createdAt));
  if (confirmed.length > 0) {
    changes.push({ id: "receipts", label: `${plural(confirmed.length, "receipt")} confirmed`, href: "/dashboard/receipts" });
  }

  const known = new Set(baseline.recurringIds);
  const newSubs = data.recurring.filter((r) => !known.has(r.id));
  if (newSubs.length > 0) {
    changes.push({
      id: "subscriptions",
      label: `${plural(newSubs.length, "new subscription")} detected`,
      detail: newSubs.slice(0, 3).map((r) => r.merchant).join(", "),
      href: "/dashboard/subscriptions",
    });
  }

  const knownCreep = new Set(baseline.creepMerchants);
  for (const item of creepItems(data.leaks).filter((i) => !knownCreep.has(i.merchant))) {
    changes.push({ id: `creep:${item.merchant}`, label: `Price increase: ${item.merchant}`, detail: item.detail, href: "/dashboard/drains" });
  }

  for (const b of data.budgets) {
    const level = budgetLevel(b.percentUsed);
    if (level > (baseline.budgetLevels[b.id] ?? 0)) {
      changes.push({
        id: `budget:${b.id}`,
        label: `${b.categoryDisplayName} budget ${level === 100 ? "is used up" : "passed 80%"}`,
        detail: `${Math.round(b.percentUsed)}% used`,
        href: "/dashboard/budgets",
      });
    }
  }

  return changes;
}


export type AttentionSeverity = "HIGH" | "MEDIUM" | "LOW";

export interface AttentionItem {
  id: string;
  severity: AttentionSeverity;
  // rupee impact used to break severity ties
  impact: number;
  title: string;
  description: string;
  action: { label: string; href: string };
}

const SEVERITY_RANK: Record<AttentionSeverity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

export function rankAttention(items: AttentionItem[], limit = ATTENTION_LIMIT): AttentionItem[] {
  return [...items]
    .sort((a, b) => SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity] || b.impact - a.impact)
    .slice(0, limit);
}

export function attentionItems(data: {
  receipts?: Pick<Receipt, "status" | "transaction" | "extraction">[];
  uncategorized?: number;
  budgets?: Pick<Budget, "id" | "percentUsed" | "categoryDisplayName" | "currentSpend" | "monthlyLimit">[];
  recurring?: Pick<RecurringPattern, "id" | "merchant" | "status" | "estimatedAmount">[];
  goals?: Pick<Goal, "id" | "name" | "status" | "paceStatus" | "remaining">[];
}): AttentionItem[] {
  const items: AttentionItem[] = [];

  const drafts = (data.receipts ?? []).filter(isDraftReceipt);
  if (drafts.length > 0) {
    items.push({
      id: "drafts",
      severity: "MEDIUM",
      impact: drafts.reduce((s, r) => s + (r.extraction?.amount ?? 0), 0),
      title: `${plural(drafts.length, "receipt")} waiting for review`,
      description: "Nothing is saved until you confirm the details.",
      action: { label: "Review", href: "/dashboard/receipts" },
    });
  }

  if (data.uncategorized && data.uncategorized > 0) {
    items.push({
      id: "uncategorized",
      severity: "LOW",
      impact: 0,
      title: `${plural(data.uncategorized, "uncategorized transaction")}`,
      description: "Categories drive budgets, habits and drains.",
      action: { label: "Categorize", href: "/dashboard/transactions" },
    });
  }

  for (const b of data.budgets ?? []) {
    if (b.percentUsed < 90) continue;
    const over = b.percentUsed >= 100;
    items.push({
      id: `budget:${b.id}`,
      severity: over ? "HIGH" : "MEDIUM",
      impact: Math.max(0, b.currentSpend - b.monthlyLimit),
      title: `${b.categoryDisplayName} budget ${over ? "exceeded" : "almost used"}`,
      description: `${Math.round(b.percentUsed)}% of ${formatRupees(b.monthlyLimit)} used this month.`,
      action: { label: "Open budget", href: "/dashboard/budgets" },
    });
  }

  for (const r of data.recurring ?? []) {
    if (r.status !== "OVERDUE" && r.status !== "MISSED") continue;
    items.push({
      id: `recurring:${r.id}`,
      severity: "MEDIUM",
      impact: r.estimatedAmount,
      title: `${r.merchant} payment ${r.status === "MISSED" ? "missed" : "overdue"}`,
      description: `Usually about ${formatRupees(r.estimatedAmount)}. Check whether it moved or was cancelled.`,
      action: { label: "Check", href: "/dashboard/subscriptions" },
    });
  }

  for (const g of data.goals ?? []) {
    if (g.status !== "ACTIVE" || (g.paceStatus !== "BEHIND" && g.paceStatus !== "OVERDUE")) continue;
    items.push({
      id: `goal:${g.id}`,
      severity: g.paceStatus === "OVERDUE" ? "MEDIUM" : "LOW",
      impact: g.remaining,
      title: `${g.name} is ${g.paceStatus === "OVERDUE" ? "past its date" : "behind pace"}`,
      description: `${formatRupees(g.remaining)} to go.`,
      action: { label: "Open goal", href: "/dashboard/goals" },
    });
  }

  return rankAttention(items);
}


export function drainKey(leak: Pick<LeakInsight, "type" | "items">): string {
  return `${leak.type}:${leak.items[0]?.merchant ?? ""}`;
}

export function visibleDrains<T extends Pick<LeakInsight, "type" | "items" | "monthlyImpact">>(
  leaks: T[],
  hidden: string[],
  limit = DRAIN_LIMIT
): T[] {
  const skip = new Set(hidden);
  return leaks
    .filter((l) => !skip.has(drainKey(l)))
    .sort((a, b) => b.monthlyImpact - a.monthlyImpact)
    .slice(0, limit);
}

export interface ChecklistItem {
  id: "data" | "categorize" | "budget" | "goal";
  label: string;
  href: string;
  done: boolean;
}

export function checklist(input: {
  transactionCount: number;
  uncategorized: number;
  budgetCount: number;
  goalCount: number;
}): ChecklistItem[] {
  const hasData = input.transactionCount > 0;
  return [
    { id: "data", label: "Add your transactions", href: "/dashboard/capture/import", done: hasData },
    { id: "categorize", label: "Review uncategorized transactions", href: "/dashboard/transactions", done: hasData && input.uncategorized === 0 },
    { id: "budget", label: "Set one budget", href: "/dashboard/budgets", done: input.budgetCount > 0 },
    { id: "goal", label: "Set one goal", href: "/dashboard/goals", done: input.goalCount > 0 },
  ];
}
