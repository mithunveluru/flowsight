// Pure decisions behind the receipt review screen and inbox. Type-only imports keep
// this file loadable by the node test runner.
import type {
  OcrExtractionResult,
  Receipt,
  ReceiptAmountOption,
  ReceiptDecision,
  ReceiptField,
} from "./types";

export type FieldMode = "plain" | "highlight" | "ask";

export interface FieldPlan {
  mode: FieldMode;
  confidence: number | null;
  basis: string | null;
}

export type ReviewPlan = Record<ReceiptField, FieldPlan>;

const MODE: Record<ReceiptDecision, FieldMode> = {
  ACCEPT: "plain",
  ACCEPT_WITH_HIGHLIGHT: "highlight",
  ASK_USER: "ask",
};

// Same bands as the backend's ReceiptDecision (0.90 / 0.60), for the confidence chip.
export const RECEIPT_THRESHOLDS = { high: 0.9, medium: 0.6 };

function fieldValue(x: OcrExtractionResult, field: ReceiptField): unknown {
  return field === "merchant" ? x.merchant : field === "amount" ? x.amount : field === "date" ? x.date : x.currency;
}

/**
 * Maps the backend's per-field decision to how the field is shown. A missing value is
 * always asked for. Receipts read before per-field decisions existed fall back to the
 * old receipt-level flag, which only ever concerned the amount.
 */
export function planReview(x: OcrExtractionResult | null | undefined): ReviewPlan {
  const plan = {} as ReviewPlan;
  for (const field of ["merchant", "amount", "date", "currency"] as const) {
    const f = x?.fields?.[field];
    const value = x ? fieldValue(x, field) : null;
    let mode: FieldMode;
    if (value == null || value === "") mode = "ask";
    else if (f?.decision) mode = MODE[f.decision] ?? "highlight";
    else if (field === "amount" && x?.requiresConfirmation) mode = "highlight";
    else mode = "plain";
    plan[field] = { mode, confidence: f?.confidence ?? null, basis: f?.basis ?? null };
  }
  return plan;
}

/** The fields that need a person before Confirm, in form order. */
export function fieldsToAsk(plan: ReviewPlan): ReceiptField[] {
  return (["merchant", "amount", "date", "currency"] as const).filter((f) => plan[f].mode === "ask");
}

