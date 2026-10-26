// Pure decisions behind the transactions table and the review queue. Type-only
// imports keep this file loadable by the node test runner.
import type {
  MerchantRuleSuggestion,
  Transaction,
  TransactionCategory,
  TransactionSource,
} from "./types";

// Below 0.6 a model suggestion looks tentative (ml-categorization handoff).
export const CATEGORY_THRESHOLDS = { high: 0.8, medium: 0.6 };

export type CategorySignal =
  | { kind: "user" }
  | { kind: "none" }
  | { kind: "rules" }
  | { kind: "model" | "personal"; confidence: number | null };

/**
 * What to show next to a category: the user's own choice carries no chip, keyword
 * rules say "Auto" without a percentage (not calibrated), and model or personal
 * suggestions show their calibrated probability.
 */
export function categorySignal(
  tx: Pick<Transaction, "category" | "categorySource" | "confidenceScore" | "reviewed">
): CategorySignal {
  if (!tx.category || tx.category === "UNCATEGORIZED") return { kind: "none" };
  switch (tx.categorySource) {
    case "USER":
      return { kind: "user" };
    case "MODEL":
      return { kind: "model", confidence: tx.confidenceScore ?? null };
    case "PERSONAL":
      return { kind: "personal", confidence: tx.confidenceScore ?? null };
    case "RULES":
      return { kind: "rules" };
    default:
      // rows from before category sources were recorded
      return tx.reviewed ? { kind: "user" } : { kind: "rules" };
  }
}

/** The model's other picks for this row, best first, without the current category. */
export function otherAlternatives(tx: Pick<Transaction, "category" | "categoryAlternatives">) {
  return (tx.categoryAlternatives ?? []).filter((a) => a.category !== tx.category && a.category !== "UNCATEGORIZED");
}

/** Up to three one-tap answers for the review queue: the model's top three, else the current guess. */
export function reviewChoices(
  tx: Pick<Transaction, "category" | "categoryAlternatives">,
  limit = 3
): { category: TransactionCategory; probability: number | null }[] {
  const out: { category: TransactionCategory; probability: number | null }[] = [];
  const add = (category: TransactionCategory | null | undefined, probability: number | null) => {
    if (!category || category === "UNCATEGORIZED" || out.some((c) => c.category === category)) return;
    out.push({ category, probability });
  };
  for (const a of tx.categoryAlternatives ?? []) add(a.category, a.probability);
  add(tx.category, null);
  return out.slice(0, limit);
}

export const SOURCE_LABEL: Record<TransactionSource, string> = {
  MANUAL: "Manual",
  CSV: "CSV import",
  OCR: "Receipt",
  SAMPLE: "Sample",
  SMS: "SMS",
};

export interface ClientFilters {
  query: string;
  source: TransactionSource | null;
  needsReview: boolean;
}

export const NO_CLIENT_FILTERS: ClientFilters = { query: "", source: null, needsReview: false };

export function hasClientFilters(f: ClientFilters): boolean {
  return f.query.trim() !== "" || f.source != null || f.needsReview;
}

/** Filters applied to the rows already loaded (the API filters only by category and date). */
export function filterRows<T extends Pick<Transaction, "merchant" | "description" | "notes" | "source" | "reviewed">>(
  rows: readonly T[],
  f: ClientFilters
): T[] {
  const q = f.query.trim().toLowerCase();
  return rows.filter(
    (r) =>
      (!q || [r.merchant, r.description, r.notes].some((s) => s?.toLowerCase().includes(q))) &&
      (!f.source || r.source === f.source) &&
      (!f.needsReview || !r.reviewed)
  );
}

export type BulkAction =
  | { type: "recategorize"; category: TransactionCategory }
  | { type: "review" }
  | { type: "delete" };

/**
 * Which selected rows an action really changes. Re-sending a category the user already
 * chose, or marking a reviewed row reviewed, would only add noise to the feedback log.
 */
export function planBulk<T extends Pick<Transaction, "id" | "category" | "categorySource" | "reviewed">>(
  action: BulkAction,
  rows: readonly T[],
  selected: ReadonlySet<string>
): { targets: string[]; unchanged: number } {
  const chosen = rows.filter((r) => selected.has(r.id));
  const changes = (r: T) =>
    action.type === "recategorize"
      ? !(r.category === action.category && r.categorySource === "USER")
      : action.type === "review"
        ? !r.reviewed
        : true;
  const targets = chosen.filter(changes).map((r) => r.id);
  return { targets, unchanged: chosen.length - targets.length };
}

export interface BulkOutcome<R> {
  succeeded: { id: string; result: R }[];
  failed: { id: string; error: unknown }[];
}

/** Runs one request per id with bounded concurrency; failures are collected, not thrown. */
export async function runBulk<R>(
  ids: readonly string[],
  task: (id: string) => Promise<R>,
  concurrency = 4
): Promise<BulkOutcome<R>> {
  const out: BulkOutcome<R> = { succeeded: [], failed: [] };
  let next = 0;
  const worker = async () => {
    while (next < ids.length) {
      const id = ids[next++];
      try {
        out.succeeded.push({ id, result: await task(id) });
      } catch (error) {
        out.failed.push({ id, error });
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, ids.length) }, worker));
  return out;
}

/** One suggestion per rule; a later edit of the same rule replaces the earlier one. */
export function collectSuggestions(
  current: readonly MerchantRuleSuggestion[],
  incoming: readonly (MerchantRuleSuggestion | null | undefined)[]
): MerchantRuleSuggestion[] {
  const byRule = new Map(current.map((s) => [s.ruleId, s]));
  for (const s of incoming) if (s) byRule.set(s.ruleId, s);
  return [...byRule.values()];
}

export interface WindowRange {
  start: number;
  end: number; // exclusive
  padTop: number;
  padBottom: number;
}

/** Rows to render for a fixed row height, with `overscan` extra rows on each side. */
export function windowRange(opts: {
  scrollTop: number;
  viewportHeight: number;
  rowHeight: number;
  count: number;
  overscan?: number;
}): WindowRange {
  const { scrollTop, viewportHeight, rowHeight, count, overscan = 8 } = opts;
  if (count <= 0 || rowHeight <= 0) return { start: 0, end: 0, padTop: 0, padBottom: 0 };
  // a stale scrollTop (the list just got shorter) is clamped so the window is never empty
  const maxScroll = Math.max(0, count * rowHeight - viewportHeight);
  const first = Math.floor(Math.min(Math.max(0, scrollTop), maxScroll) / rowHeight);
  const visible = Math.ceil(Math.max(0, viewportHeight) / rowHeight) + 1;
  const start = Math.min(count, Math.max(0, first - overscan));
  const end = Math.min(count, first + visible + overscan);
  return { start, end, padTop: start * rowHeight, padBottom: (count - end) * rowHeight };
}

/** Fetch the next page once the rendered window comes within `threshold` rows of the loaded end. */
export function shouldLoadMore(windowEnd: number, loaded: number, hasMore: boolean, threshold = 20): boolean {
  return hasMore && loaded - windowEnd <= threshold;
}
