"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, ListTree, Loader2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import { EvidenceDrawer, type Evidence } from "@/components/ui/evidence-drawer";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAnnounce } from "@/components/ui/announcer";
import { toast } from "@/components/ui/toast";
import { markNewTransaction } from "@/features/transactions/new-row";
import { useConfirmReceipt } from "@/features/receipts/hooks";
import {
  RECEIPT_THRESHOLDS,
  amountChoices,
  askedSummary,
  describeDateBasis,
  initialValues,
  planReview,
  swapDayMonth,
  validateReview,
  type FieldPlan,
  type ReviewErrors,
} from "@/features/receipts/review-logic";
import type { Receipt } from "@/features/receipts/types";
import { CurrencySelect } from "@/features/transactions/currency-select";
import { currencySymbol, formatMoney, normalizeCurrency, parseAmount } from "@/features/transactions/currency";
import { CATEGORY_OPTIONS, type TransactionCategory } from "@/features/transactions/types";
import { cn } from "@/lib/utils";
import { TitleRow } from "@/components/layout/title-row";
import { ChipSettle, FillIn } from "@/features/receipts/review-motion";
import { ReceiptImagePanel } from "./receipt-image";

const CHIP_LABELS = { high: "Looks right", medium: "Check this", low: "Please check" };
const FIELD_ID = { merchant: "rv-merchant", amount: "rv-amount", date: "rv-date", currency: "rv-currency" } as const;

const localToday = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

/** What the field's chip says: a confidence level, or that a person has to fill it in. */
function FieldSignal({ plan }: { plan: FieldPlan }) {
  if (plan.mode === "ask") {
    return (
      <ChipSettle>
        <span className="text-xs font-medium text-warning">Needs your input</span>
      </ChipSettle>
    );
  }
  return (
    <ChipSettle>
      <ConfidenceChip value={plan.confidence} thresholds={RECEIPT_THRESHOLDS} labels={CHIP_LABELS} showValue />
    </ChipSettle>
  );
}

const flag = (plan: FieldPlan) =>
  plan.mode === "highlight" ? "border-caution/60 bg-caution-soft/40" : plan.mode === "ask" ? "border-warning/50" : undefined;