/** "the merchant and the date", for the note above the form; "" when nothing is asked. */
export function askedSummary(fields: readonly ReceiptField[]): string {
  const names = fields.map((f) => (f === "merchant" ? "the merchant" : `the ${f}`));
  return names.length < 2 ? names.join("") : `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

export interface AmountChoice {
  amount: number;
  label: string | null;
  basis: string | null;
  description: string;
}

const roundCents = (n: number) => Math.round(n * 100);

/**
 * The totals to offer when the amount is asked for: the backend's candidates, best
 * first, plus its own pick when that is not among them (the Java path can return cash
 * minus change as the amount). Duplicates by cent value are dropped.
 */
export function amountChoices(x: OcrExtractionResult | null | undefined): AmountChoice[] {
  const raw: ReceiptAmountOption[] = [...(x?.amountCandidates ?? [])];
  if (x?.amount != null && !raw.some((o) => o.amount != null && roundCents(o.amount) === roundCents(x.amount!))) {
    raw.unshift({ amount: x.amount, label: null, basis: x.fields?.amount?.basis ?? null });
  }
  const seen = new Set<number>();
  const out: AmountChoice[] = [];
  for (const o of raw) {
    if (o.amount == null || !Number.isFinite(o.amount) || o.amount <= 0) continue;
    const key = roundCents(o.amount);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ amount: o.amount, label: o.label, basis: o.basis, description: describeAmountBasis(o.basis) });
  }
  return out;
}

/** Plain-language reading of an amount basis such as "conflict:arithmetic(items+tax)+total-label+2-passes". */
export function describeAmountBasis(basis: string | null | undefined): string {
  if (!basis) return "Read from the receipt";
  const b = basis.toLowerCase();
  const parts: string[] = [];
  if (b.startsWith("conflict")) parts.push("Two readings of the total disagree");
  if (b.includes("derived")) parts.push("Cash paid minus change");
  else if (b.includes("payment")) parts.push("Amount paid");
  if (b.includes("arithmetic")) parts.push("Adds up with the items and tax");
  if (b.includes("grand-total-label")) parts.push("Printed as the grand total");
  else if (b.includes("total-label")) parts.push("Printed next to a total");
  if (/\bllm\b/.test(b.replace(/[+:-]/g, " "))) parts.push("Read by the vision model");
  const passes = /(\d+)-passes/.exec(b);
  if (passes) parts.push(`Same on ${passes[1]} reads`);
  return parts.length ? parts.join(" · ") : "Read from the receipt";
}

export type AmountSelection = { kind: "candidate"; index: number } | { kind: "other"; text: string } | null;

/** The amount a selection stands for, or null when nothing valid is chosen yet. */
export function selectedAmount(
  selection: AmountSelection,
  choices: AmountChoice[],
  parse: (text: string) => number | null
): number | null {
  if (!selection) return null;
  if (selection.kind === "candidate") return choices[selection.index]?.amount ?? null;
  return parse(selection.text);
}

/** Note for a date the backend flagged, saying which order was assumed; null when there is nothing to say. */
export function describeDateBasis(basis: string | null | undefined): string | null {
  if (!basis) return null;
  if (basis === "day-month-order-unknown") return "The day and month could be either way round.";
  const m = /^day-month-order-from-([A-Z]{3})$/.exec(basis);
  if (m) {
    return m[1] === "USD"
      ? "Read as month/day because the receipt is in USD."
      : `Read as day/month because the receipt is in ${m[1]}.`;
  }
  if (basis === "passes-disagree") return "Two readings of the date disagree.";
  return null;
}

/** "2026-03-04" -> "2026-04-03"; null when swapping gives the same or an impossible date. */
export function swapDayMonth(iso: string | null | undefined): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? "");
  if (!m) return null;
  const [, y, mo, d] = m;
  if (mo === d || Number(d) > 12) return null;
  const swapped = `${y}-${d}-${mo}`;
  const check = new Date(`${swapped}T00:00:00Z`);
  return check.getUTCMonth() + 1 === Number(d) && check.getUTCDate() === Number(mo) ? swapped : null;
}

/** First ten characters of an ISO date or date-time, else "". */
export function isoDay(value: string | null | undefined): string {
  return value && /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(0, 10) : "";
}

export interface ReviewValues {
  merchant: string;
  // the amount as typed; parsed on submit
  amount: string;
  date: string;
  currency: string;
  category: string;
  notes: string;
}

/** Initial form values: asked fields start empty so nothing unknown looks known. */
export function initialValues(receipt: Pick<Receipt, "extraction" | "currency">, plan: ReviewPlan): ReviewValues {
  const x = receipt.extraction;
  const keep = (f: ReceiptField) => plan[f].mode !== "ask";
  return {
    merchant: keep("merchant") ? x?.merchant ?? "" : "",
    amount: keep("amount") && x?.amount != null ? String(x.amount) : "",
    date: keep("date") ? isoDay(x?.date) : "",
    currency: keep("currency") ? (x?.currency ?? receipt.currency ?? "").toUpperCase() : "",
    category: "",
    notes: "",
  };
}

export type ReviewErrors = Partial<Record<"merchant" | "amount" | "date" | "currency", string>>;

export function validateReview(
  v: { merchant: string; amount: number | null; date: string; currency: string | null },
  today: string
): ReviewErrors {
  const e: ReviewErrors = {};
  if (!v.merchant.trim()) e.merchant = "Enter the merchant name.";
  else if (v.merchant.trim().length > 255) e.merchant = "Keep the merchant name under 255 characters.";
  if (v.amount == null) e.amount = "Choose a total or type the amount.";
  if (!v.date) e.date = "Enter the date on the receipt.";
  else if (v.date > today) e.date = "The date cannot be in the future.";
  if (!v.currency) e.currency = "Choose the currency.";
  return e;
}

export type ReceiptStage = "processing" | "draft" | "failed" | "confirmed";

export function receiptStage(r: Pick<Receipt, "status" | "transaction">): ReceiptStage {
  if (r.transaction) return "confirmed";
  if (r.status === "FAILED") return "failed";
  if (r.status === "COMPLETED") return "draft";
  return "processing";
}

const STAGE_ORDER: Record<ReceiptStage, number> = { draft: 0, failed: 1, processing: 2, confirmed: 3 };

/** Inbox order: drafts awaiting review, then unreadable ones, then in-flight, then done; newest first within each. */
export function sortInbox<T extends Pick<Receipt, "status" | "transaction" | "createdAt">>(receipts: readonly T[]): T[] {
  return [...receipts].sort(
    (a, b) => STAGE_ORDER[receiptStage(a)] - STAGE_ORDER[receiptStage(b)] || b.createdAt.localeCompare(a.createdAt)
  );
}

export type ImageRetention = { state: "kept"; until: Date | null } | { state: "expired" } | { state: "none" };

/** Whether the review copy should still exist. The endpoint can 404 anyway (e.g. after a redeploy). */
export function imageRetention(r: Pick<Receipt, "imageAvailable" | "imageExpiresAt">): ImageRetention {
  const until = r.imageExpiresAt ? new Date(r.imageExpiresAt) : null;
  if (r.imageAvailable) return { state: "kept", until };
  return until ? { state: "expired" } : { state: "none" };
}