export function ReviewForm({ receipt }: { receipt: Receipt }) {
  const announce = useAnnounce();
  const confirm = useConfirmReceipt(receipt.id);
  const x = receipt.extraction;
  const plan = useMemo(() => planReview(x), [x]);
  const choices = useMemo(() => amountChoices(x), [x]);
  const [values, setValues] = useState(() => initialValues(receipt, plan));
  const [errors, setErrors] = useState<ReviewErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);

  const set = (patch: Partial<typeof values>) => setValues((v) => ({ ...v, ...patch }));
  const currency = normalizeCurrency(values.currency);
  const symbol = currencySymbol(currency);
  const showChoices = choices.length > 0 && (plan.amount.mode !== "plain" || choices.length > 1);
  const parsedAmount = parseAmount(values.amount);
  const swapped = plan.date.basis?.startsWith("day-month-order") ? swapDayMonth(values.date) : null;
  const dateNote = describeDateBasis(plan.date.basis);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);
    const found = validateReview(
      { merchant: values.merchant, amount: parsedAmount, date: values.date, currency },
      localToday()
    );
    setErrors(found);
    const first = (["merchant", "amount", "date", "currency"] as const).find((f) => found[f]);
    if (first) {
      document.getElementById(FIELD_ID[first])?.focus();
      announce("Some details need your attention before this can be saved.", "assertive");
      return;
    }
    try {
      const saved = await confirm.mutateAsync({
        merchant: values.merchant.trim(),
        amount: parsedAmount!,
        date: values.date,
        currency: currency!,
        category: (values.category || undefined) as TransactionCategory | undefined,
        notes: values.notes.trim() || undefined,
      });
      if (saved.transaction) markNewTransaction(saved.transaction.id);
      toast({ title: "Receipt saved", description: "It is now in your transactions.", tone: "success" });
      announce("Receipt saved as a transaction.");
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : "Could not save the transaction. Try again.";
      setServerError(msg);
      announce(msg, "assertive");
    }
  };

  const asked = (["merchant", "amount", "date", "currency"] as const).filter((f) => plan[f].mode === "ask");

  return (
    <div className="space-y-sp-giant">
      <TitleRow
        title="Review receipt"
        description="Nothing is saved until you confirm. Check the values against the image."
        actions={
          <Button variant="ghost" size="sm" asChild>
            <Link href="/dashboard/receipts">
              <ArrowLeft aria-hidden="true" />
              Back to receipt inbox
            </Link>
          </Button>
        }
      />

      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div className="lg:sticky lg:top-4">
          <ReceiptImagePanel receipt={receipt} />
        </div>

        <form onSubmit={submit} noValidate className="space-y-4">
          {asked.length > 0 && (
            <div className="rounded-md border border-caution/25 bg-caution-soft p-3 text-sm text-caution" role="note">
              {asked.length === 1 ? "One detail" : `${asked.length} details`} could not be read with enough certainty. Fill in{" "}
              {askedSummary(asked)}.
            </div>
          )}

          <Field
            label="Merchant"
            id={FIELD_ID.merchant}
            required
            error={errors.merchant}
            labelAction={<FieldSignal plan={plan.merchant} />}
            hint={x?.merchantRaw && x.merchantRaw !== values.merchant ? `Printed as "${x.merchantRaw}"` : undefined}
          >
            <FillIn index={0} pulse={plan.merchant.mode === "highlight"}>
              <Input
                value={values.merchant}
                onChange={(e) => set({ merchant: e.target.value })}
                placeholder="e.g. Walmart, Starbucks"
                maxLength={255}
                className={flag(plan.merchant)}
              />
            </FillIn>
          </Field>

          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_14rem]">
            <Field
              label="Total amount"
              id={FIELD_ID.amount}
              required
              error={errors.amount}
              labelAction={<FieldSignal plan={plan.amount} />}
            >
              <FillIn index={1} pulse={plan.amount.mode === "highlight"}>
                <div className="relative">
                  {symbol && (
                    <span aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                      {symbol}
                    </span>
                  )}
                  <Input
                    value={values.amount}
                    onChange={(e) => set({ amount: e.target.value })}
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder="0.00"
                    style={symbol ? { paddingLeft: `${1.1 + symbol.length * 0.55}rem` } : undefined}
                    className={cn("tabular-nums", flag(plan.amount))}
                  />
                </div>
              </FillIn>
            </Field>
            <Field
              label="Currency"
              id={FIELD_ID.currency}
              required
              error={errors.currency}
              labelAction={<FieldSignal plan={plan.currency} />}
            >
              <FillIn index={2} pulse={plan.currency.mode === "highlight"}>
                <CurrencySelect
                  value={values.currency}
                  onChange={(code) => set({ currency: code })}
                  detected={x?.currency}
                  className={flag(plan.currency)}
                />
              </FillIn>
            </Field>
          </div>

          {showChoices && (
            <fieldset className="-mt-1 min-w-0 space-y-1.5">
              <legend className="text-xs font-medium text-muted-foreground">
                {plan.amount.mode === "ask" ? "Totals found on the receipt. Pick one or type the amount." : "Other totals found on the receipt"}
              </legend>
              <div className="flex flex-col gap-1.5">
                {choices.map((c) => {
                  const chosen = parsedAmount != null && Math.round(parsedAmount * 100) === Math.round(c.amount * 100);
                  return (
                    <button
                      key={c.amount}
                      type="button"
                      aria-pressed={chosen}
                      onClick={() => set({ amount: String(c.amount) })}
                      className={cn(
                        "flex min-h-11 items-center justify-between gap-3 rounded-md border px-3 py-2 text-left text-sm transition-colors",
                        chosen ? "border-primary bg-band-blue" : "border-input bg-card hover:bg-band-blue"
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block font-medium tabular-nums">{formatMoney(c.amount, currency ?? x?.currency)}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {c.label ? `${c.label} · ` : ""}
                          {c.description}
                        </span>
                      </span>
                      {chosen && <CheckCircle2 className="h-4 w-4 shrink-0 text-primary" aria-label="Selected" />}
                    </button>
                  );
                })}
              </div>
            </fieldset>
          )}

          <Field
            label="Date"
            id={FIELD_ID.date}
            required
            error={errors.date}
            labelAction={<FieldSignal plan={plan.date} />}
            hint={dateNote ?? undefined}
          >
            <div className="flex flex-wrap items-center gap-2">
              <FillIn index={3} pulse={plan.date.mode === "highlight"}>
                <Input
                  type="date"
                  value={values.date}
                  max={localToday()}
                  onChange={(e) => set({ date: e.target.value })}
                  className={cn("w-auto min-w-44", flag(plan.date))}
                />
              </FillIn>
              {swapped && (
                <Button type="button" size="sm" variant="outline" onClick={() => set({ date: swapped })}>
                  Swap day and month
                </Button>
              )}
            </div>
          </Field>

          <Field label="Category" id="rv-category" hint="Leave on automatic and we will pick one; you can change it after saving.">
            <Select value={values.category || "__auto__"} onValueChange={(v) => set({ category: v === "__auto__" ? "" : v })}>
              <SelectTrigger>
                <SelectValue placeholder="Automatic" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__auto__">Automatic</SelectItem>
                {CATEGORY_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>

          <Field label="Notes (optional)" id="rv-notes">
            <Input value={values.notes} onChange={(e) => set({ notes: e.target.value })} maxLength={500} placeholder="Add a note" />
          </Field>

          <ReadingDetails receipt={receipt} currency={currency ?? x?.currency ?? null} />

          {serverError && (
            <p role="alert" className="flex items-center gap-1.5 text-sm font-medium text-warning">
              <XCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
              {serverError}
            </p>
          )}

          <div className="flex flex-wrap items-center gap-3 pt-1">
            <Button type="submit" disabled={confirm.isPending}>
              {confirm.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <CheckCircle2 aria-hidden="true" />}
              {confirm.isPending ? "Saving" : "Confirm and save"}
            </Button>
            <Button type="button" variant="outline" asChild>
              <Link href="/dashboard/capture/manual">Enter manually instead</Link>
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

/** Line items, taxes and how the receipt was read, in the evidence drawer. */
function ReadingDetails({ receipt, currency }: { receipt: Receipt; currency: string | null }) {
  const x = receipt.extraction;
  if (!x) return null;
  const money = (n: number | null | undefined) => (n == null ? "" : formatMoney(n, currency));
  const items = (x.lineItems ?? []).filter((i) => i.itemName);
  const inputs: Evidence["inputs"] = [
    ...items.map((i) => ({
      label: i.itemQuantity ? `${i.itemName} × ${i.itemQuantity}` : i.itemName,
      value: money(i.itemPrice),
    })),
    ...(x.subtotal != null ? [{ label: "Subtotal", value: money(x.subtotal) }] : []),
    ...(x.taxes ?? []).map((t) => ({ label: t.label ?? "Tax", value: money(t.amount) })),
    ...(x.discounts ?? []).map((d) => ({ label: d.label ?? "Discount", value: money(d.amount) })),
    ...(x.tip != null ? [{ label: "Tip", value: money(x.tip) }] : []),
    ...(x.roundOff != null ? [{ label: "Round off", value: money(x.roundOff) }] : []),
  ];
  const attempts = x.engineAttempts ?? [];
  const caveats = [
    ...(x.itemsReconciled === false ? ["The line items do not add up to the total, so check the amount."] : []),
    ...(attempts.some((a) => /error|fail|timeout/i.test(a)) ? ["A first attempt at reading failed, so a fallback reader was used."] : []),
  ];
  const caption = [items.length ? `${items.length} item${items.length === 1 ? "" : "s"}` : null, (x.taxes ?? []).length ? "taxes" : null]
    .filter(Boolean)
    .join(" and ");

  return (
    <EvidenceDrawer
      title="How this receipt was read"
      value={x.amount != null ? money(x.amount) : undefined}
      evidence={{
        method: (
          <>
            <p>The text on the photo was read automatically and checked for a printed total, line items and tax.</p>
            {attempts.length > 0 && <p className="mt-2 text-caption">Readers tried: {attempts.join(", ")}</p>}
          </>
        ),
        inputs,
        confidence: x.confidence,
        confidenceThresholds: RECEIPT_THRESHOLDS,
        source: "receipt",
        caveats,
      }}
      trigger={
        <Button type="button" variant="ghost" size="sm" className="-ml-2">
          <ListTree aria-hidden="true" />
          Line items and taxes{caption ? ` (${caption})` : ""}
        </Button>
      }
    />
  );
}
